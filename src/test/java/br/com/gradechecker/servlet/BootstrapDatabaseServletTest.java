package br.com.gradechecker.servlet;

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
            assertTrue("seed groups from db.sql", rs.getInt(1) > 0);
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
