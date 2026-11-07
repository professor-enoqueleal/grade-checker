package br.com.gradechecker.servlet;

import br.com.gradechecker.config.Milestones;
import br.com.gradechecker.model.*;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.service.PeriodResolver;
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

    private GitHubProxy proxy;
    private ContributionsDetailServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private PeriodResolver resolver;

    @Before
    public void setUp() {
        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");

        proxy = mock(GitHubProxy.class);
        resolver = new PeriodResolver(Milestones.of(semester), Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));
        servlet = new ContributionsDetailServlet(proxy, resolver, groupId -> groupId == 1L ? Map.of("name:joao", "alice") : Map.of());

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

    private static AttributedCommit commit(String oid, String message, String url, String author, boolean linked,
                                           AttributionSource source, String identity) {
        HistoryCommit c = new HistoryCommit(oid, message, url, "2026-09-01T14:05:00Z", 1, 0, 1, 1, "n", "e", null);
        return new AttributedCommit(c, author, linked, source, identity);
    }

    private void history(boolean truncated, AttributedCommit... commits) {
        when(proxy.getCommitHistory(any(), any())).thenReturn(new AttributedHistory(truncated, List.of(commits)));
    }

    private CommitRequestBody sentToGitHub() {
        ArgumentCaptor<CommitRequestBody> captor = ArgumentCaptor.forClass(CommitRequestBody.class);
        verify(proxy).getCommitHistory(captor.capture(), any());
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
                commit("a1", "<script>alert(1)</script>", "https://github.com/octocat/hello-world/commit/a1", "alice", true, AttributionSource.ACCOUNT, null),
                commit("b2", "fix", "javascript:alert(1)", "alice", true, AttributionSource.ACCOUNT, null));

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
                commit("a1", "mine", null, "alice", true, AttributionSource.ACCOUNT, null),
                commit("b2", "bob's", null, "bob", true, AttributionSource.ACCOUNT, null),
                commit("c3", "no account", null, "alice", false, AttributionSource.NO_ACCOUNT, "alice <x@y.z>"));

        servlet.doGet(request, response);

        List<CommitRow> list = attribute("commits");
        assertEquals(1, list.size());
        assertEquals("a1", list.get(0).getSha());
    }

    @Test
    public void identitiesOfUnlinkedCommitsAreShownAndListedOnceInOrder() throws Exception {
        history(false,
                commit("a1", "m", null, "alice", true, AttributionSource.SAME_NAME, "alice <alice.gmail.com>"),
                commit("b2", "m", null, "alice", true, AttributionSource.MANUAL, "Joao <=>"),
                commit("c3", "m", null, "alice", true, AttributionSource.SAME_NAME, "alice <alice.gmail.com>"),
                commit("d4", "m", null, "alice", true, AttributionSource.ACCOUNT, null));

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
    public void aliasesOfTheGroupArePassedAndNoneWithoutGroupId() throws Exception {
        history(false);
        servlet.doGet(request, response);
        verify(proxy).getCommitHistory(any(), eq(Map.of("name:joao", "alice")));

        reset(proxy);
        history(false);
        when(request.getParameter("groupId")).thenReturn(null);
        servlet.doGet(request, response);
        verify(proxy).getCommitHistory(any(), eq(Map.of()));
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
        when(proxy.getCommitHistory(any(), any())).thenThrow(new GitHubApiException(429, GitHubApiException.RATE_LIMITED, "GitHub API rate limit reached."));

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
