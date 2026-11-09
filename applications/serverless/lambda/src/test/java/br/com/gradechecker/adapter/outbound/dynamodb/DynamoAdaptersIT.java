package br.com.gradechecker.adapter.outbound.dynamodb;

import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.port.MemberRepository;
import br.com.gradechecker.core.port.RepositoryTakenException;
import br.com.gradechecker.session.Session;
import br.com.gradechecker.tools.Seed;

import com.google.gson.reflect.TypeToken;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

/** Stores over DynamoDB Local (capabilities dynamodb-data, session-auth, github-cache). mvn test -Pintegration */
public class DynamoAdaptersIT {

    private static LocalTables tables;
    private DynamoClient dynamo;
    private DynamoGroupRepository groups;
    private DynamoMemberRepository members;
    private DynamoIdentityAliasRepository aliases;

    @BeforeClass
    public static void create() {
        tables = LocalTables.create();
    }

    @AfterClass
    public static void drop() {
        if (tables != null) tables.drop();
    }

    @Before
    public void setUp() {
        tables.clear();
        dynamo = tables.dynamo();
        groups = new DynamoGroupRepository(dynamo);
        members = new DynamoMemberRepository(dynamo);
        aliases = new DynamoIdentityAliasRepository(dynamo);
    }

    private long group(String url) {
        return groups.create(new Group("g", url, "o", "r"));
    }

    @Test
    public void idsGrowAndTheRepositoryIsUniqueIgnoringSpelling() {
        long first = group("https://github.com/o/r");
        long second = group("https://github.com/o/other");
        assertEquals(first + 1, second);
        assertThrows(RepositoryTakenException.class, () -> group("https://www.GitHub.com/O/R.git/"));
        assertEquals(second, groups.findByRepository("https://github.com/o/other", null).getId());
        assertNull(groups.findByRepository("https://github.com/o/other", second));
    }

    @Test
    public void concurrentCreationOfTheSameRepositoryStoresOnlyOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                try {
                    group("https://github.com/o/same");
                    return true;
                } catch (RepositoryTakenException e) {
                    return false;
                }
            }));
        }
        int created = 0;
        for (Future<Boolean> f : results) if (f.get()) created++;
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(1, groups.findAll().size());
    }

    @Test
    public void editingTheRepositoryReleasesTheOldOneAndArchivedKeepsIt() {
        long id = group("https://github.com/o/a");
        long other = group("https://github.com/o/b");
        Group edit = new Group(id, "renamed", "https://github.com/o/c", "o", "c");
        assertTrue(groups.update(edit));
        assertEquals("renamed", groups.find(id).getName());
        assertNull(groups.findByRepository("https://github.com/o/a", null));      // released
        assertThrows(RepositoryTakenException.class,
                () -> groups.update(new Group(id, "x", "https://github.com/o/b", "o", "b")));

        assertTrue(groups.archive(other));
        assertFalse(groups.archive(other));
        assertFalse(groups.update(new Group(other, "x", "https://github.com/o/z", "o", "z")));  // archived: no edit
        assertTrue(groups.findByRepository("https://github.com/o/b", null).isArchived());
        assertEquals(1, groups.findActive().size());
        assertEquals(1, groups.findArchived().size());
        assertTrue(groups.restore(other));
        assertFalse(groups.restore(other));
    }

    @Test
    public void membersKeepTheCountAndRenameMovesTheAliases() {
        long id = group("https://github.com/o/r");
        assertTrue(members.addIfAbsent(id, "Ana", "Ana", true));
        assertFalse(members.addIfAbsent(id, "ana", "dup", false));
        assertTrue(members.addIfAbsent(id, "bruno", "Bruno", false));
        assertEquals(2, groups.find(id).getMemberCount());

        aliases.save(id, "email:a@local", "Ana", "professor");
        assertEquals(MemberRepository.RenameResult.ALREADY_MEMBER,
                members.rename(id, "Ana", "bruno", "x"));
        assertEquals(MemberRepository.RenameResult.OK, members.rename(id, "ana", "ana-b", "Ana B"));
        assertEquals("ana-b", aliases.findByGroupId(id).get("email:a@local"));
        assertEquals(List.of("ana-b", "bruno"), members.findLoginsByGroupId(id));
        assertEquals(MemberRepository.RenameResult.NOT_MEMBER, members.rename(id, "ana", "x", "x"));
        assertEquals(MemberRepository.RenameResult.OK, members.rename(id, "bruno", "Bruno", "Bruno M"));
        assertEquals("Bruno", members.findByGroupId(id).get(1).getLogin());

        assertTrue(members.delete(id, "BRUNO"));
        assertFalse(members.delete(id, "bruno"));
        assertEquals(1, groups.find(id).getMemberCount());
        assertTrue(aliases.delete(id, "email:a@local"));
        assertFalse(aliases.delete(id, "email:a@local"));
    }

    @Test
    public void seedIsIdempotentAndKeepsArchivedGroups() {
        int created = Seed.run(groups);
        assertEquals(Seed.load().size(), created);
        long first = groups.findAll().get(0).getId();
        groups.archive(first);
        assertEquals(0, Seed.run(groups));
        assertEquals(created, groups.findAll().size());
        assertTrue(groups.find(first).isArchived());
    }

    @Test
    public void sessionsExpireAndStatesAreSingleUse() {
        Clock now = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
        DynamoSessionStore sessions = new DynamoSessionStore(dynamo, now);
        Session session = sessions.create("ana", "token");
        assertEquals("token", sessions.find(session.getId()).getAccessToken());
        assertNull(sessions.find("unknown"));

        DynamoSessionStore later = new DynamoSessionStore(dynamo, Clock.offset(now, java.time.Duration.ofHours(8).plusSeconds(1)));
        assertNull("expired before the TTL deletes it", later.find(session.getId()));

        String state = sessions.createState();
        assertTrue(sessions.consumeState(state));
        assertFalse(sessions.consumeState(state));
        String old = sessions.createState();
        assertFalse(new DynamoSessionStore(dynamo, Clock.offset(now, java.time.Duration.ofMinutes(11))).consumeState(old));

        sessions.delete(session.getId());
        assertNull(sessions.find(session.getId()));
    }

    @Test
    public void cacheRoundTripsCompressedAndExpires() {
        Clock now = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
        DynamoResultCache cache = new DynamoResultCache(dynamo, now, 600);
        List<HistoryCommit> commits = new ArrayList<>();          // what the GitHub client caches: up to 1000 commits
        for (int i = 0; i < 1000; i++) {
            commits.add(new HistoryCommit("oid-" + i, "message " + i, "https://github.com/o/r/commit/" + i,
                    "2026-10-01T12:00:00Z", i, i % 7, 3, i % 50 == 0 ? 2 : 1, "author " + i, "author" + i + "@example.com",
                    i % 3 == 1 ? null : "login" + i));
        }
        cache.put("key", new RepositoryHistory("2026-01-01T00:00:00Z", null, true, commits));

        RepositoryHistory back = cache.get("key", RepositoryHistory.class);
        assertEquals(1000, back.getCommits().size());
        assertTrue(back.isTruncated());
        HistoryCommit last = back.getCommits().get(999);
        assertEquals("oid-999", last.getOid());
        assertEquals("login999", last.getAuthorLogin());
        assertEquals(999, last.getAdditions());
        assertNull("identity without account", back.getCommits().get(1).getAuthorLogin());
        assertNull(cache.get("other", RepositoryHistory.class));
        assertNull(new DynamoResultCache(dynamo, Clock.offset(now, java.time.Duration.ofSeconds(601)), 600).get("key", RepositoryHistory.class));

        // the contributors of a repository, rebuilt as a typed list
        cache.put("contributors", List.of(new Contributor("octocat", 1, "https://avatars/1", "https://github.com/octocat", 42)));
        List<Contributor> contributors = cache.get("contributors", new TypeToken<List<Contributor>>() { }.getType());
        assertEquals("octocat", contributors.get(0).getLogin());
        assertEquals("https://avatars/1", contributors.get(0).getAvatarUrl());
        assertEquals(42, contributors.get(0).getContributions());

        // a broken table only logs: no exception
        DynamoResultCache broken = new DynamoResultCache(new DynamoClient(dynamo.client(), "x", "missing-table"), now, 600);
        broken.put("key", back);
        assertNull(broken.get("key", RepositoryHistory.class));
    }
}
