package br.com.gradechecker.adapter.outbound.jdbc;

import br.com.gradechecker.adapter.inbound.web.BootstrapDatabaseServletTestSupport;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/** openspec capability identity-aliases: persistence of manual associations. */
public class JdbcIdentityAliasRepositoryTest {

    private static long g1;
    private static long g2;

    @BeforeClass
    public static void database() throws Exception {
        BootstrapDatabaseServletTestSupport.loadSchema();
        g1 = TestGroups.create("alias-test-1"); // db.sql may come without seed groups
        g2 = TestGroups.create("alias-test-2");
    }

    @Test
    public void savesUpdatesListsAndDeletesPerGroup() {
        JdbcIdentityAliasRepository repository = new JdbcIdentityAliasRepository();
        repository.delete(g1, "name:repo-test");
        repository.delete(g2, "name:repo-test");

        repository.save(g1, "name:repo-test", "alice", "professor-enoqueleal");
        repository.save(g1, "name:repo-test", "bob", "professor-enoqueleal"); // same identity: points to the new login
        repository.save(g2, "name:repo-test", "carol", "professor-enoqueleal");

        Map<String, String> group1 = repository.findByGroupId(g1);
        assertEquals("bob", group1.get("name:repo-test"));
        assertEquals("carol", repository.findByGroupId(g2).get("name:repo-test"));

        assertTrue(repository.delete(g1, "name:repo-test"));
        assertFalse(repository.delete(g1, "name:repo-test"));
        assertNull(repository.findByGroupId(g1).get("name:repo-test"));
        assertEquals("other groups are untouched", "carol", repository.findByGroupId(g2).get("name:repo-test"));
        repository.delete(g2, "name:repo-test");
    }

    @Test
    public void existsForLoginIgnoresCaseAndGroup() {
        JdbcIdentityAliasRepository repository = new JdbcIdentityAliasRepository();
        repository.save(g1, "name:exists-test", "Alice", "professor-enoqueleal");
        try {
            assertTrue(repository.existsForLogin(g1, "alice"));
            assertFalse(repository.existsForLogin(g2, "alice"));
            assertFalse(repository.existsForLogin(g1, "bob"));
        } finally {
            repository.delete(g1, "name:exists-test");
        }
    }
}
