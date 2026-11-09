package br.com.gradechecker.adapter.inbound.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** openspec capability github-sign-in: /login/github. */
public class GitHubLoginServletTest {

    @Rule
    public EnvironmentVariables environment = new EnvironmentVariables();

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(session);
        environment.set("GITHUB_BASE_URL", null);
    }

    @Test
    public void redirectsToGitHubWithConfiguredClientIdAndState() throws Exception {
        environment.set("GITHUB_CLIENT_ID", "Iv1.abc");

        new GitHubLoginServlet().doGet(request, response);

        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        verify(session).setAttribute(eq(GitHubLoginServlet.STATE_ATTRIBUTE), state.capture());
        assertTrue(state.getValue().length() >= 43); // 32 bytes, base64url

        verify(response).sendRedirect("https://github.com/login/oauth/authorize?client_id=Iv1.abc&state=" + state.getValue());
    }

    @Test
    public void usesConfiguredBaseUrl() throws Exception {
        environment.set("GITHUB_CLIENT_ID", "Iv1.abc");
        environment.set("GITHUB_BASE_URL", "https://ghe.example.com/");

        new GitHubLoginServlet().doGet(request, response);

        verify(response).sendRedirect(startsWith("https://ghe.example.com/login/oauth/authorize?client_id=Iv1.abc&state="));
    }

    @Test
    public void notConfiguredShowsMessageOnLoginPage() throws Exception {
        environment.set("GITHUB_CLIENT_ID", null);
        RequestDispatcher loginPage = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/authentication.jsp")).thenReturn(loginPage);

        new GitHubLoginServlet().doGet(request, response);

        verify(request).setAttribute("message", "GitHub sign-in is not configured.");
        verify(loginPage).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void stateIsRandomAndUrlSafe() {
        Set<String> states = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String state = GitHubLoginServlet.newState();
            assertTrue(state, state.matches("[A-Za-z0-9_-]{43}"));
            states.add(state);
        }
        assertEquals(100, states.size());
    }
}
