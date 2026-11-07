package br.com.gradechecker.repository;

import br.com.gradechecker.servlet.BootstrapDatabaseServletTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/** openspec capability identity-aliases: persistence of manual associations. */
public class IdentityAliasRepositoryTest {

    @BeforeClass
    public static void database() {
        BootstrapDatabaseServletTestSupport.loadSchema();
    }

    @Test
    public void savesUpdatesListsAndDeletesPerGroup() {
        IdentityAliasRepository repository = new IdentityAliasRepository();
        repository.delete(1, "name:repo-test");
        repository.delete(2, "name:repo-test");

        repository.save(1, "name:repo-test", "alice", "professor-enoqueleal");
        repository.save(1, "name:repo-test", "bob", "professor-enoqueleal"); // same identity: points to the new login
        repository.save(2, "name:repo-test", "carol", "professor-enoqueleal");

        Map<String, String> group1 = repository.findByGroupId(1);
        assertEquals("bob", group1.get("name:repo-test"));
        assertEquals("carol", repository.findByGroupId(2).get("name:repo-test"));

        assertTrue(repository.delete(1, "name:repo-test"));
        assertFalse(repository.delete(1, "name:repo-test"));
        assertNull(repository.findByGroupId(1).get("name:repo-test"));
        assertEquals("other groups are untouched", "carol", repository.findByGroupId(2).get("name:repo-test"));
        repository.delete(2, "name:repo-test");
    }
}
