import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PreparedStatement Variante (MariaDB):
 * Pro TSV-Zeile wird ein PreparedStatement ausgeführt (ohne Batch).
 */
public class PreparedStmMdb {
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

    public void insertData(String source) throws SQLException {
        try {
            // Tabelle neu erstellen (für reproduzierbare Messungen)
            Statement st = con.createStatement();
            String sql = "DROP Table if exists authps;";
            st.execute(sql);

            Statement cstm = con.createStatement();
            String create = "CREATE TABLE authps(name VARCHAR(49), pubID VARCHAR(129));";
            cstm.execute(create);

            // Prepared INSERT: Parameter statt String-Konkatenation
            String sqlStm = "INSERT INTO authps(name, pubID) VALUES (?,?)";
            PreparedStatement stm = con.prepareStatement(sqlStm);

            BufferedReader reader;
            reader = new BufferedReader(new FileReader(source));
            String line;

            int counter = 0; // for runtime test

            while ((line = reader.readLine()) != null && counter < 10000) {
                counter++;
                String[] columns = line.split("\t", 0);
                if (columns.length <= 2) {
                    String name = columns[0].replace("'", "''");
                    String pubID = columns[1].replace("'", "''");

                    stm.setString(1, name);
                    stm.setString(2, pubID);

                    // Pro Datensatz ein Execute (kein Batch)
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
        PreparedStmMdb ps = new PreparedStmMdb();
        ps.connect();
        ps.insertData(
                "F:\\uni\\tuning\\Project1\\code\\dblp\\auth.tsv");
    }
}
