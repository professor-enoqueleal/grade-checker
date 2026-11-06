package br.com.gradechecker.servlet;

import br.com.gradechecker.config.Milestones;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.service.ContributorsService;
import br.com.gradechecker.service.PeriodResolver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContributorsStatServletTest {

    private ContributorsService service;
    private ContributorsStatServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;

    @Before
    public void setUp() throws Exception {
        service = mock(ContributorsService.class);
        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");
        PeriodResolver resolver = new PeriodResolver(Milestones.of(semester),
                Clock.fixed(Instant.parse("2026-09-01T12:00:10Z"), ZoneOffset.UTC)); // M1 in progress, M2/M3 in the future
        servlet = new ContributorsStatServlet(service, resolver);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        body = new StringWriter();

        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("accessToken")).thenReturn("token");
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
    }

    private JsonObject json() {
        return JsonParser.parseString(body.toString()).getAsJsonObject();
    }

    @Test
    public void missingOwnerOrRepoIsBadRequestWithoutCallingGitHub() throws Exception {
        when(request.getParameter("repo")).thenReturn("hello-world");

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertEquals("bad_request", json().get("error").getAsString());
        verifyNoInteractions(service);
    }

    @Test
    public void successKeepsOldFieldsAndAddsNewOnes() throws Exception {
        when(request.getParameter("owner")).thenReturn("octocat");
        when(request.getParameter("repo")).thenReturn("hello-world");
        when(service.getContributionsReport(any())).thenReturn(new ContributionsReport(
                "2024-01-01T00:01:00Z", "2026-11-05T18:01:00Z", 3, false,
                List.of(new ContributorStatsResponseBody("alice", 2, 15, 3, 90.0))));

        servlet.doGet(request, response);

        JsonObject json = json();
        assertEquals("Contributions Dashboard", json.get("message").getAsString());
        assertEquals(3, json.get("totalCommits").getAsInt());
        assertFalse(json.get("truncated").getAsBoolean());
        assertEquals("2024-01-01T00:01:00Z", json.get("since").getAsString());

        JsonObject alice = json.getAsJsonObject("stats").getAsJsonArray("consolidatedStats").get(0).getAsJsonObject();
        assertEquals("alice", alice.get("author").getAsString());
        assertEquals(2, alice.get("commits").getAsInt());
        assertEquals(15, alice.get("totalAdditions").getAsInt());
        assertEquals(3, alice.get("totalDeletions").getAsInt());
        assertEquals(90.0, alice.get("percentage").getAsDouble(), 0.0);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void defaultPeriodIsAllWithoutSince() throws Exception {
        when(request.getParameter("owner")).thenReturn("octocat");
        when(request.getParameter("repo")).thenReturn("hello-world");
        when(service.getContributionsReport(any())).thenAnswer(inv -> {
            CommitRequestBody b = inv.getArgument(0);
            return new ContributionsReport(b.getSince(), b.getUntil(), 0, false, List.of());
        });

        servlet.doGet(request, response);

        ArgumentCaptor<CommitRequestBody> captor = ArgumentCaptor.forClass(CommitRequestBody.class);
        verify(service).getContributionsReport(captor.capture());
        assertNull(captor.getValue().getSince());
        assertEquals("2026-09-01T12:01:00Z", captor.getValue().getUntil());
        assertEquals("token", captor.getValue().getAccessToken());

        JsonObject json = json();
        assertEquals("all", json.get("period").getAsString());
        assertTrue(json.get("since").isJsonNull());
        assertEquals("All history", json.get("periodText").getAsString());
    }

    @Test
    public void milestonePeriodIsResolvedOnTheServer() throws Exception {
        when(request.getParameter("owner")).thenReturn("octocat");
        when(request.getParameter("repo")).thenReturn("hello-world");
        when(request.getParameter("period")).thenReturn("m1");
        when(service.getContributionsReport(any())).thenReturn(new ContributionsReport("s", "u", 0, false, List.of()));

        servlet.doGet(request, response);

        ArgumentCaptor<CommitRequestBody> captor = ArgumentCaptor.forClass(CommitRequestBody.class);
        verify(service).getContributionsReport(captor.capture());
        assertEquals("2026-08-13T03:00:00Z", captor.getValue().getSince());
        assertEquals("2026-09-18T02:59:59Z", captor.getValue().getUntil());
        assertEquals("m1", json().get("period").getAsString());
    }

    @Test
    public void futureMilestoneIsBadRequestWithoutCallingGitHub() throws Exception {
        when(request.getParameter("owner")).thenReturn("octocat");
        when(request.getParameter("repo")).thenReturn("hello-world");
        when(request.getParameter("period")).thenReturn("m3");

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertEquals("invalid_period", json().get("error").getAsString());
        assertEquals("Milestone 3 starts on Oct 30, 2026.", json().get("message").getAsString());
        verifyNoInteractions(service);
    }

    // ---------- grades (openspec change nota-por-marco) ----------

    private static ContributorStatsResponseBody stat(String author, boolean linked, double effectiveLines, int days, int relevant, int commits, int lines) {
        ContributorStatsResponseBody s = new ContributorStatsResponseBody(author, commits, lines, 0, 0, linked, java.util.Collections.emptyList());
        s.setGradingIndicators(effectiveLines, days, relevant);
        return s;
    }

    private ContributorsStatServlet gradingServlet(Instant now, List<String> members) {
        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");
        PeriodResolver resolver = new PeriodResolver(Milestones.of(semester), Clock.fixed(now, ZoneOffset.UTC));
        return new ContributorsStatServlet(service, resolver, groupId -> members, br.com.gradechecker.config.GradingConfig.defaults());
    }

    private void groupParams(String period) {
        when(request.getParameter("owner")).thenReturn("LucasMoura-dev777");
        when(request.getParameter("repo")).thenReturn("cannamed");
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("period")).thenReturn(period);
    }

    private static com.google.gson.JsonObject row(JsonObject json, String author) {
        for (com.google.gson.JsonElement e : json.getAsJsonObject("stats").getAsJsonArray("consolidatedStats")) {
            if (e.getAsJsonObject().get("author").getAsString().equals(author)) return e.getAsJsonObject();
        }
        throw new AssertionError("no row " + author);
    }

    @Test
    public void milestoneGradesForGroup6() throws Exception {
        groupParams("m2");
        when(service.getContributionsReport(any())).thenReturn(new ContributionsReport("s", "u", 29, false, List.of(
                stat("matheusmbn070906", true, 1546, 7, 6, 9, 2524),
                stat("Pedroasmfodsj", true, 894, 8, 6, 8, 1057),
                stat("thiagofp77", true, 1294, 4, 6, 7, 1580),
                stat("LucasMoura-dev777", true, 878, 5, 4, 5, 878),
                stat("SOMEONE ELSE", false, 10, 1, 0, 2, 10))));

        gradingServlet(Instant.parse("2026-11-06T12:00:00Z"),
                List.of("matheusmbn070906", "Pedroasmfodsj", "thiagofp77", "LucasMoura-dev777", "absent-student")).doGet(request, response);

        JsonObject json = json();
        JsonObject grading = json.getAsJsonObject("grading");
        assertEquals(0.2, grading.get("fairShare").getAsDouble(), 1e-9);   // 5 students
        assertEquals(5, grading.get("students").getAsInt());
        assertEquals(2, grading.get("excludedCommits").getAsInt());
        assertFalse(grading.get("provisional").getAsBoolean());            // M2 ended on 29/10

        assertEquals(0.00, row(json, "absent-student").get("grade").getAsDouble(), 0);  // student without commits appears
        assertTrue(row(json, "SOMEONE ELSE").get("grade").isJsonNull());
        assertTrue(row(json, "thiagofp77").getAsJsonObject("gradeDetails").has("indexShare"));
        double g = row(json, "LucasMoura-dev777").get("grade").getAsDouble();
        assertTrue(g > 0 && g <= 3);
    }

    @Test
    public void milestoneInProgressIsProvisional() throws Exception {
        groupParams("m3");
        when(service.getContributionsReport(any())).thenReturn(new ContributionsReport("s", "u", 1, false, List.of(stat("a", true, 10, 1, 1, 1, 10))));

        gradingServlet(Instant.parse("2026-11-06T12:00:00Z"), List.of("a")).doGet(request, response);

        assertTrue(json().getAsJsonObject("grading").get("provisional").getAsBoolean());
    }

    @Test
    public void allPeriodHasMilestoneGradesAndTotal() throws Exception {
        groupParams("all");
        when(service.getContributionsReport(any())).thenReturn(new ContributionsReport("s", "u", 2, false, List.of(
                stat("a", true, 100, 3, 2, 2, 100), stat("b", true, 100, 3, 2, 2, 100))));

        // 2026-09-01: M1 in progress, M2 and M3 not started
        gradingServlet(Instant.parse("2026-09-01T12:00:00Z"), List.of("a", "b")).doGet(request, response);

        JsonObject json = json();
        JsonObject a = row(json, "a");
        assertEquals(3.0, a.getAsJsonObject("milestoneGrades").get("m1").getAsDouble(), 0);
        assertTrue(a.getAsJsonObject("milestoneGrades").get("m2").isJsonNull());
        assertEquals(3.0, a.get("total").getAsDouble(), 0);
        assertEquals("m1", json.getAsJsonObject("grading").getAsJsonArray("provisionalMilestones").get(0).getAsString());
        verify(service, times(2)).getContributionsReport(any()); // all + m1 (m2/m3 not started)
    }

    @Test
    public void customPeriodAndMissingGroupHaveNoGrades() throws Exception {
        groupParams("custom");
        when(service.getContributionsReport(any())).thenReturn(new ContributionsReport("s", "u", 1, false, List.of(stat("a", true, 10, 1, 1, 1, 10))));

        gradingServlet(Instant.parse("2026-11-06T12:00:00Z"), List.of("a")).doGet(request, response);

        assertFalse(json().has("grading"));
    }

    @Test
    public void gitHubErrorIsTranslated() throws Exception {
        when(request.getParameter("owner")).thenReturn("octocat");
        when(request.getParameter("repo")).thenReturn("hello-world");
        when(service.getContributionsReport(any())).thenThrow(
                new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found"));

        servlet.doGet(request, response);

        verify(response).setStatus(404);
        assertEquals("github_not_found", json().get("error").getAsString());
        assertEquals("Repository not found", json().get("message").getAsString());
    }
}
