package br.com.gradechecker.config;

import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionPoolConfig {

    private static BasicDataSource dataSource;

    private static BasicDataSource getDataSource() {

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

    public static Connection getConnection() throws SQLException {

        return getDataSource().getConnection();

    }

}