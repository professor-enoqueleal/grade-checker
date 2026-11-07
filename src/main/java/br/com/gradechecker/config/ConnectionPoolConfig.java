package br.com.gradechecker.config;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectionPoolConfig {

    private static final Logger logger = LogManager.getLogger(ConnectionPoolConfig.class);

    private static BasicDataSource dataSource;

    private static synchronized BasicDataSource getDataSource() {

        if (dataSource == null) {

            dataSource = new BasicDataSource();

            // Allow overriding via system properties (useful in tests) or environment variables
            String dbUrl = System.getProperty("DB_URL");
            if (dbUrl == null) dbUrl = System.getenv("DB_URL");
            if (dbUrl == null) dbUrl = "jdbc:h2:~/h2db/grade-checker;DB_CLOSE_ON_EXIT=FALSE"; // default

            String dbUser = System.getProperty("DB_USER");
            if (dbUser == null) dbUser = System.getenv("DB_USER");
            if (dbUser == null) dbUser = "sa";

            String dbPass = System.getProperty("DB_PASS");
            if (dbPass == null) dbPass = System.getenv("DB_PASS");
            if (dbPass == null) dbPass = "sa";

            dataSource.setUrl(dbUrl);
            dataSource.setUsername(dbUser);
            dataSource.setPassword(dbPass);
            dataSource.setMinIdle(1);
            dataSource.setMaxIdle(5);
            dataSource.setMaxTotal(20);

            // Optional: validation query for H2
            dataSource.setValidationQuery("SELECT 1");

            System.out.println("Connection pool initialized for URL: " + dbUrl);

        }

        return dataSource;

    }

    public static synchronized Connection getConnection() throws SQLException {

        return getDataSource().getConnection();

    }

    /**
     * Closes the database when the application stops (openspec capability database-bootstrap): H2 writes to disk
     * with a delay and the URL uses DB_CLOSE_ON_EXIT=FALSE, so without this the last writes may be lost on a restart.
     * A later {@link #getConnection()} creates a new pool.
     */
    public static synchronized void shutdown() {

        BasicDataSource current = dataSource;
        dataSource = null;
        if (current != null) close(current, current.getUrl());

    }

    /** Runs SHUTDOWN on an H2 database (writes everything to disk and closes it) and closes the pool; errors are only logged. */
    static void close(BasicDataSource pool, String url) {

        if (url != null && url.startsWith("jdbc:h2:")) {
            try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
                logger.info("Database closed: {}", url);
            } catch (SQLException e) {
                logger.error("Fail on closing the database {}: {}", url, e.getMessage());
            }
        }

        try {
            pool.close();
        } catch (SQLException e) {
            logger.error("Fail on closing the connection pool: {}", e.getMessage());
        }

    }

}