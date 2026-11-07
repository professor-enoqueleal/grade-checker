package br.com.gradechecker.repository;

import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Group;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

public class GroupRepositoryTest {

    @BeforeClass
    public static void setupDatabase() throws Exception {
        // Use H2 in-memory database for tests
        System.setProperty("DB_URL", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        System.setProperty("DB_USER", "sa");
        System.setProperty("DB_PASS", "");

        // Run schema from resources/db.sql
        try (Connection conn = ConnectionPoolConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            InputStream is = GroupRepositoryTest.class.getResourceAsStream("/db.sql");
            if (is == null) {
                throw new IllegalStateException("db.sql not found in test resources");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                // Split by semicolon to execute statements
                for (String sql : sb.toString().split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        }
    }

    @Test
    public void findAll_and_getGroupDetail_work() throws Exception {
        br.com.gradechecker.cache.CacheManager.invalidateAll();
        TestGroups.create("group-repository-test"); // db.sql may come without seed groups
        GroupRepository repo = new GroupRepository();

        List<Group> groups = repo.findAll();

        assertNotNull(groups);
        assertFalse(groups.isEmpty());

        Group g = groups.get(0);
        Group detail = repo.getGroupDetail(g.getId());

        assertNotNull(detail);
        assertEquals(g.getId(), detail.getId());
    }
}
