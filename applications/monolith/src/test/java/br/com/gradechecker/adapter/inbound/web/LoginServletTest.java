package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.adapter.github.GitHubOAuthClient;
import br.com.gradechecker.core.github.GitHubApiException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

/** openspec capability token-sign-in. */
public class LoginServletTest {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher loginPage;
    private GitHubOAuthClient gitHubOAuth;

    @Before
    public void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        loginPage = mock(RequestDispatcher.class);
        gitHubOAuth = mock(GitHubOAuthClient.class);
        when(gitHubOAuth.fetchLogin(anyString())).thenReturn("alice");
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(session); // a session already exists (login page visited)
        when(request.getRequestDispatcher("/authentication.jsp")).thenReturn(loginPage);
    }

    @Test
    public void validTokenRenewsSessionStoresTokenAndRedirects() throws Exception {
        when(request.getParameter("access-token")).thenReturn("ghp_abc");

        new LoginServlet(gitHubOAuth).doPost(request, response);

        InOrder order = inOrder(request, session, response);
        order.verify(request).changeSessionId();
        order.verify(session).setAttribute("accessToken", "ghp_abc");
        order.verify(response).sendRedirect("/home");
        verify(session).setAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE, "alice");
        verify(session).setAttribute(AdminConfig.IS_ADMIN_ATTRIBUTE, false);
    }

    @Test
    public void tokenRejectedByGitHubShows401WithoutSession() throws Exception {
        when(request.getParameter("access-token")).thenReturn("ghp_bad");
        when(gitHubOAuth.fetchLogin("ghp_bad")).thenThrow(new GitHubApiException(401, GitHubApiException.UNAUTHORIZED, "rejected"));

        new LoginServlet(gitHubOAuth).doPost(request, response);

        verify(response).setStatus(401);
        verify(request).setAttribute("message", LoginServlet.REJECTED_TOKEN);
        verify(request).setAttribute("tokenFormOpen", true);
        verify(loginPage).forward(request, response);
        verify(session, never()).setAttribute(eq("accessToken"), any());
        verify(request, never()).changeSessionId();
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void gitHubUnavailableShows502WithoutSession() throws Exception {
        when(request.getParameter("access-token")).thenReturn("ghp_abc");
        when(gitHubOAuth.fetchLogin("ghp_abc")).thenThrow(new GitHubApiException(502, GitHubApiException.ERROR, "down"));

        new LoginServlet(gitHubOAuth).doPost(request, response);

        verify(response).setStatus(502);
        verify(request).setAttribute("message", LoginServlet.UNVERIFIED_TOKEN);
        verify(session, never()).setAttribute(eq("accessToken"), any());
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void withoutPreviousSessionCreatesOneInsteadOfFailing() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.changeSessionId()).thenThrow(new IllegalStateException("no session")); // servlet container behaviour
        when(request.getParameter("access-token")).thenReturn("ghp_abc");

        new LoginServlet(gitHubOAuth).doPost(request, response);

        verify(request, never()).changeSessionId();
        verify(session).setAttribute("accessToken", "ghp_abc");
        verify(response).sendRedirect("/home");
    }

    @Test
    public void tokenIsTrimmed() throws Exception {
        when(request.getParameter("access-token")).thenReturn("  ghp_abc  ");

        new LoginServlet(gitHubOAuth).doPost(request, response);

        verify(session).setAttribute("accessToken", "ghp_abc");
    }

    @Test
    public void blankOrMissingTokenShowsLoginWith400InsteadOfError() throws Exception {
        for (String token : new String[]{"   ", "", null}) {
            setUp();
            when(request.getParameter("access-token")).thenReturn(token);

            new LoginServlet(gitHubOAuth).doPost(request, response);

            verify(response).setStatus(400);
            verify(request).setAttribute("message", LoginServlet.MISSING_TOKEN);
            verify(request).setAttribute("tokenFormOpen", true);
            verify(loginPage).forward(request, response);
            verify(session, never()).setAttribute(eq("accessToken"), any());
            verify(request, never()).changeSessionId();
            verify(response, never()).sendRedirect(anyString());
            verifyNoInteractions(gitHubOAuth);
        }
    }
}
