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

public class Ass2Mdb_salary {
    // Statische Variablen für die Datenbankverbindung und den Zufallsgenerator (mit Seed für Reproduzierbarkeit)
    private static Connection con;
    private static Random random = new Random(42);
    
    // Vordefinierte Listen mit Testdaten (Manager, Kurse, Standorte)
    static List<String> managers = Arrays.asList("Manager_1", "Manager_2", "Manager_3", "Manager_4", "Manager_5", "Manager_6", "Manager_7", "Manager_8", "Manger_9", "Manger_10");
    static List<String> courses = Arrays.asList("Course_1", "Course_2", "Course_3", "Course_4", "Course_5", "Course_6", "Course_7", "Course_8", "Course_9", "Course_10");
    static List<String> locations = Arrays.asList("Location_1", "Location_2", "Location_3", "Location_4", "Location_5", "Location_6", "Location_7", "Location_8", "Location_9", "Location_10");
    
    // Listen und Maps, um Abteilungen und deren Manager-Zuweisungen zwischenzuspeichern
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
            // Verbindung aufbauen
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
            // Alte Tabelle löschen, falls vorhanden
            Statement deleteTdStm = con.createStatement();
            String deleteTd = "DROP Table if exists techdept";
            deleteTdStm.execute(deleteTd);

            // Neue Tabelle 'techdept' erstellen
            Statement createTdStm = con.createStatement();
            String createTd = "CREATE TABLE techdept(dept VARCHAR(30), manager VARCHAR(30), location VARCHAR(30), PRIMARY KEY(dept));";
            createTdStm.execute(createTd);

            // Eindeutigen B-Tree-Index auf die Spalte 'dept' setzen (verhindert Duplikate)
            Statement indexTdStm = con.createStatement();
            String indexTd = "CREATE UNIQUE INDEX index_dept_techdept on techdept(dept);";      
            indexTdStm.execute(indexTd);

            // Tabelle mit Daten füllen
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

            // Indizes erstellen: ssnum und name müssen eindeutig sein, dept bekommt einen normalen Index
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

    // Hilfsmethode, um Standard-Abteilungen zu generieren und Managern zuzuweisen
    public static void mapDepts() {
        char c = 'A';
        int deptCounter = 0;

        // Erstellt 21 reguläre Abteilungen (Dept_A bis Dept_U)
        while (deptCounter <= 20){
            String dept = "Dept_" + c;
            // Wählt einen zufälligen Manager für die Abteilung
            String manager = managers.get(random.nextInt(managers.size()));

            mapDeptToManager.put(dept, manager);
            depts.add(dept);
            deptCounter++;
            c++;
        }
    }

    // Füllt die Tabelle 'techdept' mit 10 zufälligen Einträgen
    public static void fillTechdept() throws SQLException {
        mapDepts(); // Zuerst Basis-Abteilungen generieren

        String sqlStm = "INSERT INTO techdept(dept, manager, location) VALUES (?,?,?)";
        PreparedStatement stm = con.prepareStatement(sqlStm);

        int batchSize = 10;
        int batchCounter = 0;
        int insertCounter = 1;
        char c = 'A';

        // Generiert 10 spezielle Tech-Abteilungen
        while (insertCounter <= 10) {
            insertCounter++;

            String dept = "Techdept_" + c;
            String manager = managers.get(random.nextInt(managers.size()));
            String location = locations.get(random.nextInt(locations.size()));

            // In Map und Liste eintragen für spätere Referenzen
            mapDeptToManager.put(dept, manager);
            techdepts.add(dept);

            // Parameter setzen
            stm.setString(1, dept);
            stm.setString(2, manager);
            stm.setString(3, location);

            // Zum Batch hinzufügen, um mehrere Statements gebündelt und damit schneller auszuführen
            stm.addBatch();
            batchCounter++;
            c++;

            if (batchCounter % batchSize == 0) {
                stm.executeBatch();
            }
        }

        // Verbleibende Einträge ausführen
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

            // 10% Chance, dass der Mitarbeiter in einer Tech-Abteilung arbeitet
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

            // Zufälliges Gehalt (40.000 bis 99.999) und Zufallsanzahl an Freunden (3 bis 19)
            int salary = random.nextInt(60000) + 40000;
            int numFriends = random.nextInt(17) + 3;

            // Parameter für das Statement binden
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
            
            // Zufällige Note zwischen 1 und 4
            int grade = random.nextInt(4) + 1;

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

    // Führt eine Abfrage aus und vergleicht die Performance einer Originalabfrage mit einer optimierten Version
    public static void queri_i() throws SQLException {
        // --- 1. Originalabfrage (Correlated Subquery) ---
        Statement queri_iStm = con.createStatement();
        String queri_i = "SELECT name FROM employee e WHERE salary > (SELECT AVG(e2.salary) FROM employee e2, techdept t WHERE e2.dept = e.dept AND e2.dept = t.dept)";
        java.time.Instant startTime = java.time.Instant.now();
        queri_iStm.executeQuery(queri_i);
        java.time.Instant endTime = java.time.Instant.now();
        long executionTime = java.time.Duration.between(startTime, endTime).toMillis();
        System.out.println("Original Query1 takes: " + executionTime +" milliseconds.");

        // --- 2. Umgeschriebene Abfrage (Optimiert mit temporärer Tabelle) ---
        Statement queri_i_createTempStm = con.createStatement();
        Statement a = con.createStatement();
        
        // Alte temporäre Tabelle löschen, falls vorhanden
        String b = "DROP TEMPORARY TABLE IF EXISTS Temp";
        a.execute(b);
        
        // Temporäre Tabelle erstellen und direkt mit den Durchschnittsgehältern pro Tech-Abteilung füllen
        String queri_i_createTemp = "CREATE TEMPORARY TABLE IF NOT EXISTS Temp AS SELECT AVG(e.salary) as averageSalary, e.dept FROM employee e, techdept t WHERE e.dept = t.dept GROUP BY e.dept";
        queri_i_createTempStm.execute(queri_i_createTemp);

        //Die neue Abfrage nutzt nun einen Join mit der vorberechneten temporären Tabelle
        Statement queri_i_rwStm = con.createStatement();
        String queri_irw = "SELECT name FROM employee e, temp te WHERE salary > averagesalary AND e.dept = te.dept";
        java.time.Instant startTimerw = java.time.Instant.now();
        queri_i_rwStm.executeQuery(queri_irw);
        java.time.Instant endTimerw = java.time.Instant.now();
        long executionTimerw = java.time.Duration.between(startTimerw, endTimerw).toMillis();
         System.out.println("Rewitten Query1 takes: " + executionTimerw +" milliseconds.");
    }

    public static void main(String[] args) throws SQLException {
        // Verbindungsaufbau zur MariaDB-Datenbank
        connect();
        
        // Schema erstellen und Tabellen mit Beispieldaten befüllen
        createSchema();
        
        // Performance-Tests ausführen
        queri_i();

        // Verbindung ordnungsgemäß schließen
        con.close();
    }
}