import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class StraightForwardMdb {
    private Connection con;

    public void connect(){
        String url = "jdbc:mariadb://localhost:3306/tuning";
        String user = "root";
        String pw = "root";

        try {
            con = DriverManager.getConnection(url, user, pw);
            System.out.println("Successfully Connected.");
        } catch (SQLException e) {
            System.out.println("Failed to connect: " + e.getMessage());
        }
    }



    public void insertData(String source) throws SQLException {
        try{
            Statement st = con.createStatement();
            String sql = "DROP Table if exists authsf;";
            st.execute(sql);

            Statement cstm = con.createStatement();
            String create = "CREATE TABLE authsf(name VARCHAR(49), pubID VARCHAR(129));";
            cstm.execute(create);

            Statement stm = con.createStatement();

            BufferedReader reader;
            reader = new BufferedReader(new FileReader(source));
            String line;

            int counter = 0;  //for runtime test

            while ((line = reader.readLine()) != null && counter < 20000) {
                counter++;
                String[] columns = line.split("\t", 0);
                if (columns.length <= 2) {
                    String name = columns[0].replace("'", "''");
                    String pubID = columns[1].replace("'", "''");

                    String sqlStm = "INSERT INTO authsf(name, pubID) VALUES ('" + name + "','" + pubID + "')";
                    stm.execute(sqlStm);
                }
            }

            System.out.println("Data successfully transmitted.");
        }
        catch (IOException | SQLException e){
            System.err.println(e.getMessage());
        }
        con.close();
    }

    public static void main(String[] args) throws SQLException {
        StraightForwardMdb sf = new StraightForwardMdb();
        sf.connect();
        sf.insertData("F:\\uni\\tuning\\Project1\\code\\dblp\\auth.tsv");
    }
}