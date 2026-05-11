import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Assignment_3 {
    // Statische Variablen für die Datenbankverbindung und Zufallszahlengenerierung
    private static Connection con;
    
    // Fester Seed (42) für den Random-Generator, damit die Ergebnisse bei jedem Lauf reproduzierbar sind
    private static final Random random = new Random(42);

    // Datenbank-Verbindungsdetails (Host, Port, DB-Name, Benutzer, Passwort)
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tuning";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "root"; // Update this

    // Dateipfade für die Rohdaten (TSV = Tab-Separated Values).
    // Diese müssen vom PostgreSQL-Serverprozess direkt lesbar sein.
    private static final String PUBL_TSV_PATH = "F:\\uni\\tuning\\projekt3\\code\\publ.tsv";
    private static final String AUTH_TSV_PATH = "F:\\uni\\tuning\\projekt3\\code\\auth.tsv";

    // Zwischenspeicher für zufällig ausgewählte Suchparameter.
    // Werden vor den Benchmarks geladen, um Performance-Messungen nicht zu verfälschen.
    private static List<String> randomPubIDs = new ArrayList<>();
    private static List<String> randomBooktitles = new ArrayList<>();
    private static List<String> randomYears = new ArrayList<>();

    public static void main(String[] args) {
        try {
            // 1. Datenbankverbindung herstellen
            connect();
            
            // 2. Datenbankschema leeren, neu anlegen und Daten direkt aus den TSV-Dateien importieren
            createSchema();
            loadData();
            
            // 3. Vorab zufällige Parameter (1000 Stück) laden, die später in die Benchmark-Queries eingesetzt werden
            fetchRandomParameters(1000); 

            // 4. Benchmarks starten - wir testen 4 verschiedene Index-Strategien:
            
            // Strategie 1: Full Table Scan (kein Index)
            System.out.println("TABLE SCAN (NO INDEX)");
            runAllQueries("NO_INDEX");

            // Strategie 2: Ungeclusterter B+ Baum (Daten auf Disk sind nicht nach dem Index sortiert)
            System.out.println("\nNON-CLUSTERING B+ TREE");
            runAllQueries("NON_CLUSTERING_BTREE");

            // Strategie 3: Ungeclusterter Hash-Index (Gut für exakte Übereinstimmungen / Point Queries)
            System.out.println("\nNON-CLUSTERING HASH INDEX");
            runAllQueries("NON_CLUSTERING_HASH");

            // Strategie 4: Geclusterter B+ Baum (Daten auf Disk werden physisch nach dem Index sortiert)
            System.out.println("\nCLUSTERING B+ TREE");
            runAllQueries("CLUSTERING_BTREE");

            System.out.println("Done.");
            
            // Verbindung ordnungsgemäß schließen
            con.close();
        } catch (SQLException e) {
            e.printStackTrace(); // Fehlerbehandlung bei SQL-Problemen
        }
    }

    // Stellt die JDBC-Verbindung zur PostgreSQL Datenbank her
    public static void connect() throws SQLException {
        con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        System.out.println("Successfully Connected.");
    }

    // Löscht existierende Tabellen und legt sie komplett leer neu an
    public static void createSchema() throws SQLException {
        Statement st = con.createStatement();
        st.execute("DROP TABLE IF EXISTS Auth");
        st.execute("DROP TABLE IF EXISTS Publ");

        // ACHTUNG: Es werden ABSICHTLICH keine Primary Keys (Primärschlüssel), Foreign Keys 
        // oder andere Constraints definiert! Würde man bspw. einen Primary Key setzen, 
        // würde PostgreSQL automatisch versteckt einen B-Tree-Index anlegen, was die Messung ruiniert.
        st.execute("CREATE TABLE Auth(name TEXT, pubID TEXT)");
        st.execute("CREATE TABLE Publ(pubID TEXT, type TEXT, title TEXT, booktitle TEXT, year TEXT, publisher TEXT)");
        System.out.println("Schema created successfully.");
    }

    // Lädt die Rohdaten rasend schnell direkt in die Datenbank
    public static void loadData() throws SQLException {
        System.out.println("Loading data... (This might take a moment depending on your disk)");
        Statement st = con.createStatement();
        
        // Verwendet den nativen PostgreSQL COPY-Befehl. Er ist deutlich schneller als 
        // einzelne INSERT-Statements, weil er die Datei direkt auf Serverseite parst.
        st.execute("COPY Auth FROM '" + AUTH_TSV_PATH + "' DELIMITER E'\\t' CSV");
        st.execute("COPY Publ FROM '" + PUBL_TSV_PATH + "' DELIMITER E'\\t' CSV");
        System.out.println("Data loaded successfully.");
    }

    // Holt im Voraus Testparameter aus der Datenbank, um sie als Variablen für die Query-Benchmarks zu nutzen
    public static void fetchRandomParameters(int limit) throws SQLException {
        System.out.println("Fetching random parameters for queries...");
        Statement st = con.createStatement();

        // Holt zufällige pubIDs per RANDOM() Sortierung
        ResultSet rs = st.executeQuery("SELECT pubID FROM Publ ORDER BY RANDOM() LIMIT " + limit);
        while (rs.next()) randomPubIDs.add(rs.getString(1));

        // Holt zufällige Buchtitel (ignoriert leere Werte). Eignet sich gut für "Low Selectivity" 
        // (niedrige Selektivität bedeutet: viele Zeilen matchen für denselben Buchtitel)
        rs = st.executeQuery("SELECT booktitle FROM (SELECT DISTINCT booktitle FROM Publ WHERE booktitle IS NOT NULL AND booktitle != '') AS t ORDER BY RANDOM() LIMIT " + limit);
        while (rs.next()) randomBooktitles.add(rs.getString(1));

        // Holt zufällige Jahre (ignoriert leere Werte). Eignet sich gut für "High Selectivity" 
        // (hohe Selektivität bedeutet: nur wenige/bestimmte proportionale Zeilen matchen für das selbe Jahr)
        rs = st.executeQuery("SELECT year FROM (SELECT DISTINCT year FROM Publ WHERE year IS NOT NULL AND year != '') AS t ORDER BY RANDOM() LIMIT " + limit);
        while (rs.next()) randomYears.add(rs.getString(1));
    }

    // Führt 4 verschiedene Test-Abfragen (Queries) für die gewählte Index-Strategie aus
    private static void runAllQueries(String indexStrategy) throws SQLException {
        // Jeder der 4 Query-Typen darf für ~20 Sekunden laufen, um statistisch sichere Durchschnittswerte zu bilden
        long durationPerQueryType = 20_000_000_000L; // 20 Sekunden umgerechnet in Nanosekunden

        // --- Q1: Punkt-Abfrage (Point Query) ---
        // Ziel: Exakt EINEN bestimmen Datensatz über den primary-key-ähnlichen Wert pubID finden.
        // Setup: Index wird auf 'pubID' erstellt. Fall wir "NON-CLUSTERED" testen, clustern wir absichtlich
        // auf einer unzusammenhängenden Spalte wie "publisher", damit die Daten physisch durchgemischt gelagert sind.
        setupIndex(indexStrategy, "pubID", "publisher"); 
        benchmark("SELECT * FROM Publ WHERE pubID = ?", indexStrategy, "Point (pubID)", randomPubIDs, durationPerQueryType, 1);

        // --- Q2: Multi-Punkt-Abfrage mit niedriger Selektivität (Low Selectivity) ---
        // Ziel: Alle Publikationen eines bestimmten Buchtitels finden (gibt viele Ergebnisse zurück).
        setupIndex(indexStrategy, "booktitle", "pubID");
        benchmark("SELECT * FROM Publ WHERE booktitle = ?", indexStrategy, "Low Selectivity (booktitle)", randomBooktitles, durationPerQueryType, 1);

        // --- Q3: Multi-Punkt-Abfrage mit IN-Operator --
        // Ziel: Sucht in einem Schritt nach 3 verschiedenen, genauen pubIDs. 
        setupIndex(indexStrategy, "pubID", "publisher");
        benchmark("SELECT * FROM Publ WHERE pubID IN (?, ?, ?)", indexStrategy, "IN Predicate (pubID)", randomPubIDs, durationPerQueryType, 3);

        // --- Q4: Multi-Punkt-Abfrage mit hoher Selektivität (High Selectivity) ---
        // Ziel: Sucht alle Publikationen eines spezifischen Jahres.
        setupIndex(indexStrategy, "year", "pubID");
        benchmark("SELECT * FROM Publ WHERE year = ?", indexStrategy, "High Selectivity (year)", randomYears, durationPerQueryType, 1);
    }

    // Konfiguriert die Indexstruktur und physische Sortierung direkt vor jedem Benchmark
    private static void setupIndex(String strategy, String targetColumn, String independentColumn) throws SQLException {
        Statement st = con.createStatement();
        
        // Alte, aus vorigen Durchläufen stammende Indizes radikal löschen
        st.execute("DROP INDEX IF EXISTS target_idx");
        st.execute("DROP INDEX IF EXISTS cluster_idx");

        switch (strategy) {
            case "NO_INDEX":
                // Nichts tun! Die Datenbank ist gezwungen, jede Zeile einzeln zu lesen ("Sequential Scan")
                break;
            case "NON_CLUSTERING_BTREE":
                // Normalen B-Tree auf die Suchspalte setzen
                st.execute("CREATE INDEX target_idx ON Publ (" + targetColumn + ")");
                // Trick: Damit die Daten physisch garantiert nicht nach 'targetColumn' sortiert sind,
                // erstellen wir temporär einen Index auf eine völlig andere Spalte und lassen 
                // Postgres die Tabelle physisch danach sortieren (CLUSTER command).
                st.execute("CREATE INDEX cluster_idx ON Publ (" + independentColumn + ")");
                st.execute("CLUSTER Publ USING cluster_idx");
                break;
            case "NON_CLUSTERING_HASH":
                // Setzt gezielt einen HASH Index. Hash-Indizes können nur auf exakte Werte matchen (keine Range-Queries wie >, <)
                st.execute("CREATE INDEX target_idx ON Publ USING hash (" + targetColumn + ")");
                // Gleicher Clustertrick wie beim nicht-geclusterten BTree
                st.execute("CREATE INDEX cluster_idx ON Publ (" + independentColumn + ")");
                st.execute("CLUSTER Publ USING cluster_idx");
                break;
            case "CLUSTERING_BTREE":
                // Setzt einen B-Tree Index...
                st.execute("CREATE INDEX target_idx ON Publ (" + targetColumn + ")");
                // ...und sortiert die Tabelle physisch exakt nach diesem Index um! (CLUSTER)
                // Dadurch liegen im Speicher benachbarte Werte auch physisch nebeneinander auf der Festplatte.
                st.execute("CLUSTER Publ USING target_idx"); 
                break;
        }
    }

    // Führt die eigentlichen Laufzeitmessungen aus (Benchmarking)
    private static void benchmark(String query, String strategy, String qName, List<String> params, long timeLimitNs, int paramCount) throws SQLException {
        // Statement vorbereiten (?, ?, ?) um SQL-Injection zu vermeiden und Caching in Postgres zu erlauben
        PreparedStatement pst = con.prepareStatement(query);
        long start = System.nanoTime(); // Zeiterfassung starten
        long queriesRun = 0; // Zähler, wie viele SQL queries wir in den ~20 Sekunden abfeuern konnten
        long totalExecutionTimeNs = 0; // Variable zum Aufsummieren der tatsächlichen DB-Ausführungszeit

        int paramsSize = params.size();

        // Unendliche Schleife...
        while (true) {
            long loopStart = System.nanoTime();
            
            // Abbruchbedingung: Zeitlimit von 20 Sekunden überschritten
            if (loopStart - start >= timeLimitNs) {
                break; 
            }

            // Wir füllen unser Prepared Statement iterativ mit zufällig ausgewählten 
            // Parametern, die wir anfangs von fetchRandomParameters() geladen haben.
            // paramCount steuert, wie viele "?" in der Query stecken.
            for (int i = 1; i <= paramCount; i++) {
                pst.setString(i, params.get(random.nextInt(paramsSize)));
            }
            
            // Abfrage zur Datenbank senden!
            pst.execute();
            
            // Statistik aktualisieren
            queriesRun++;
            totalExecutionTimeNs += (System.nanoTime() - loopStart); // Nur die tatsächliche Ausführungszeit der Query messen
        }
        pst.close();

        // ---------------------------------
        // Metriken berechnen & formatiert ausgeben
        // ---------------------------------
        // Umwandlung von Nanosekunden -> Sekunden
        double totalSeconds = totalExecutionTimeNs / 1_000_000_000.0;
        
        // Durchsatz = Anzahl Queries pro Sekunde
        double throughput = queriesRun / totalSeconds;
        
        // Durchschnittliche Laufzeit EIner Query in Millisekunden
        double avgRuntimeMs = (totalExecutionTimeNs / 1_000_000.0) / queriesRun;

        System.out.printf("  Query: %-25s | Queries Run: %8d | Avg Runtime: %8.3f ms/query | Throughput: %8.2f Q/sec%n",
                           qName, queriesRun, avgRuntimeMs, throughput);
    }
}