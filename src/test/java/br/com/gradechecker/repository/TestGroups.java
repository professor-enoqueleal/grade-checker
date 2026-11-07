package br.com.gradechecker.repository;

import br.com.gradechecker.config.ConnectionPoolConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/** Groups created by the tests themselves: db.sql may come without seed groups. */
public final class TestGroups {

    private TestGroups() {
    }

    /** Creates (or reuses) a group with this name and returns its id. */
    public static long create(String name) throws Exception {
        String url = "https://github.com/test/" + name;
        try (Connection c = ConnectionPoolConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT ID FROM GROUPS WHERE REPOSITORY_URL = ?")) {
                ps.setString(1, url);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES (?, ?, 'test', ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, url);
                ps.setString(3, name);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        }
    }
}
