import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PreparedStatement Variante (PostgreSQL):
 * Pro Datensatz wird ein PreparedStatement mit Platzhaltern (?,?,...)
 * ausgeführt.
 *
 * Vorteil gegenüber StraightForward: SQL wird vorbereitet, Parameter werden
 * gebunden
 * (in der Praxis sicherer und oft schneller als String-Konkatenation, aber hier
 * ohne Batch).
 */
public class PreparedStmPg {
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
     * Erstellt die Zieltabelle neu und importiert Daten aus TSV.
     * Diese Variante nutzt PreparedStatement, aber ohne Batch-Verarbeitung.
     */
    public void insertData(String source) throws SQLException {
        try {
            Statement st = con.createStatement();
            String sql = "DROP Table if exists authps;";
            st.execute(sql);

            Statement cstm = con.createStatement();
            String create = "CREATE TABLE authps(name VARCHAR(49), pubID VARCHAR(129));";
            cstm.execute(create);

            // INSERT mit Platzhaltern; Werte werden über setString() gebunden
            String sqlStm = "INSERT INTO authps(name, pubID) VALUES (?,?)";
            PreparedStatement stm = con.prepareStatement(sqlStm);

            BufferedReader reader;
            reader = new BufferedReader(new FileReader(source));
            String line;

            int counter = 0; // for runtime test: Begrenzung der Datensätze

            while ((line = reader.readLine()) != null && counter < 10000) {
                counter++;
                String[] columns = line.split("\t", 0);

                // Erwartet: 2 Spalten (name, pubID)
                if (columns.length <= 2) {
                    // Escaping ist hier eigentlich nicht zwingend nötig (Parameterbindung),
                    // wird aber im Assignment konsistent zu den anderen Varianten gemacht.
                    String name = columns[0].replace("'", "''");
                    String pubID = columns[1].replace("'", "''");

                    stm.setString(1, name);
                    stm.setString(2, pubID);

                    // Pro Zeile ein Execute (ohne addBatch/executeBatch)
                    stm.execute();
                }
            }

            System.out.println("Data successfully transmitted.");
        } catch (IOException | SQLException e) {
            System.err.println(e.getMessage());
        }
        con.close();
    }

    public static void main(String[] args) throws SQLException {
        PreparedStmPg ps = new PreparedStmPg();
        ps.connect();
        ps.insertData(
                "F:\\uni\\tuning\\Project1\\code\\dblp\\auth.tsv");
    }
}
