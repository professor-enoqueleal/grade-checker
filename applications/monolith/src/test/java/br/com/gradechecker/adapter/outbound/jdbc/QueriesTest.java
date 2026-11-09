package br.com.gradechecker.adapter.outbound.jdbc;

import org.h2.tools.RunScript;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/** openspec capability sql-queries: every query is valid SQL, and no SQL lives in the repositories. */
public class QueriesTest {

    private static Connection connection;
    private static final Properties properties = new Properties();

    @BeforeClass
    public static void setUp() throws Exception {
        // in-memory H2 with the real schema (db.sql)
        connection = DriverManager.getConnection("jdbc:h2:mem:queries-test;DB_CLOSE_DELAY=-1", "sa", "");
        RunScript.execute(connection, new FileReader("src/main/resources/db.sql"));

        try (InputStream in = QueriesTest.class.getResourceAsStream("/queries.properties")) {
            properties.load(in);
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        connection.close();
    }

    @Test
    public void everyQueryCompilesAgainstTheSchema() {
        List<String> invalid = new ArrayList<>();

        for (String key : properties.stringPropertyNames()) {
            // dynamic IN lists (%s) are filled only with "?" placeholders by the repositories
            String sql = properties.getProperty(key).replace("%s", "?, ?");
            try (PreparedStatement ignored = connection.prepareStatement(sql)) {
                // compiled: tables and columns exist
            } catch (Exception e) {
                invalid.add(key + " -> " + e.getMessage().split("\n")[0]);
            }
        }

        assertTrue("Invalid queries in queries.properties:\n" + String.join("\n", invalid), invalid.isEmpty());
    }

    @Test
    public void getReturnsTheConfiguredQuery() {
        assertEquals("SELECT * FROM GROUPS WHERE ARCHIVED_AT IS NULL ORDER BY ID", Queries.get("group.findAll"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownKeyFails() {
        Queries.get("does.not.exist");
    }

    @Test
    public void repositoriesUseOnlySqlQueriesConstants() throws Exception {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(Path.of("src/main/java/br/com/gradechecker/adapter/outbound/jdbc"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                String name = p.getFileName().toString();
                if (name.equals("SqlQueries.java") || name.equals("Queries.java")) continue;
                if (Files.readString(p).contains("Queries.get(")) offenders.add(name);
            }
        }

        assertTrue("Repositories must use SqlQueries constants, not Queries.get: " + offenders, offenders.isEmpty());
        assertFalse("SqlQueries is the single access point",
                Files.exists(Path.of("src/main/java/br/com/gradechecker/adapter/outbound/jdbc/SqlQueriesForMembers.java")));
    }

    @Test
    public void repositoriesHaveNoSqlLiterals() throws Exception {
        Pattern sqlLiteral = Pattern.compile("\"\\s*(SELECT|INSERT|UPDATE|DELETE|MERGE)\\s", Pattern.CASE_INSENSITIVE);
        List<String> offenders;

        try (Stream<Path> files = Files.walk(Path.of("src/main/java/br/com/gradechecker/adapter/outbound/jdbc"))) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> {
                        try {
                            List<String> found = new ArrayList<>();
                            List<String> lines = Files.readAllLines(p);
                            for (int i = 0; i < lines.size(); i++) {
                                Matcher m = sqlLiteral.matcher(lines.get(i));
                                if (m.find()) found.add(p.getFileName() + ":" + (i + 1));
                            }
                            return found.stream();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }

        assertTrue("SQL literals must live in queries.properties: " + offenders, offenders.isEmpty());
    }
}
