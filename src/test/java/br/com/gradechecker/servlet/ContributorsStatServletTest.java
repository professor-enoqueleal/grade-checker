package br.com.gradechecker.servlet;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.service.ContributorsService;
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
import java.time.Instant;
import java.util.List;

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
        servlet = new ContributorsStatServlet(service);
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
    public void usesDefaultPeriodAndSessionToken() throws Exception {
        when(request.getParameter("owner")).thenReturn("octocat");
        when(request.getParameter("repo")).thenReturn("hello-world");
        when(service.getContributionsReport(any())).thenReturn(new ContributionsReport("s", "u", 0, false, List.of()));

        servlet.doGet(request, response);

        ArgumentCaptor<CommitRequestBody> captor = ArgumentCaptor.forClass(CommitRequestBody.class);
        verify(service).getContributionsReport(captor.capture());
        assertEquals(ContributorsStatServlet.DEFAULT_SINCE, captor.getValue().getSince());
        assertTrue(captor.getValue().getUntil().endsWith(":00Z"));
        assertEquals("token", captor.getValue().getAccessToken());
        assertEquals("octocat", captor.getValue().getOwner());
    }

    @Test
    public void defaultUntilIsRoundedUpToTheMinute() {
        assertEquals("2026-11-05T14:33:00Z", ContributorsStatServlet.defaultUntil(Instant.parse("2026-11-05T14:32:10Z")));
        assertEquals("2026-11-05T14:32:00Z", ContributorsStatServlet.defaultUntil(Instant.parse("2026-11-05T14:32:00Z")));
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
