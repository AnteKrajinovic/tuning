import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

public class CopyPg {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:postgresql://localhost:5432/tuning"; 
    String user = "postgres";
    String pw = "root";
    Path source = Path.of("F:\\uni\\tuning\\Project1\\code\\dblp\\auth.tsv");

    try (Connection con = DriverManager.getConnection(url, user, pw)) {
      con.setAutoCommit(false);

      try (Statement st = con.createStatement()) {
        st.execute("DROP TABLE IF EXISTS authcp");
        st.execute("CREATE TABLE authcp(name VARCHAR(49), pubID VARCHAR(129))");
      }

      PGConnection pgCon = con.unwrap(PGConnection.class);  
      CopyManager cm = pgCon.getCopyAPI();

      String copySql =
          "COPY authcp(name, pubID) FROM STDIN WITH (" +
          "FORMAT csv, DELIMITER E'\\t', NULL '', QUOTE E'\\x01'" +
          ")";

      long rows;
      try (BufferedReader br = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
        rows = cm.copyIn(copySql, br);
      }

      con.commit();
      System.out.println("COPY done. Rows: " + rows);
    }
  }
}