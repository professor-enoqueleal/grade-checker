package br.com.gradechecker.config;

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * openspec capability database-bootstrap: the database is closed (and its writes kept) when the application stops.
 * Tests use their own pools: the shared static pool of ConnectionPoolConfig is never shut down here.
 */
public class ConnectionPoolConfigTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static BasicDataSource pool(String url) {
        BasicDataSource pool = new BasicDataSource();
        pool.setUrl(url);
        pool.setUsername("sa");
        pool.setPassword("");
        return pool;
    }

    @Test
    public void writeMadeJustBeforeTheShutdownIsKept() throws Exception {
        String url = "jdbc:h2:" + folder.getRoot().getAbsolutePath() + "/close-test;DB_CLOSE_ON_EXIT=FALSE";
        BasicDataSource pool = pool(url);
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE T(ID INT)");
            s.execute("INSERT INTO T VALUES (42)");
        }
        Connection borrowed = pool.getConnection();     // a request still holding a connection

        ConnectionPoolConfig.close(pool, url);

        assertTrue(pool.isClosed());
        assertTrue("SHUTDOWN invalidates connections still in use", borrowed.isClosed());
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT ID FROM T")) {
            assertTrue(rs.next());
            assertEquals(42, rs.getInt(1));
        }
    }

    @Test
    public void nonH2DatabaseOnlyClosesThePool() throws Exception {
        BasicDataSource pool = pool("jdbc:unknown:db");
        ConnectionPoolConfig.close(pool, "jdbc:unknown:db");
        assertTrue(pool.isClosed());
    }

    @Test
    public void failureIsOnlyLogged() {
        BasicDataSource pool = pool("jdbc:h2:/nonexistent-dir-4242/x;IFEXISTS=TRUE");
        ConnectionPoolConfig.close(pool, pool.getUrl());   // cannot connect: logged, no exception
        assertTrue(pool.isClosed());
    }
}
