package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.adapter.outbound.jdbc.JdbcGroupRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcIdentityAliasRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcMemberRepository;
import br.com.gradechecker.core.application.CommitHistoryQuery;
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
import br.com.gradechecker.core.view.CommitRow;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** openspec capability commit-history-page. */
public class ContributionsDetailServletTest {

    private GitHubGateway gitHub;
    private ContributionsDetailServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private PeriodResolver resolver;
    private JdbcIdentityAliasRepository aliases;

    @Before
    public void setUp() {
        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");

        gitHub = mock(GitHubGateway.class);
        resolver = new PeriodResolver(Milestones.of(semester), Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));
        JdbcGroupRepository groups = mock(JdbcGroupRepository.class);
        when(groups.findActive(1L)).thenReturn(new Group(1, "g", "https://github.com/octocat/hello-world", "octocat", "hello-world"));
        aliases = mock(JdbcIdentityAliasRepository.class);
        when(aliases.findByGroupId(1L)).thenReturn(Map.of("name:joao", "alice"));
        servlet = new ContributionsDetailServlet(new CommitHistoryQuery(
                new GroupManagement(groups, mock(JdbcMemberRepository.class), gitHub), aliases, gitHub, resolver,
                GradingConfig.defaults()), resolver);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("accessToken")).thenReturn("token");
        when(request.getRequestDispatcher("commits-detail.jsp")).thenReturn(mock(RequestDispatcher.class));
        when(request.getParameter("author")).thenReturn("alice");
        when(request.getParameter("owner")).thenReturn("octocat");
        when(request.getParameter("repo")).thenReturn("hello-world");
        when(request.getParameter("groupId")).thenReturn("1");
    }

    /** A commit of an account (login) or, with login null, of a git identity without account. */
    private static HistoryCommit commit(String oid, String message, String url, String login, String name, String email) {
        return new HistoryCommit(oid, message, url, "2026-09-01T14:05:00Z", 1, 0, 1, 1, name, email, login);
    }

    private static HistoryCommit byAccount(String oid, String login) {
        return commit(oid, "m", null, login, login, login + "@x.com");
    }

    /** GitHub answers with these commits; the library attributes them (aliases of group 1: name:joao -> alice). */
    private void history(boolean truncated, HistoryCommit... commits) {
        when(gitHub.getHistory(any())).thenAnswer(call -> {
            HistoryRequest r = call.getArgument(0);
            return new RepositoryHistory(r.getSince(), r.getUntil(), truncated, List.of(commits));
        });
    }

    private HistoryRequest sentToGitHub() {
        ArgumentCaptor<HistoryRequest> captor = ArgumentCaptor.forClass(HistoryRequest.class);
        verify(gitHub).getHistory(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private <T> T attribute(String name) {
        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq(name), value.capture());
        return (T) value.getValue();
    }

    @Test
    public void defaultIsAllAndRowsAreSafe() throws Exception {
        history(false,
                commit("a1", "<script>alert(1)</script>", "https://github.com/octocat/hello-world/commit/a1", "alice", "Alice", "a@x.com"),
                commit("b2", "fix", "javascript:alert(1)", "alice", "Alice", "a@x.com"));

        servlet.doGet(request, response);

        assertNull(sentToGitHub().getSince());
        verify(request).setAttribute("periodKey", "all");
        verify(request).setAttribute("periodText", "All history");
        verify(request).setAttribute("totalCommits", 2);

        List<CommitRow> list = attribute("commits");
        assertEquals("committedDate in the milestones zone", "Sep 1, 2026 11:05", list.get(0).getDate());
        assertEquals("<script>alert(1)</script>", list.get(0).getMessage()); // escaped by <c:out> in the JSP
        assertEquals("a1", list.get(0).getSha());
        assertEquals("https://github.com/octocat/hello-world/commit/a1", list.get(0).getUrl());
        assertNull(list.get(0).getIdentity());
        assertNull(list.get(1).getUrl()); // non-GitHub URL is not used as a link
        assertTrue("no Also counted line", this.<List<String>>attribute("alsoCounted").isEmpty());
    }

    @Test
    public void onlyCommitsCountedForTheStudentIgnoringCase() throws Exception {
        when(request.getParameter("author")).thenReturn("ALICE");
        history(false,
                commit("a1", "mine", null, "alice", "Alice", "a@x.com"),
                commit("b2", "bob's", null, "bob", "Bob", "b@x.com"),
                commit("c3", "no account", null, null, "Carol", "carol@y.z"));

        servlet.doGet(request, response);

        List<CommitRow> list = attribute("commits");
        assertEquals(1, list.size());
        assertEquals("a1", list.get(0).getSha());
    }

    @Test
    public void frequencyChartUsesTheSameCommitsAsTheTable() throws Exception {
        history(false,
                byAccount("a1", "alice"),
                byAccount("b1", "bob"),                                         // not counted for alice
                commit("a2", "m", null, null, "alice", "alice.gmail.com"));    // same name as the login

        servlet.doGet(request, response);

        br.com.gradechecker.core.history.CommitActivity activity = attribute("commitActivity");
        assertEquals(2, activity.getTotal());                  // = Total of commits
        assertEquals(1, activity.getActiveDays());
        assertEquals("day", activity.getGranularity());          // All: from the first commit (01/09) to today (01/09)
        String json = attribute("commitActivityJson");
        assertTrue(json.contains("\"total\":2"));
    }

    @Test
    public void noCommitsNoFrequencyChart() throws Exception {
        history(false);
        servlet.doGet(request, response);
        assertNull(attribute("commitActivity"));
        assertNull(attribute("commitActivityJson"));
    }

    @Test
    public void identitiesOfUnlinkedCommitsAreShownAndListedOnceInOrder() throws Exception {
        history(false,
                commit("a1", "m", null, null, "alice", "alice.gmail.com"),     // same name as the login
                commit("b2", "m", null, null, "Joao", "="),                    // linked manually (name:joao)
                commit("c3", "m", null, null, "alice", "alice.gmail.com"),
                byAccount("d4", "alice"));

        servlet.doGet(request, response);

        List<CommitRow> list = attribute("commits");
        assertEquals("alice <alice.gmail.com>", list.get(0).getIdentity());
        assertFalse(list.get(0).isManual());
        assertEquals("Joao <=>", list.get(1).getIdentity());
        assertTrue(list.get(1).isManual());
        assertNull(list.get(3).getIdentity());
        assertEquals(List.of("alice <alice.gmail.com>", "Joao <=> (manual)"), attribute("alsoCounted"));
    }

    @Test
    public void theRepositoryAndTheAliasesAreThoseOfTheGroup() throws Exception {
        when(request.getParameter("owner")).thenReturn("someone");       // ignored: the group has the repository
        history(false);
        servlet.doGet(request, response);
        verify(gitHub).getHistory(argThat(b -> "octocat".equals(b.getOwner()) && "hello-world".equals(b.getRepository())));
        verify(aliases).findByGroupId(1L);
    }

    @Test
    public void withoutGroupIdOrWithAMissingGroupNothingIsRead() throws Exception {
        when(request.getParameter("groupId")).thenReturn(null);
        servlet.doGet(request, response);
        verify(response).sendError(400, ContributionsDetailServlet.GROUP_ID_REQUIRED);

        when(request.getParameter("groupId")).thenReturn("99");
        servlet.doGet(request, response);
        verify(response).sendError(404, "Group not found.");
        verifyNoInteractions(gitHub);
    }

    @Test
    public void withoutAuthorIsBadRequest() throws Exception {
        when(request.getParameter("author")).thenReturn(null);
        servlet.doGet(request, response);
        verify(response).sendError(400, "The 'author' parameter is required.");
        verifyNoInteractions(gitHub);
    }

    @Test
    public void milestoneFillsCustomInputsInLocalTime() throws Exception {
        when(request.getParameter("period")).thenReturn("m1");
        history(false);

        servlet.doGet(request, response);

        assertEquals("2026-08-13T03:00:00Z", sentToGitHub().getSince());
        verify(request).setAttribute("customSince", "2026-08-13T00:00");
        verify(request).setAttribute("customUntil", "2026-09-17T23:59");
        verify(request).setAttribute("periodText", "Aug 13, 2026 – Sep 17, 2026");
    }

    @Test
    public void futureMilestoneFallsBackToAllWithWarning() throws Exception {
        when(request.getParameter("period")).thenReturn("m2");
        history(false);

        servlet.doGet(request, response);

        assertNull(sentToGitHub().getSince());
        verify(request).setAttribute("periodKey", "all");
        verify(request).setAttribute("periodWarning", "Milestone 2 starts on Sep 18, 2026. Showing all history instead.");
    }

    @Test
    public void gitHubErrorIsShownInsteadOfEmptyList() throws Exception {
        when(gitHub.getHistory(any())).thenThrow(new GitHubApiException(429, GitHubApiException.RATE_LIMITED, "GitHub API rate limit reached."));

        servlet.doGet(request, response);

        verify(request).setAttribute("githubError", "GitHub API rate limit reached.");
        verify(request).setAttribute("totalCommits", 0);
    }

    @Test
    public void truncationIsExposed() throws Exception {
        history(true);

        servlet.doGet(request, response);

        verify(request).setAttribute("truncated", true);
    }
}
