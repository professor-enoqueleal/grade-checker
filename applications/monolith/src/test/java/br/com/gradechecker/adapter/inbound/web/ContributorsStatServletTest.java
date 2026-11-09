package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.adapter.outbound.jdbc.JdbcGroupRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcIdentityAliasRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcMemberRepository;
import br.com.gradechecker.core.application.ContributionsQuery;
import br.com.gradechecker.core.application.GroupManagement;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.port.GitHubGateway;

import com.google.gson.JsonElement;
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
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContributorsStatServletTest {

    private GitHubGateway gitHub;
    private ContributorsStatServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;

    @Before
    public void setUp() throws Exception {
        gitHub = mock(GitHubGateway.class);
        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");
        PeriodResolver resolver = new PeriodResolver(Milestones.of(semester),
                Clock.fixed(Instant.parse("2026-09-01T12:00:10Z"), ZoneOffset.UTC)); // M1 in progress, M2/M3 in the future
        servlet = servlet(resolver, List.of());
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        body = new StringWriter();

        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("accessToken")).thenReturn("token");
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        history();   // no commits unless a test says otherwise
    }

    /** The servlet over the use case ContributionsQuery, for the active group 6 (octocat/hello-world). */
    private ContributorsStatServlet servlet(PeriodResolver resolver, List<String> members) {
        JdbcGroupRepository groups = mock(JdbcGroupRepository.class);
        when(groups.findActive(6L)).thenReturn(new Group(6, "g", "https://github.com/octocat/hello-world", "octocat", "hello-world"));
        JdbcMemberRepository memberRepository = mock(JdbcMemberRepository.class);
        when(memberRepository.findLoginsByGroupId(6L)).thenReturn(members);
        GradingConfig grading = GradingConfig.defaults();
        JdbcIdentityAliasRepository aliases = mock(JdbcIdentityAliasRepository.class);
        when(aliases.findByGroupId(6L)).thenReturn(Map.of());
        return new ContributorsStatServlet(new ContributionsQuery(new GroupManagement(groups, memberRepository, gitHub),
                memberRepository, aliases, gitHub, resolver, grading));
    }

    /** GitHub answers every read of the history with these commits (the report is computed by the library). */
    private void history(HistoryCommit... commits) {
        doAnswer(inv -> {
            HistoryRequest r = inv.getArgument(0);
            return new RepositoryHistory(r.getSince(), r.getUntil(), false, List.of(commits));
        }).when(gitHub).getHistory(any());
    }

    private static int sequence;

    /** A commit of a login (null: identity without account) on a day, with its lines and files. */
    private static HistoryCommit commit(String login, String email, String date, int additions, int deletions) {
        String name = login == null ? "Someone Else" : login;
        return new HistoryCommit("c" + (++sequence), "work", null, date + "T15:00:00Z", additions, deletions, 2, 1, name, email, login);
    }

    private HistoryRequest readFromGitHub() {
        ArgumentCaptor<HistoryRequest> captor = ArgumentCaptor.forClass(HistoryRequest.class);
        verify(gitHub, atLeastOnce()).getHistory(captor.capture());
        return captor.getAllValues().get(0);
    }

    private JsonObject json() {
        return JsonParser.parseString(body.toString()).getAsJsonObject();
    }

    @Test
    public void missingGroupIdIsBadRequestWithoutCallingGitHub() throws Exception {
        when(request.getParameter("owner")).thenReturn("octocat");      // the repository comes from the group
        when(request.getParameter("repo")).thenReturn("hello-world");

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertEquals("bad_request", json().get("error").getAsString());
        assertEquals(ContributorsStatServlet.GROUP_ID_REQUIRED, json().get("message").getAsString());
        verifyNoInteractions(gitHub);
    }

    @Test
    public void missingOrArchivedGroupIsNotFoundWithoutCallingGitHub() throws Exception {
        when(request.getParameter("groupId")).thenReturn("99");

        servlet.doGet(request, response);

        verify(response).setStatus(404);
        assertEquals("Group not found.", json().get("message").getAsString());
        verifyNoInteractions(gitHub);
    }

    @Test
    public void theRepositoryIsTheOneOfTheGroup() throws Exception {
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("owner")).thenReturn("someone");       // ignored
        when(request.getParameter("repo")).thenReturn("other-repo");

        servlet.doGet(request, response);

        assertEquals("octocat", readFromGitHub().getOwner());
        assertEquals("hello-world", readFromGitHub().getRepository());
    }

    @Test
    public void successKeepsOldFieldsAndAddsNewOnes() throws Exception {
        when(request.getParameter("groupId")).thenReturn("6");
        history(commit("alice", "alice@x.com", "2026-08-20", 10, 3), commit("alice", "alice@x.com", "2026-08-21", 5, 0),
                commit("bob", "bob@x.com", "2026-08-22", 2, 0));

        servlet.doGet(request, response);

        JsonObject json = json();
        assertEquals("Contributions Dashboard", json.get("message").getAsString());
        assertEquals(3, json.get("totalCommits").getAsInt());
        assertFalse(json.get("truncated").getAsBoolean());
        assertTrue("All has no start", json.get("since").isJsonNull());

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
        when(request.getParameter("groupId")).thenReturn("6");
        servlet.doGet(request, response);

        HistoryRequest read = readFromGitHub();
        assertNull(read.getSince());
        assertEquals("2026-09-01T12:01:00Z", read.getUntil());
        assertEquals("token", read.getAccessToken());

        JsonObject json = json();
        assertEquals("all", json.get("period").getAsString());
        assertTrue(json.get("since").isJsonNull());
        assertEquals("All history", json.get("periodText").getAsString());
    }

    @Test
    public void milestonePeriodIsResolvedOnTheServer() throws Exception {
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("period")).thenReturn("m1");

        servlet.doGet(request, response);

        assertEquals("2026-08-13T03:00:00Z", readFromGitHub().getSince());
        assertEquals("2026-09-18T02:59:59Z", readFromGitHub().getUntil());
        assertEquals("m1", json().get("period").getAsString());
    }

    @Test
    public void futureMilestoneIsBadRequestWithoutCallingGitHub() throws Exception {
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("period")).thenReturn("m3");

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertEquals("invalid_period", json().get("error").getAsString());
        assertEquals("Milestone 3 starts on Oct 30, 2026.", json().get("message").getAsString());
        verifyNoInteractions(gitHub);
    }

    // ---------- grades (openspec change nota-por-marco) ----------

    private ContributorsStatServlet gradingServlet(Instant now, List<String> members) {
        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");
        PeriodResolver resolver = new PeriodResolver(Milestones.of(semester), Clock.fixed(now, ZoneOffset.UTC));
        return servlet(resolver, members);
    }

    private void groupParams(String period) {
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("period")).thenReturn(period);
    }

    private static JsonObject row(JsonObject json, String author) {
        for (JsonElement e : json.getAsJsonObject("stats").getAsJsonArray("consolidatedStats")) {
            if (e.getAsJsonObject().get("author").getAsString().equals(author)) return e.getAsJsonObject();
        }
        throw new AssertionError("no row " + author);
    }

    @Test
    public void milestoneGradesForGroup6() throws Exception {
        groupParams("m2");
        history(commit("matheusmbn070906", "m@x.com", "2026-09-20", 300, 20), commit("matheusmbn070906", "m@x.com", "2026-10-02", 200, 0),
                commit("Pedroasmfodsj", "p@x.com", "2026-09-22", 150, 10), commit("Pedroasmfodsj", "p@x.com", "2026-10-05", 90, 0),
                commit("thiagofp77", "t@x.com", "2026-09-25", 250, 30), commit("thiagofp77", "t@x.com", "2026-10-09", 40, 5),
                commit("LucasMoura-dev777", "l@x.com", "2026-10-11", 120, 0),
                commit(null, "else@home.dev", "2026-10-12", 5, 0), commit(null, "else@home.dev", "2026-10-13", 5, 0));

        gradingServlet(Instant.parse("2026-11-06T12:00:00Z"),
                List.of("matheusmbn070906", "Pedroasmfodsj", "thiagofp77", "LucasMoura-dev777", "absent-student")).doGet(request, response);

        JsonObject json = json();
        JsonObject grading = json.getAsJsonObject("grading");
        assertEquals(0.2, grading.get("fairShare").getAsDouble(), 1e-9);   // 5 students
        assertEquals(5, grading.get("students").getAsInt());
        assertEquals(2, grading.get("excludedCommits").getAsInt());
        assertFalse(grading.get("provisional").getAsBoolean());            // M2 ended on 29/10

        assertEquals(0.00, row(json, "absent-student").get("grade").getAsDouble(), 0);  // student without commits appears
        assertTrue(row(json, "Someone Else").get("grade").isJsonNull());
        assertTrue(row(json, "thiagofp77").getAsJsonObject("gradeDetails").has("indexShare"));
        double g = row(json, "LucasMoura-dev777").get("grade").getAsDouble();
        assertTrue(g > 0 && g <= 3);
    }

    @Test
    public void milestoneInProgressIsProvisional() throws Exception {
        groupParams("m3");
        history(commit("a", "a@x.com", "2026-11-02", 10, 0));

        gradingServlet(Instant.parse("2026-11-06T12:00:00Z"), List.of("a")).doGet(request, response);

        assertTrue(json().getAsJsonObject("grading").get("provisional").getAsBoolean());
    }

    @Test
    public void allPeriodHasMilestoneGradesAndTotal() throws Exception {
        groupParams("all");
        history(commit("a", "a@x.com", "2026-08-20", 50, 0), commit("a", "a@x.com", "2026-08-21", 50, 0),
                commit("b", "b@x.com", "2026-08-20", 50, 0), commit("b", "b@x.com", "2026-08-21", 50, 0));

        // 2026-09-01: M1 in progress, M2 and M3 not started
        gradingServlet(Instant.parse("2026-09-01T12:00:00Z"), List.of("a", "b")).doGet(request, response);

        JsonObject json = json();
        JsonObject a = row(json, "a");
        assertEquals(3.0, a.getAsJsonObject("milestoneGrades").get("m1").getAsDouble(), 0);
        assertTrue(a.getAsJsonObject("milestoneGrades").get("m2").isJsonNull());
        assertEquals(3.0, a.get("total").getAsDouble(), 0);
        assertEquals("m1", json.getAsJsonObject("grading").getAsJsonArray("provisionalMilestones").get(0).getAsString());
        // the table of All, then one read for the grades of every started milestone (m1)
        verify(gitHub, times(2)).getHistory(any());
    }

    @Test
    public void customPeriodHasNoGrades() throws Exception {
        groupParams("custom");
        history(commit("a", "a@x.com", "2026-11-02", 10, 0));

        gradingServlet(Instant.parse("2026-11-06T12:00:00Z"), List.of("a")).doGet(request, response);

        assertFalse(json().has("grading"));
    }

    @Test
    public void gitHubErrorIsTranslated() throws Exception {
        when(request.getParameter("groupId")).thenReturn("6");
        doThrow(new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found")).when(gitHub).getHistory(any());

        servlet.doGet(request, response);

        verify(response).setStatus(404);
        assertEquals("github_not_found", json().get("error").getAsString());
        assertEquals("Repository not found", json().get("message").getAsString());
    }
}
