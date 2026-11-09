package br.com.gradechecker.adapter.outbound.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility to load SQL queries from /queries.properties
 */
public final class Queries {

    private static final Properties props = new Properties();

    static {
        try (InputStream in = Queries.class.getResourceAsStream("/queries.properties")) {
            if (in == null) {
                throw new IllegalStateException("queries.properties not found on classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load queries.properties: " + e.getMessage());
        }
    }

    private Queries() {
        // utility
    }

    public static String get(String key) {

        String v = props.getProperty(key);

        if (v == null) {
            throw new IllegalArgumentException("Query not found for key: " + key);
        }

        return v;

    }

}

