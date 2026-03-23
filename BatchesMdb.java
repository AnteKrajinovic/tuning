import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Batch-Variante (MariaDB):
 * PreparedStatement + addBatch/executeBatch, um Inserts in großen Blöcken
 * auszuführen.
 */
public class BatchesMdb {
    /** JDBC-Verbindung zur Datenbank; wird in connect() initialisiert. */
    private Connection con;

    public void connect() {
        String url = "jdbc:mariadb://localhost:3306/tuning";
        String user = "root";
        String pw = "root";

        try {
            con = DriverManager.getConnection(url, user, pw);
            System.out.println("Successfully Connected.");
        } catch (SQLException e) {
            System.out.println("Failed to Connect: " + e.getMessage());
        }
    }

    /**
     * Importiert TSV-Daten in eine neu erstellte Tabelle und nutzt
     * Batch-Verarbeitung.
     */
    public void insertData(String source) throws SQLException {
        try {
            Statement st = con.createStatement();
            String sql = "DROP Table if exists authb;";
            st.execute(sql);

            Statement cstm = con.createStatement();
            String create = "CREATE TABLE authb(name VARCHAR(49), pubID VARCHAR(129));";
            cstm.execute(create);

            String sqlStm = "INSERT INTO authb(name, pubID) VALUES (?,?)";
            PreparedStatement stm = con.prepareStatement(sqlStm);

            int batchSize = 150000; // Anzahl INSERTs pro Batch
            int batchCounter = 0; // zählt, wie viele Datensätze zur aktuellen Batch hinzugefügt wurden

            BufferedReader reader;
            reader = new BufferedReader(new FileReader(source));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", 0);
                if (columns.length <= 2) {
                    String name = columns[0].replace("'", "''");
                    String pubID = columns[1].replace("'", "''");

                    stm.setString(1, name);
                    stm.setString(2, pubID);

                    stm.addBatch();
                    batchCounter++;
                }

                // Blockweise ausführen, um Netzwerk/DB-Overhead zu reduzieren
                if (batchCounter % batchSize == 0) {
                    stm.executeBatch();
                }
            }

            // Abschluss: verbleibende Datensätze ausführen
            stm.executeBatch();
            System.out.println("Data successfully transmitted.");
        } catch (IOException | SQLException e) {
            System.err.println(e.getMessage());
        }
        con.close();
    }

    public static void main(String[] args) throws SQLException {
        BatchesMdb b = new BatchesMdb();
        b.connect();
        b.insertData(
                "F:\\uni\\tuning\\Project1\\code\\dblp\\auth.tsv");
    }
}
