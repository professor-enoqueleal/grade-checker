package br.com.gradechecker.core.application;

import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class CommitHistoryQueryTest {

    private static PeriodResolver periods() {
        Properties p = new Properties();
        p.setProperty("milestone.1.start", "2026-08-13");
        p.setProperty("milestone.1.end", "2026-09-17");
        return new PeriodResolver(Milestones.of(p), Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    public void anInvalidPeriodShowsAllWithAWarning() {
        CommitHistoryQuery query = new CommitHistoryQuery(null, null, null, periods(), GradingConfig.defaults());

        CommitHistoryQuery.ShownPeriod m1 = query.period("m1", null, null);
        assertEquals("m1", m1.getPeriod().getKey());
        assertNull(m1.getWarning());

        CommitHistoryQuery.ShownPeriod bad = query.period("custom", "2026-10-10T00:00", "2026-09-01T00:00");
        assertEquals(PeriodResolver.ALL, bad.getPeriod().getKey());
        assertTrue(bad.getWarning().endsWith(" Showing all history instead."));
    }

    @Test
    public void theAuthorIsRequiredBeforeAnyRead() {
        CommitHistoryQuery query = new CommitHistoryQuery(null, null, null, periods(), GradingConfig.defaults());   // nothing else is touched
        try {
            query.history(1, null, "all", null, null, "t");
            fail();
        } catch (DomainException e) {
            assertEquals(CommitHistoryQuery.AUTHOR_REQUIRED, e.getMessage());
        }
    }

    @Test
    public void commitsCountedForTheLoginReadThroughThePortWithTheAliasesOfTheGroup() {
        InMemoryPorts.Groups groups = new InMemoryPorts.Groups();
        InMemoryPorts.Aliases aliases = new InMemoryPorts.Aliases();
        InMemoryPorts.GitHub gitHub = new InMemoryPorts.GitHub();
        long id = groups.add("g", "https://github.com/acme/app", false).getId();
        aliases.save(id, "email:ana@home.dev", "ana", "teacher");
        gitHub.history = r -> new RepositoryHistory(r.getSince(), r.getUntil(), false, List.of(
                commit("a1", "ana", "Ana", "ana@x.com", 1),
                commit("b2", null, "Ana Home", "ana@home.dev", 1),     // linked manually
                commit("c3", "bruno", "Bruno", "bruno@x.com", 1),
                commit("m4", "ana", "Ana", "ana@x.com", 2)));          // merge: never listed
        CommitHistoryQuery query = new CommitHistoryQuery(new GroupManagement(groups, new InMemoryPorts.Members(aliases), gitHub),
                aliases, gitHub, periods(), GradingConfig.defaults());

        CommitHistoryQuery.History history = query.history(id, "ANA", "m1", null, null, "t");

        assertEquals(List.of("a1", "b2"), history.getCommits().stream().map(c -> c.getCommit().getOid()).collect(Collectors.toList()));
        assertEquals(List.of("Ana Home <ana@home.dev> (manual)"), history.getAlsoCounted());
        HistoryRequest request = gitHub.requests.get(0);
        assertEquals("acme", request.getOwner());
        assertEquals("app", request.getRepository());
        assertEquals("2026-08-13T03:00:00Z", request.getSince());
    }

    private static HistoryCommit commit(String oid, String login, String name, String email, int parents) {
        return new HistoryCommit(oid, "m", null, "2026-09-01T12:00:00Z", 10, 0, 1, parents, name, email, login);
    }
}
