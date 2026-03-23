import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Batch-Variante (PostgreSQL):
 * Nutzt ein PreparedStatement und sammelt viele Inserts mit addBatch(),
 * um sie in Blöcken mit executeBatch() an die DB zu schicken.
 *
 * Ziel: Overhead pro INSERT reduzieren (weniger Roundtrips), oft deutlich
 * schneller.
 */
public class BatchesPg {
    /** JDBC-Verbindung zur Datenbank; wird in connect() initialisiert. */
    private Connection con;

    public void connect() {
        String url = "jdbc:postgresql://localhost:5432/tuning";
        String user = "postgres";
        String pw = "root";

        try {
            con = DriverManager.getConnection(url, user, pw);
            System.out.println("Successfully Connected.");
        } catch (SQLException e) {
            System.out.println("Failed to Connect: " + e.getMessage());
        }
    }

    /**
     * Erstellt die Zieltabelle neu und importiert TSV-Daten per Batch.
     * 
     * @param source Pfad zur TSV-Datei
     */
    public void insertData(String source) throws SQLException {
        try {
            // Tabelle neu erstellen, damit der Testlauf reproduzierbar ist
            Statement st = con.createStatement();
            String sql = "DROP Table if exists authb;";
            st.execute(sql);

            Statement cstm = con.createStatement();
            String create = "CREATE TABLE authb(name VARCHAR(49), pubID VARCHAR(129));";
            cstm.execute(create);

            // Prepared INSERT mit Parametern
            String sqlStm = "INSERT INTO authb(name, pubID) VALUES (?,?)";
            PreparedStatement stm = con.prepareStatement(sqlStm);

            // Batch-Größe: wie viele Inserts gesammelt werden, bevor executeBatch()
            // ausgeführt wird
            int batchSize = 100000;
            int batchCounter = 0;

            int maxRows = 100000;
            int counter = 0;

            BufferedReader reader;
            reader = new BufferedReader(new FileReader(source));
            String line;

            while ((line = reader.readLine()) != null && counter < maxRows) {
                String[] columns = line.split("\t", 0);

                // Erwartet: name + pubID; für das Experiment wird von korrekten Zeilen
                // ausgegangen
                if (columns.length <= 2) {
                    // (Optional) Escaping; bei Parameterbindung nicht zwingend erforderlich
                    String name = columns[0].replace("'", "''");
                    String pubID = columns[1].replace("'", "''");

                    stm.setString(1, name);
                    stm.setString(2, pubID);

                    // Statement in Batch aufnehmen statt sofort ausführen
                    stm.addBatch();
                    batchCounter++;
                }

                // Sobald batchSize erreicht ist: Blockweise zur DB schicken
                if (batchCounter % batchSize == 0) {
                    stm.executeBatch();
                }
                counter++;
            }

            // Restliche Einträge (falls letzte Batch nicht exakt batchSize erreicht)
            stm.executeBatch();
            System.out.println("Data successfully transmitted.");
        } catch (IOException | SQLException e) {
            System.err.println("Failed to transmit data: " + e.getMessage());
        }
        con.close();
    }

    public static void main(String[] args) throws SQLException {
        BatchesPg b = new BatchesPg();
        b.connect();
        b.insertData(
                "F:\\uni\\tuning\\Project1\\code\\dblp\\auth.tsv");
    }
}
