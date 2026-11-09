package br.com.gradechecker.adapter.outbound.jdbc;

import br.com.gradechecker.adapter.inbound.web.BootstrapDatabaseServletTestSupport;
import br.com.gradechecker.adapter.outbound.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.core.group.Member;
import br.com.gradechecker.core.port.MemberRepository;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/** openspec capability group-members: persistence of the members of a group. */
public class JdbcMemberRepositoryTest {

    private static long groupId;
    private final JdbcMemberRepository repository = new JdbcMemberRepository();

    @BeforeClass
    public static void database() throws Exception {
        BootstrapDatabaseServletTestSupport.loadSchema();
        groupId = TestGroups.create("members-test");
    }

    @Before
    public void clean() throws Exception {
        CacheManager.rebuild(true, 60L, 1000L);
        CacheManager.invalidateAll();
        try (Connection c = ConnectionPoolConfig.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM MEMBERS WHERE GROUP_ID = " + groupId);
            s.execute("DELETE FROM IDENTITY_ALIASES WHERE GROUP_ID = " + groupId);
        }
    }

    @Test
    public void addsIgnoringCaseListsSortedAndCounts() {
        assertTrue(repository.addIfAbsent(groupId, "Zed", "Zed Z", true));
        assertTrue(repository.addIfAbsent(groupId, "alice", "alice", false));
        assertFalse("already a member, ignoring case", repository.addIfAbsent(groupId, "ALICE", "Alice", true));

        List<Member> members = repository.findByGroupId(groupId);
        assertEquals(List.of("alice", "Zed"), members.stream().map(Member::getLogin).collect(Collectors.toList()));
        assertEquals("Zed Z", members.get(1).getName());
        assertEquals(2, repository.findByGroupId(groupId).size());
        assertTrue(repository.isMember(groupId, "zed"));
        assertEquals(List.of("Zed", "alice"), repository.findLoginsByGroupId(groupId));
    }

    @Test
    public void githubIdIsAlwaysNullAndCreatedManuallyIsKept() throws Exception {
        repository.addIfAbsent(groupId, "manual-one", "Manual", true);
        repository.addIfAbsent(groupId, "card-one", "card-one", false);

        try (Connection c = ConnectionPoolConfig.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT GITHUB_USERNAME, GITHUB_ID, CREATED_MANUALLY FROM MEMBERS WHERE GROUP_ID = "
                     + groupId + " ORDER BY GITHUB_USERNAME")) {
            assertTrue(rs.next());
            assertEquals("card-one", rs.getString(1));
            assertNull(rs.getObject(2));
            assertFalse(rs.getBoolean(3));
            assertTrue(rs.next());
            assertNull(rs.getObject(2));
            assertTrue(rs.getBoolean(3));
        }
    }

    @Test
    public void writesInvalidateTheCachedList() {
        assertEquals(0, repository.findByGroupId(groupId).size());
        assertTrue(repository.findByGroupId(groupId).isEmpty());

        repository.addIfAbsent(groupId, "bob", "bob", false);
        assertEquals(1, repository.findByGroupId(groupId).size());
        assertEquals(1, repository.findByGroupId(groupId).size());

        assertTrue(repository.delete(groupId, "BOB"));
        assertFalse(repository.delete(groupId, "bob"));
        assertEquals(0, repository.findByGroupId(groupId).size());
        assertTrue(repository.findByGroupId(groupId).isEmpty());
    }

    @Test
    public void renameChangesTheNameOnly() {
        repository.addIfAbsent(groupId, "alice", "alice", true);
        assertEquals(1, repository.findByGroupId(groupId).size());   // cached

        assertEquals(MemberRepository.RenameResult.OK, repository.rename(groupId, "ALICE", "alice", "Alice A."));

        List<Member> members = repository.findByGroupId(groupId);
        assertEquals("alice", members.get(0).getLogin());
        assertEquals("Alice A.", members.get(0).getName());          // cache invalidated
    }

    @Test
    public void renameMovesTheManualAssociationsToTheNewLogin() {
        JdbcIdentityAliasRepository aliases = new JdbcIdentityAliasRepository();
        repository.addIfAbsent(groupId, "joao-silv", "Joao", true);
        aliases.save(groupId, "name:joao", "joao-silv", "professor");

        assertEquals(MemberRepository.RenameResult.OK, repository.rename(groupId, "joao-silv", "joao-silva", "Joao"));

        assertTrue(repository.isMember(groupId, "joao-silva"));
        assertFalse(repository.isMember(groupId, "joao-silv"));
        assertEquals("joao-silva", aliases.findByGroupId(groupId).get("name:joao"));
        assertFalse(aliases.existsForLogin(groupId, "joao-silv"));
    }

    @Test
    public void renameFixesTheCaseOfTheLogin() {
        repository.addIfAbsent(groupId, "ThiagoFP77", "Thiago", true);
        new JdbcIdentityAliasRepository().save(groupId, "name:thiago", "ThiagoFP77", "professor");

        assertEquals(MemberRepository.RenameResult.OK, repository.rename(groupId, "ThiagoFP77", "thiagofp77", "Thiago"));

        assertEquals(List.of("thiagofp77"), repository.findLoginsByGroupId(groupId));
        assertEquals("thiagofp77", new JdbcIdentityAliasRepository().findByGroupId(groupId).get("name:thiago"));
    }

    @Test
    public void renameToAnotherMemberOrOfANonMemberChangesNothing() {
        JdbcIdentityAliasRepository aliases = new JdbcIdentityAliasRepository();
        repository.addIfAbsent(groupId, "alice", "Alice", true);
        repository.addIfAbsent(groupId, "bob", "Bob", true);
        aliases.save(groupId, "name:al", "alice", "professor");

        assertEquals(MemberRepository.RenameResult.ALREADY_MEMBER, repository.rename(groupId, "alice", "BOB", "x"));
        assertEquals(MemberRepository.RenameResult.NOT_MEMBER, repository.rename(groupId, "carol", "dave", "x"));

        assertEquals(List.of("alice", "bob"), repository.findLoginsByGroupId(groupId));
        assertEquals("Alice", repository.findByGroupId(groupId).get(0).getName());
        assertEquals("alice", aliases.findByGroupId(groupId).get("name:al"));
    }

    @Test
    public void databaseOfAnOlderVersionWithContributorsForeignKeyStillAcceptsMembers() throws Exception {
        try (Connection c = ConnectionPoolConfig.getConnection(); Statement s = c.createStatement()) {
            // schema of the previous versions: CONTRIBUTORS table and MEMBERS.GITHUB_ID -> CONTRIBUTORS.ID
            s.execute("CREATE TABLE IF NOT EXISTS CONTRIBUTORS(ID INT PRIMARY KEY, LOGIN VARCHAR(255), AVATAR_URL VARCHAR(255),"
                    + " HTML_URL VARCHAR(255), CONTRIBUTIONS INT, GROUP_ID INT)");
            s.execute("ALTER TABLE MEMBERS ADD CONSTRAINT IF NOT EXISTS LEGACY_MEMBERS_CONTRIBUTORS FOREIGN KEY (GITHUB_ID) REFERENCES CONTRIBUTORS (ID)");
        }
        try {
            assertTrue(BootstrapDatabaseServletTestSupport.reload());   // db.sql again: starts normally, keeps the old table
            assertTrue(repository.addIfAbsent(groupId, "legacy-student", "legacy-student", true));
            assertTrue(repository.isMember(groupId, "legacy-student"));
        } finally {
            try (Connection c = ConnectionPoolConfig.getConnection(); Statement s = c.createStatement()) {
                s.execute("ALTER TABLE MEMBERS DROP CONSTRAINT IF EXISTS LEGACY_MEMBERS_CONTRIBUTORS");
                s.execute("DROP TABLE IF EXISTS CONTRIBUTORS");
            }
        }
    }

}
