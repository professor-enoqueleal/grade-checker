package br.com.gradechecker.adapter.outbound.jdbc;

import br.com.gradechecker.adapter.inbound.web.BootstrapDatabaseServletTestSupport;
import br.com.gradechecker.adapter.outbound.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.core.group.Group;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

public class JdbcGroupRepositoryTest {

    @BeforeClass
    public static void setupDatabase() throws Exception {
        // Use H2 in-memory database for tests
        System.setProperty("DB_URL", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        System.setProperty("DB_USER", "sa");
        System.setProperty("DB_PASS", "");

        // Run schema from resources/db.sql
        try (Connection conn = ConnectionPoolConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            InputStream is = JdbcGroupRepositoryTest.class.getResourceAsStream("/db.sql");
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
    public void findActiveListAndById() throws Exception {
        CacheManager.invalidateAll();
        TestGroups.create("group-repository-test"); // db.sql may come without seed groups
        JdbcGroupRepository repo = new JdbcGroupRepository();

        List<Group> groups = repo.findActive();

        assertNotNull(groups);
        assertFalse(groups.isEmpty());

        Group g = groups.get(0);
        Group detail = repo.findActive(g.getId());

        assertNotNull(detail);
        assertEquals(g.getId(), detail.getId());
    }
    // ---------- group management (openspec capability group-management)

    private static Group group(String name, String url) {
        String[] parts = url.replace("https://github.com/", "").split("/");
        return new Group(name, url, parts[0], parts[1]);
    }

    @Test
    public void createUpdateArchiveAndRestoreKeepMembersAndAliases() throws Exception {
        CacheManager.invalidateAll();
        JdbcGroupRepository repo = new JdbcGroupRepository();
        long id = repo.create(group("crud-test", "https://github.com/test/crud-test"));
        assertTrue(id > 0);
        new JdbcMemberRepository().addIfAbsent(id, "crud-student", "crud-student", true);
        new JdbcIdentityAliasRepository().save(id, "name:crud", "crud-student", "professor");
        assertEquals(1, repo.findActive().stream().filter(g -> g.getId() == id).findFirst().get().getMemberCount());

        // edit: same id, members and aliases stay; caches see the new values
        assertEquals("crud-test", repo.findActive(id).getName());          // cached
        Group edited = group("crud-renamed", "https://github.com/test/crud-renamed");
        edited.setId(id);
        assertTrue(repo.update(edited));
        assertEquals("crud-renamed", repo.findActive(id).getName());
        assertEquals("https://github.com/test/crud-renamed", repo.findActive(id).getRepositoryUrl());

        // archive: out of both findActive, listed as archived, nothing deleted
        assertTrue(repo.archive(id));
        assertFalse("already archived", repo.archive(id));
        assertNull(repo.findActive(id));
        assertTrue(repo.findActive().stream().noneMatch(g -> g.getId() == id));
        assertTrue(repo.findArchived().stream().anyMatch(g -> g.getId() == id && g.getArchivedAt() != null));
        assertFalse("an archived group cannot be edited", repo.update(edited));
        assertTrue(new JdbcMemberRepository().isMember(id, "crud-student"));
        assertEquals("crud-student", new JdbcIdentityAliasRepository().findByGroupId(id).get("name:crud"));

        // restore: back with the same members and aliases
        assertTrue(repo.restore(id));
        assertFalse("already active", repo.restore(id));
        assertNotNull(repo.findActive(id));
        assertTrue(repo.findActive().stream().anyMatch(g -> g.getId() == id));
        assertTrue(new JdbcMemberRepository().isMember(id, "crud-student"));
    }

    @Test
    public void findByRepositoryIgnoresCaseGitAndSlashAndSeesArchivedGroups() throws Exception {
        CacheManager.invalidateAll();
        JdbcGroupRepository repo = new JdbcGroupRepository();
        long id = repo.create(group("dup-test", "https://github.com/Test/Dup-Test"));

        assertEquals(id, repo.findByRepository("https://github.com/test/dup-test.git/", null).getId());
        assertNull("the edited group itself does not count", repo.findByRepository("https://github.com/test/dup-test", id));
        assertNull(repo.findByRepository("https://github.com/test/other", null));

        repo.archive(id);
        Group archived = repo.findByRepository("https://github.com/test/dup-test", null);
        assertEquals(id, archived.getId());
        assertTrue(archived.isArchived());
        repo.restore(id);
    }

    @Test
    public void archivedSeedIsNotCreatedAgainOnTheNextStart() throws Exception {
        CacheManager.invalidateAll();
        JdbcGroupRepository repo = new JdbcGroupRepository();
        String seed = "https://github.com/Hugodev0711/clinica-pi";
        Group group = repo.findByRepository(seed, null);
        org.junit.Assume.assumeNotNull(group);   // db.sql may come without seeds

        assertTrue(repo.archive(group.getId()));
        try {
            assertTrue(BootstrapDatabaseServletTestSupport.reload());   // next start
            try (Connection c = ConnectionPoolConfig.getConnection(); Statement st = c.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM GROUPS WHERE REPOSITORY_URL = '" + seed + "'")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
            assertTrue(repo.find(group.getId()).isArchived());
        } finally {
            repo.restore(group.getId());
        }
    }

}
