package br.com.gradechecker.core.application;

import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Capabilities milestone-grading ("All" grades), class-gradebook and gradebook-api: the history is read ONCE and
 * sliced per milestone, with exactly the same result as one read per milestone (used when the history is truncated).
 */
public class MilestoneGradesTest {

    private final Group group = new Group(6, "g", "https://github.com/owner/repo", "owner", "repo");
    private final List<HistoryCommit> commits = new ArrayList<>();
    private InMemoryPorts.GitHub gitHub;
    private InMemoryPorts.Aliases aliases;
    private boolean truncated;

    @Before
    public void setUp() {
        gitHub = new InMemoryPorts.GitHub();
        gitHub.history = this::history;
        aliases = new InMemoryPorts.Aliases();
        aliases.save(6, "email:carla@home.dev", "carla", "teacher");
        // milestone 1
        commit("2026-08-14T12:00:00Z", "ana", "ana@x.com", 120, 10);
        commit("2026-08-20T12:00:00Z", "bruno", "bruno@x.com", 40, 5);
        commit("2026-09-17T23:00:00Z", "ana", "ana@x.com", 30, 0);       // last evening of M1 (UTC-3)
        // milestone 2
        commit("2026-09-18T04:00:00Z", "bruno", "bruno@x.com", 300, 20);  // first hour of M2 (UTC-3)
        commit("2026-10-01T12:00:00Z", null, "carla@home.dev", 80, 8);       // without account, linked manually
        commit("2026-10-10T12:00:00Z", "carla", "carla@x.com", 50, 5);
        commit("2026-10-12T12:00:00Z", null, "nobody@home.dev", 60, 0);      // without account, not linked
        commit("2026-10-13T12:00:00Z", "dependabot[bot]", "bot@x.com", 5, 5);
        // milestone 3 (in progress on 2026-11-06)
        commit("2026-11-02T12:00:00Z", "ana", "ana@x.com", 200, 30);
        commit("2026-11-05T12:00:00Z", "carla", "carla@x.com", 90, 9);
        // before the semester: never counted
        commit("2026-07-01T12:00:00Z", "bruno", "bruno@x.com", 999, 0);
    }

    /** GitHub simulated: the commits of the list inside [since, until]. */
    private RepositoryHistory history(HistoryRequest request) {
        Instant since = request.getSince() == null ? null : Instant.parse(request.getSince());
        Instant until = request.getUntil() == null ? null : Instant.parse(request.getUntil());
        List<HistoryCommit> inside = new ArrayList<>();
        for (HistoryCommit c : commits) {
            Instant date = Instant.parse(c.getCommittedDate());
            if ((since == null || !date.isBefore(since)) && (until == null || !date.isAfter(until))) inside.add(c);
        }
        return new RepositoryHistory(request.getSince(), request.getUntil(), truncated, inside);
    }

    private void commit(String date, String login, String email, int additions, int deletions) {
        commits.add(new HistoryCommit(Integer.toHexString(commits.size() + 1) + "abc", "work", "https://github.com/o/r/commit/x",
                date, additions, deletions, 2, 1, login == null ? "Someone" : login, email, login));
    }

    private static PeriodResolver resolver(String now) {
        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");
        return new PeriodResolver(Milestones.of(semester), Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private MilestoneGrades.GroupGrades grades(String now, List<String> students) {
        return new MilestoneGrades(gitHub, aliases, resolver(now), GradingConfig.defaults()).grades(group, students, "token");
    }

    private MilestoneGrades.GroupGrades grades() {
        return grades("2026-11-06T12:00:00Z", List.of("ana", "bruno", "carla"));
    }

    @Test
    public void oneReadOfTheHistoryGivesTheSameGradesAsOneReadPerMilestone() {
        MilestoneGrades.GroupGrades single = grades();
        List<HistoryRequest> singleReads = new ArrayList<>(gitHub.requests);
        gitHub.requests.clear();
        truncated = true;                         // over the commit limit: one read per started milestone

        MilestoneGrades.GroupGrades perMilestone = grades();

        assertEquals(1, singleReads.size());
        assertEquals("2026-08-13T03:00:00Z", singleReads.get(0).getSince());
        assertEquals("the combined read, then one per started milestone", 4, gitHub.requests.size());
        assertEquals(perMilestone.getProvisionalMilestones(), single.getProvisionalMilestones());
        assertEquals(perMilestone.getExcludedCommits(), single.getExcludedCommits());
        assertEquals(perMilestone.getStudents().size(), single.getStudents().size());
        for (MilestoneGrades.StudentGrades expected : perMilestone.getStudents()) {
            MilestoneGrades.StudentGrades actual = single.student(expected.getLogin());
            assertEquals(expected.getLogin(), expected.getMilestones(), actual.getMilestones());
            assertEquals(expected.getLogin(), expected.getTotal(), actual.getTotal(), 0);
        }
    }

    @Test
    public void theRequestCarriesTheRepositoryOfTheGroupAndTheToken() {
        grades();

        HistoryRequest request = gitHub.requests.get(0);
        assertEquals("owner", request.getOwner());
        assertEquals("repo", request.getRepository());
        assertEquals("token", request.getAccessToken());
        assertEquals("end of M3 (UTC-3)", "2026-11-20T02:59:59Z", request.getUntil());
    }

    @Test
    public void milestonesNotStartedAreNull() {
        // 2026-09-01: M1 in progress, M2 and M3 not started
        MilestoneGrades.GroupGrades grades = grades("2026-09-01T12:00:00Z", List.of("ana", "bruno"));

        MilestoneGrades.StudentGrades ana = grades.student("ANA");
        assertNotNull(ana.get("m1"));
        assertNull(ana.get("m2"));
        assertNull(ana.get("m3"));
        assertEquals(ana.get("m1"), ana.getTotal(), 0);
        assertEquals(List.of("m1"), grades.getProvisionalMilestones());
        assertEquals(1, gitHub.requests.size());
    }

    @Test
    public void totalIsTheSumOfTheMilestonesInRosterOrder() {
        MilestoneGrades.GroupGrades grades = grades();

        for (MilestoneGrades.StudentGrades s : grades.getStudents()) {
            double sum = 0;
            for (Double g : s.getMilestones().values()) sum += g == null ? 0 : g;
            assertEquals(s.getLogin(), sum, s.getTotal(), 0.011);
        }
        assertEquals(List.of("m3"), grades.getProvisionalMilestones());
        assertEquals(List.of("ana", "bruno", "carla"),
                grades.getStudents().stream().map(MilestoneGrades.StudentGrades::getLogin).collect(Collectors.toList()));
    }

    @Test
    public void botsAndAuthorsWithoutAccountHaveNoGradeAndTheirCommitsAreLeftOut() {
        MilestoneGrades.GroupGrades grades = grades("2026-11-06T12:00:00Z", List.of("ana", "bruno", "carla", "dependabot[bot]"));

        assertNull(grades.student("dependabot[bot]"));
        assertNull(grades.student("nobody@home.dev"));
        assertEquals(3, grades.getStudents().size());
        assertEquals("the bot and the identity not linked", 2, grades.getExcludedCommits());
    }

    @Test
    public void manualAssociationsOfTheGroupEnterTheGrades() {
        int linked = grades().getExcludedCommits();
        aliases.delete(6, "email:carla@home.dev");

        int unlinked = grades().getExcludedCommits();

        assertEquals("the commit of carla@home.dev counts for carla only while linked", linked + 1, unlinked);
    }

    @Test
    public void groupWithoutStudentsHasNoGrades() {
        assertTrue(grades("2026-11-06T12:00:00Z", List.of()).getStudents().isEmpty());
    }

    @Test(expected = GitHubApiException.class)
    public void gitHubErrorPropagates() {
        gitHub.history = r -> { throw new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found"); };

        grades();
    }
}
