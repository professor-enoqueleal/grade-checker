package br.com.gradechecker.service;

import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.config.Milestones;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** openspec capabilities milestone-grading ("All" grades) and class-gradebook. */
public class GroupGradesServiceTest {

    private ContributorsService contributors;

    @Before
    public void setUp() {
        contributors = mock(ContributorsService.class);
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

    private static ContributorStatsResponseBody stat(String author, boolean linked, double effectiveLines, int days, int relevant, int commits) {
        ContributorStatsResponseBody s = new ContributorStatsResponseBody(author, commits, (int) effectiveLines, 0, 0, linked, Collections.emptyList());
        s.setGradingIndicators(effectiveLines, days, relevant);
        return s;
    }

    private static ContributionsReport report(ContributorStatsResponseBody... stats) {
        return new ContributionsReport("s", "u", stats.length, false, List.of(stats));
    }

    private GroupGradesService service(String now) {
        return new GroupGradesService(contributors, resolver(now), GradingConfig.defaults());
    }

    @Test
    public void milestonesNotStartedAreNullAndDoNotCallGitHub() {
        when(contributors.getContributionsReport(any())).thenReturn(report(stat("a", true, 100, 3, 2, 2), stat("b", true, 100, 3, 2, 2)));

        // 2026-09-01: M1 in progress, M2 and M3 not started
        GroupGradesService.GroupGrades grades = service("2026-09-01T12:00:00Z").of(6, "owner", "repo", "token", List.of("a", "b"));

        GroupGradesService.StudentGrades a = grades.of("A");
        assertEquals(3.0, a.get("m1"), 0);
        assertNull(a.get("m2"));
        assertNull(a.get("m3"));
        assertEquals(3.0, a.getTotal(), 0);
        assertEquals(List.of("m1"), grades.getProvisionalMilestones());
        verify(contributors, times(1)).getContributionsReport(any());
    }

    @Test
    public void totalIsTheSumOfTheMilestonesAndOnlyRunningMilestoneIsProvisional() {
        // a does everything in every milestone; b nothing
        when(contributors.getContributionsReport(any())).thenReturn(report(stat("a", true, 100, 3, 2, 2)));

        GroupGradesService.GroupGrades grades = service("2026-11-06T12:00:00Z").of(6, "owner", "repo", "token", List.of("a", "b"));

        assertEquals(9.0, grades.of("a").getTotal(), 0);
        assertEquals(0.0, grades.of("b").getTotal(), 0);
        assertEquals(0.0, grades.of("b").get("m2"), 0);
        assertEquals(List.of("m3"), grades.getProvisionalMilestones());
        assertEquals(List.of("a", "b"), grades.getStudents().stream().map(GroupGradesService.StudentGrades::getLogin).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void excludedCommitsAreSummedAndNonStudentsHaveNoGrade() {
        when(contributors.getContributionsReport(any())).thenReturn(report(
                stat("a", true, 100, 3, 2, 2), stat("NO ACCOUNT", false, 10, 1, 0, 3), stat("dependabot[bot]", true, 5, 1, 0, 1)));

        GroupGradesService.GroupGrades grades = service("2026-11-06T12:00:00Z").of(6, "owner", "repo", "token", List.of("a", "dependabot[bot]"));

        assertEquals("4 commits left out in each of the 3 milestones", 12, grades.getExcludedCommits());
        assertNull(grades.of("dependabot[bot]"));
        assertNull(grades.of("NO ACCOUNT"));
        assertEquals(1, grades.getStudents().size());
    }

    @Test
    public void eachMilestoneRequestCarriesTheGroupAndItsPeriod() {
        when(contributors.getContributionsReport(any())).thenReturn(report());

        service("2026-11-06T12:00:00Z").of(6, "owner", "repo", "token", List.of("a"));

        ArgumentCaptor<CommitRequestBody> captor = ArgumentCaptor.forClass(CommitRequestBody.class);
        verify(contributors, times(3)).getContributionsReport(captor.capture());
        for (CommitRequestBody r : captor.getAllValues()) {
            assertEquals("6", r.getGroupId());
            assertEquals("owner", r.getOwner());
            assertEquals("repo", r.getRepository());
            assertEquals("token", r.getAccessToken());
        }
        assertEquals("2026-08-13T03:00:00Z", captor.getAllValues().get(0).getSince());
    }

    @Test
    public void manualAssociationsOfTheGroupEnterTheGrades() {
        GitHubProxy proxy = mock(GitHubProxy.class);
        Map<String, String> aliases = Map.of("name:joao", "a");
        ContributorsService real = new ContributorsService(proxy, groupId -> groupId == 6L ? aliases : Map.of());
        when(proxy.getContributionsReport(any(), eq(aliases))).thenReturn(report(stat("a", true, 100, 3, 2, 2)));

        GroupGradesService.GroupGrades grades = new GroupGradesService(real, resolver("2026-11-06T12:00:00Z"), GradingConfig.defaults())
                .of(6, "owner", "repo", "token", List.of("a"));

        verify(proxy, times(3)).getContributionsReport(any(), eq(aliases));
        assertEquals(9.0, grades.of("a").getTotal(), 0);
    }

    @Test
    public void groupWithoutStudentsHasNoGrades() {
        when(contributors.getContributionsReport(any())).thenReturn(report(stat("x", true, 10, 1, 0, 1)));

        GroupGradesService.GroupGrades grades = service("2026-11-06T12:00:00Z").of(6, "owner", "repo", "token", List.of());

        assertTrue(grades.getStudents().isEmpty());
    }

    @Test(expected = GitHubApiException.class)
    public void gitHubErrorPropagates() {
        when(contributors.getContributionsReport(any())).thenThrow(new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found"));

        service("2026-11-06T12:00:00Z").of(6, "owner", "repo", "token", List.of("a"));
    }

}
