package br.com.gradechecker.repository;

import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Member;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.Assert.*;

public class MemberRepositoryTest {

    @BeforeClass
    public static void setupDatabase() throws Exception {
        // Use same in-memory DB as other tests
        System.setProperty("DB_URL", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        System.setProperty("DB_USER", "sa");
        System.setProperty("DB_PASS", "");

        // Run schema from resources/db.sql if not already present
        try (Connection conn = ConnectionPoolConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            InputStream is = MemberRepositoryTest.class.getResourceAsStream("/db.sql");
            if (is == null) {
                throw new IllegalStateException("db.sql not found in test resources");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                for (String sql : sb.toString().split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (Exception ignored) {
                            // ignore if already created
                        }
                    }
                }
            }

            // Insert a test group to reference
            stmt.execute("INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('test', 'url', 'owner', 'repo')");
        }
    }

    @Test
    public void create_and_findById_work() throws Exception {
        // find group id
        int groupId;
        try (Connection conn = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT ID FROM GROUPS LIMIT 1");
             java.sql.ResultSet rs = ps.executeQuery()) {
            rs.next();
            groupId = rs.getInt("ID");
        }

        Member member = new Member("John Doe", "johndoe", String.valueOf(groupId));

        MemberRepository repo = new MemberRepository();
        repo.create(member);

        int count = repo.findById((long) groupId);
        assertTrue(count >= 1);
    }
}
