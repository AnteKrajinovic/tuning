import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Ass2Mdb_employee {
    // Statische Variablen für die Datenbankverbindung und den Zufallsgenerator (Seed 42 für wiederholbare Ergebnisse)
    private static Connection con;
    private static Random random = new Random(42);
    
    // Vordefinierte Listen mit Testdaten für Manager, Kurse und Standorte
    static List<String> managers = Arrays.asList("Manager_1", "Manager_2", "Manager_3", "Manager_4", "Manager_5", "Manager_6", "Manager_7", "Manager_8", "Manger_9", "Manger_10");
    static List<String> courses = Arrays.asList("Course_1", "Course_2", "Course_3", "Course_4", "Course_5", "Course_6", "Course_7", "Course_8", "Course_9", "Course_10");
    static List<String> locations = Arrays.asList("Location_1", "Location_2", "Location_3", "Location_4", "Location_5", "Location_6", "Location_7", "Location_8", "Location_9", "Location_10");
    
    // Zwischenspeicher für reguläre Abteilungen, technische Abteilungen und deren Manager-Zuordnung
    static List<String> depts = new ArrayList<>();
    static List<String> techdepts = new ArrayList<>();
    static Map<String, String> mapDeptToManager = new HashMap<>();

    // Verbindung zur Datenbank herstellen
    public static void connect() {
        // Verbindungsdaten für die MariaDB-Datenbank
        String url = "jdbc:mariadb://localhost:3306/tuning";
        String user = "root";
        String pw = "root";


        try {
            // Treiber laden und Verbindung aufbauen
            con = DriverManager.getConnection(url, user, pw);
            System.out.println("Successfully Connected.");
        } catch (SQLException e) {
            System.out.println("Failed to connect: " + e.getMessage());
        }
    }

    // Datenbankschema erstellen (Tabellen und Indizes)
    public static void createSchema() {
        try {
            // --- TABELLE techdept ---
            // Alte Tabelle löschen, falls sie existiert
            Statement deleteTdStm = con.createStatement();
            String deleteTd = "DROP Table if exists techdept";
            deleteTdStm.execute(deleteTd);

            // Neue Tabelle 'techdept' erstellen
            Statement createTdStm = con.createStatement();
            String createTd = "CREATE TABLE techdept(dept VARCHAR(30), manager VARCHAR(30), location VARCHAR(30), PRIMARY KEY(dept));";
            createTdStm.execute(createTd);

            // Eindeutigen B-Tree-Index auf die Spalte 'dept' setzen, um Duplikate zu verhindern
            Statement indexTdStm = con.createStatement();
            String indexTd = "CREATE UNIQUE INDEX index_dept_techdept on techdept(dept);";      
            indexTdStm.execute(indexTd);

            // Tabelle mit generierten Daten befüllen
            fillTechdept();

            // --- TABELLE employee ---
            // Alte Tabelle löschen
            Statement deleteEmStm = con.createStatement();
            String deleteEm = "DROP Table if exists employee";
            deleteEmStm.execute(deleteEm);

            // Neue Tabelle 'employee' erstellen
            Statement createEmStm = con.createStatement();
            String createEm = "CREATE TABLE employee(ssnum int, name VARCHAR(30), manager VARCHAR(30), dept VARCHAR(30), salary int, numfriends int, PRIMARY KEY(ssnum, name));";
            createEmStm.execute(createEm);

            // Indizes erstellen: ssnum und name müssen eindeutig sein, dept bekommt einen normalen Such-Index
            Statement indexEmStm = con.createStatement();
            String indexEm = "CREATE UNIQUE INDEX index_ssnum_employee on employee(ssnum);";      
            indexEmStm.execute(indexEm);

            Statement indexEm2Stm = con.createStatement();
            String indexEm2 = "CREATE UNIQUE INDEX index_name_employee on employee(name);";       
            indexEm2Stm.execute(indexEm2);

            Statement indexEm3Stm = con.createStatement();
            String indexEm3 = "CREATE INDEX index_dept_employee on employee(dept);";       
            indexEm3Stm.execute(indexEm3);

            // Tabelle mit Mitarbeiter-Daten füllen
            fillEmployee();

            // --- TABELLE student ---
            // Alte Tabelle löschen
            Statement deleteStStm = con.createStatement();
            String deleteSt = "DROP Table if exists student";
            deleteStStm.execute(deleteSt);

            // Neue Tabelle 'student' erstellen
            Statement createStStm = con.createStatement();
            String createSt = "CREATE TABLE student(ssnum int, name VARCHAR(30), course VARCHAR(30), grade int, PRIMARY KEY(ssnum, name));";
            createStStm.execute(createSt);

            // Eindeutige Indizes auf ssnum und name setzen
            Statement indexStStm = con.createStatement();
            String indexSt = "CREATE UNIQUE INDEX index_ssnum_student on student(ssnum);";      
            indexStStm.execute(indexSt);

            Statement indexSt2Stm = con.createStatement();
            String indexSt2 = "CREATE UNIQUE INDEX index_name_student on student(name);";       
            indexSt2Stm.execute(indexSt2);

            // Tabelle mit Studenten-Daten füllen
            fillStudent();

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }

        System.out.println("Schema successfully created.");
    }

    // Hilfsmethode: Generiert Standard-Abteilungen und weist ihnen per Zufall einen Manager zu
    public static void mapDepts() {
        char c = 'A';
        int deptCounter = 0;

        // Erstellt 21 reguläre Abteilungen (Dept_A bis Dept_U)
        while (deptCounter <= 20){
            String dept = "Dept_" + c;
            // Einen zufälligen Manager aus der Liste auswählen
            String manager = managers.get(random.nextInt(managers.size()));

            mapDeptToManager.put(dept, manager);
            depts.add(dept);
            deptCounter++;
            c++;
        }
    }

    // Füllt die Tabelle 'techdept' mit 10 zufälligen Einträgen
    public static void fillTechdept() throws SQLException {
        mapDepts(); // Zuerst Basis-Abteilungen initialisieren

        String sqlStm = "INSERT INTO techdept(dept, manager, location) VALUES (?,?,?)";
        PreparedStatement stm = con.prepareStatement(sqlStm);

        int batchSize = 10;
        int batchCounter = 0;
        int insertCounter = 1;
        char c = 'A';

        // 10 spezielle Tech-Abteilungen generieren (Techdept_A bis Techdept_J)
        while (insertCounter <= 10) {
            insertCounter++;

            String dept = "Techdept_" + c;
            String manager = managers.get(random.nextInt(managers.size()));
            String location = locations.get(random.nextInt(locations.size()));

            // In die Map und Liste zur späteren Verwendung aufnehmen
            mapDeptToManager.put(dept, manager);
            techdepts.add(dept);

            // SQL-Parameter setzen
            stm.setString(1, dept);
            stm.setString(2, manager);
            stm.setString(3, location);

            // Statement einem Batch hinzufügen, um Datenbankabfragen effizienter zu bündeln
            stm.addBatch();
            batchCounter++;
            c++;

            // Ausführen, wenn die Batchgröße erreicht ist
            if (batchCounter % batchSize == 0) {
                stm.executeBatch();
            }
        }

        // Restliche Einträge im Batch ausführen
        stm.executeBatch();
    }

    // Füllt die Tabelle 'employee' mit 100.000 Einträgen
    public static void fillEmployee() throws SQLException {
        String sqlStm = "INSERT INTO employee(ssnum, name, manager, dept, salary, numfriends) VALUES (?,?,?,?,?,?)";
        PreparedStatement stm = con.prepareStatement(sqlStm);

        int batchSize = 100000;
        int batchCounter = 0;
        int insertCounter = 1;

        int ssnum = 111111;

        while (insertCounter <= 100000) {
            insertCounter++;

            String name = "Name_" + ssnum;

            // 10% Chance, dass der Mitarbeiter in einer technischen Abteilung arbeitet
            double techdeptChance = random.nextDouble();
            String dept;
            String manager;
            if(techdeptChance <= 0.1){
                dept = techdepts.get(random.nextInt(techdepts.size()));
                manager = mapDeptToManager.get(dept);
            } else {
                dept = depts.get(random.nextInt(depts.size()));
                manager = mapDeptToManager.get(dept);
            }

            // Zufälliges Gehalt zwischen 40.000 und 99.999 sowie 3 bis 19 Freunde
            int salary = random.nextInt(60000) + 40000;
            int numFriends = random.nextInt(17) + 3;

            // Parameter setzen
            stm.setInt(1, ssnum);
            stm.setString(2, name);
            stm.setString(3, manager);
            stm.setString(4, dept);
            stm.setInt(5, salary);
            stm.setInt(6, numFriends);

            stm.addBatch();
            batchCounter++;
            ssnum = ssnum + 2;

            if (batchCounter % batchSize == 0) {
                stm.executeBatch();
            }
        }

        stm.executeBatch();
    }

    // Füllt die Tabelle 'student' mit 100.000 Einträgen
    public static void fillStudent() throws SQLException {
        String sqlStm = "INSERT INTO student(ssnum, name, course, grade) VALUES (?,?,?,?)";
        PreparedStatement stm = con.prepareStatement(sqlStm);

        int batchSize = 100000;
        int batchCounter = 0;
        int insertCounter = 1;

        int ssnum = 280000;

        while (insertCounter <= 100000) {
            insertCounter++;

            String name = "Name_" + ssnum;
            String course = courses.get(random.nextInt(courses.size()));
            
            // Zufallsnote zwischen 1 und 4
            int grade = random.nextInt(4) + 1;

            // Parameter setzen
            stm.setInt(1, ssnum);
            stm.setString(2, name);
            stm.setString(3, course);
            stm.setInt(4, grade);

            stm.addBatch();
            batchCounter++;
            ssnum = ssnum + 2;

            if (batchCounter % batchSize == 0) {
                stm.executeBatch();
            }
        }

        stm.executeBatch();
    }

    // Führt eine SQL-Abfrage aus und misst die dafür benötigte Zeit in Millisekunden
    public static long executeQuery(String sql) {
        long duration = 0;

        // Try-with-resources sorgt dafür, dass das Statement am Ende automatisch geschlossen wird
        try (Statement stmt = con.createStatement()) {
            long startTime = System.currentTimeMillis();
            stmt.execute(sql);
            long endTime = System.currentTimeMillis();
            duration = endTime - startTime; 
        } catch (SQLException e) {
            System.out.println("Error executing query: " + e.getMessage());
        }
        return duration; // Rückgabe der Laufzeit
    }

    public static void main(String[] args) throws SQLException {
        // Zur Datenbank verbinden
        connect();
        
        // Die Erstellung des Schemas ist standardmäßig auskommentiert, 
        // um den Performance-Test auf bestehenden Daten auszuführen.
        // createSchema();

        // --- Performance-Vergleich ---

        // Testfall 1: Nutzung von DISTINCT zum Vermeiden von doppelten Mitarbeiter-Nummern
        String originalQuery = "SELECT DISTINCT ssnum FROM Employee WHERE dept = 'Techdept_B'";
        long originalQueryTime = executeQuery(originalQuery);
        System.out.println("Original query execution time: " + originalQueryTime + " milliseconds.");

        // Testfall 2: Nutzung von GROUP BY als Alternative zu DISTINCT (oft vom Optimizer anders behandelt)
        String rewrittenQuery = "SELECT ssnum FROM Employee WHERE dept = 'Techdept_B'  GROUP BY ssnum";
        long rewrittenQueryTime = executeQuery(rewrittenQuery);
        System.out.println("Rewritten query execution time: " + rewrittenQueryTime + " milliseconds.");
        
        // Datenbankverbindung schließen
        con.close();
    }
}