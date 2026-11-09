package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.ConnectionPoolConfig;

import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;

/** openspec capability database-bootstrap. */
public class BootstrapDatabaseServletTest {

    @BeforeClass
    public static void database() {
        System.setProperty("DB_URL", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        System.setProperty("DB_USER", "sa");
        System.setProperty("DB_PASS", "");
    }

    @Test
    public void runsDbSqlFromTheClasspathRegardlessOfWorkingDirectory() throws Exception {
        assertTrue(new BootstrapDatabaseServlet().loadDatabase(BootstrapDatabaseServlet.SCRIPT));

        try (Connection connection = ConnectionPoolConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM GROUPS")) {
            assertTrue(rs.next());
            // seed groups are optional (the script may come with only the schema)
            if (hasActiveSeeds()) assertTrue("seed groups from db.sql", rs.getInt(1) > 0);
        }
    }

    @Test
    public void schemaHasNoContributorsTable() throws Exception {
        // openspec capability group-members: contributors come from GitHub, MEMBERS has no foreign key to them
        String script = script().toUpperCase();
        assertFalse(script.contains("CREATE TABLE IF NOT EXISTS CONTRIBUTORS"));
        assertFalse(script.contains("REFERENCES CONTRIBUTORS"));
    }

    private static String script() throws Exception {
        return new String(BootstrapDatabaseServlet.class.getResourceAsStream("/db.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean hasActiveSeeds() throws Exception {
        for (String line : script().split("\n")) {
            if (line.trim().toUpperCase().startsWith("INSERT INTO GROUPS")) return true;
        }
        return false;
    }

    @Test
    public void runningTheScriptAgainKeepsDataAndDoesNotDuplicateSeeds() throws Exception {
        BootstrapDatabaseServlet bootstrap = new BootstrapDatabaseServlet();
        assertTrue(bootstrap.loadDatabase(BootstrapDatabaseServlet.SCRIPT));

        String groups = groupIds();
        try (Connection connection = ConnectionPoolConfig.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO MEMBERS (NAME, GITHUB_USERNAME, GROUP_ID) VALUES ('Alice', 'alice-restart', 1)");
            statement.execute("INSERT INTO IDENTITY_ALIASES (GROUP_ID, IDENTITY_KEY, LOGIN) VALUES (1, 'name:restart', 'alice-restart')");
        }

        assertTrue("second start (restart)", bootstrap.loadDatabase(BootstrapDatabaseServlet.SCRIPT));

        assertEquals("seed groups are not duplicated and keep their ids", groups, groupIds());
        assertEquals(1, count("SELECT COUNT(*) FROM MEMBERS WHERE GITHUB_USERNAME = 'alice-restart'"));
        assertEquals(1, count("SELECT COUNT(*) FROM IDENTITY_ALIASES WHERE IDENTITY_KEY = 'name:restart'"));
    }

    @Test
    public void scriptNeverDropsOrDeletes() throws Exception {
        String script = new String(BootstrapDatabaseServlet.class.getResourceAsStream("/db.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        for (String line : script.split("\n")) {
            String sql = line.trim().toUpperCase();
            if (sql.startsWith("--")) continue;
            assertFalse(line, sql.startsWith("DROP ") || sql.startsWith("DELETE ") || sql.startsWith("TRUNCATE "));
            if (sql.startsWith("CREATE TABLE")) assertTrue(line, sql.startsWith("CREATE TABLE IF NOT EXISTS"));
        }
    }

    private static String groupIds() throws Exception {
        StringBuilder ids = new StringBuilder();
        try (Connection connection = ConnectionPoolConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT ID, REPOSITORY_URL FROM GROUPS ORDER BY ID")) {
            while (rs.next()) ids.append(rs.getInt(1)).append('=').append(rs.getString(2)).append('\n');
        }
        return ids.toString();
    }

    private static int count(String sql) throws Exception {
        try (Connection connection = ConnectionPoolConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    public void missingScriptIsReportedInsteadOfIgnored() {
        assertFalse(new BootstrapDatabaseServlet().loadDatabase("/does-not-exist.sql"));
    }

    @Test
    public void scriptIsPackagedOnTheClasspath() {
        assertNotNull(BootstrapDatabaseServlet.class.getResourceAsStream("/db.sql"));
    }
}
