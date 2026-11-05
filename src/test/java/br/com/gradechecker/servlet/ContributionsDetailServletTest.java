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
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContributionsDetailServletTest {

    private GitHubProxy proxy;
    private ContributionsDetailServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;

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
        servlet = new ContributionsDetailServlet(proxy, new PeriodResolver(Milestones.of(semester),
                Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC)));

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

    private static Commit commit(String sha, String message, String url) {
        Committer committer = new Committer();
        committer.setDate("2026-09-01T14:05:00Z");
        CommitDetail detail = new CommitDetail();
        detail.setMessage(message);
        detail.setCommitter(committer);
        Commit commit = new Commit();
        commit.setSha(sha);
        commit.setCommit(detail);
        commit.setHtmlUrl(url);
        return commit;
    }

    private CommitRequestBody sentToGitHub() {
        ArgumentCaptor<CommitRequestBody> captor = ArgumentCaptor.forClass(CommitRequestBody.class);
        verify(proxy).getCommits(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void defaultIsAllAndRowsAreSafe() throws Exception {
        when(proxy.getCommits(any())).thenReturn(new CommitsPage(List.of(
                commit("a1", "<script>alert(1)</script>", "https://github.com/octocat/hello-world/commit/a1"),
                commit("b2", "fix", "javascript:alert(1)")), false));

        servlet.doGet(request, response);

        CommitRequestBody body = sentToGitHub();
        assertNull(body.getSince());
        assertEquals("alice", body.getCommitAuthor());

        verify(request).setAttribute("periodKey", "all");
        verify(request).setAttribute("periodText", "All history");
        verify(request).setAttribute("totalCommits", 2);

        ArgumentCaptor<Object> rows = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq("commits"), rows.capture());
        List<CommitRow> list = (List<CommitRow>) rows.getValue();
        assertEquals("Sep 1, 2026 11:05", list.get(0).getDate());
        assertEquals("<script>alert(1)</script>", list.get(0).getMessage()); // escaped by <c:out> in the JSP
        assertEquals("https://github.com/octocat/hello-world/commit/a1", list.get(0).getUrl());
        assertNull(list.get(1).getUrl()); // non-GitHub URL is not used as a link
    }

    @Test
    public void milestoneFillsCustomInputsInLocalTime() throws Exception {
        when(request.getParameter("period")).thenReturn("m1");
        when(proxy.getCommits(any())).thenReturn(new CommitsPage(List.of(), false));

        servlet.doGet(request, response);

        assertEquals("2026-08-13T03:00:00Z", sentToGitHub().getSince());
        verify(request).setAttribute("customSince", "2026-08-13T00:00");
        verify(request).setAttribute("customUntil", "2026-09-17T23:59");
        verify(request).setAttribute("periodText", "Aug 13, 2026 – Sep 17, 2026");
    }

    @Test
    public void futureMilestoneFallsBackToAllWithWarning() throws Exception {
        when(request.getParameter("period")).thenReturn("m2");
        when(proxy.getCommits(any())).thenReturn(new CommitsPage(List.of(), false));

        servlet.doGet(request, response);

        assertNull(sentToGitHub().getSince());
        verify(request).setAttribute("periodKey", "all");
        verify(request).setAttribute("periodWarning", "Milestone 2 starts on Sep 18, 2026. Showing all history instead.");
    }

    @Test
    public void gitHubErrorIsShownInsteadOfEmptyList() throws Exception {
        when(proxy.getCommits(any())).thenThrow(new GitHubApiException(429, GitHubApiException.RATE_LIMITED, "GitHub API rate limit reached."));

        servlet.doGet(request, response);

        verify(request).setAttribute("githubError", "GitHub API rate limit reached.");
        verify(request).setAttribute("totalCommits", 0);
    }

    @Test
    public void truncationIsExposed() throws Exception {
        when(proxy.getCommits(any())).thenReturn(new CommitsPage(List.of(), true));

        servlet.doGet(request, response);

        verify(request).setAttribute("truncated", true);
    }
}
