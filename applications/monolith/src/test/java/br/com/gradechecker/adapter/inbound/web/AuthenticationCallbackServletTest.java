package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GitHubOAuthConfig;
import br.com.gradechecker.core.adapter.github.AccessToken;
import br.com.gradechecker.core.adapter.github.GitHubOAuthClient;
import br.com.gradechecker.core.github.GitHubApiException;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** openspec capability github-sign-in: state validation, errors and session renewal on /callback. */
public class AuthenticationCallbackServletTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    @Rule
    public EnvironmentVariables environment = new EnvironmentVariables();

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher loginPage;
    private final Map<String, Object> sessionAttributes = new HashMap<>();
    /** The servlet over a mocked OAuth client whose tokens belong to alice (unless the test says otherwise). */
    private AuthenticationCallbackServlet servlet(GitHubOAuthClient client) {
        boolean identified = mockingDetails(client).getStubbings().stream()
                .anyMatch(stubbing -> stubbing.getInvocation().getMethod().getName().equals("fetchLogin"));
        if (!identified) when(client.fetchLogin(anyString())).thenReturn("alice");
        return new AuthenticationCallbackServlet(client);
    }

    @Before
    public void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        loginPage = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher("/authentication.jsp")).thenReturn(loginPage);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);

        // in-memory session attributes
        doAnswer(inv -> sessionAttributes.put(inv.getArgument(0), inv.getArgument(1))).when(session).setAttribute(anyString(), any());
        when(session.getAttribute(anyString())).thenAnswer(inv -> sessionAttributes.get(inv.getArgument(0)));
        doAnswer(inv -> sessionAttributes.remove(inv.getArgument(0))).when(session).removeAttribute(anyString());
    }

    private void callback(String code, String state) {
        when(request.getParameter("code")).thenReturn(code);
        when(request.getParameter("state")).thenReturn(state);
    }

    private static AccessToken token(String value) {
        AccessToken token = new AccessToken();
        token.setAccessToken(value);
        return token;
    }

    @Test
    public void validStateExchangesCodeRenewsSessionAndRedirects() throws Exception {
        sessionAttributes.put(GitHubLoginServlet.STATE_ATTRIBUTE, "S1");
        callback("CODE", "S1");
        GitHubOAuthClient client = mock(GitHubOAuthClient.class);
        when(client.exchangeCodeForToken("CODE")).thenReturn(token("ghu_ok"));

        servlet(client).doGet(request, response);

        InOrder order = inOrder(request, session, response);
        order.verify(request).changeSessionId();
        order.verify(session).setAttribute("accessToken", "ghu_ok");
        order.verify(response).sendRedirect("/home");
        assertFalse("state is single use", sessionAttributes.containsKey(GitHubLoginServlet.STATE_ATTRIBUTE));
        assertEquals("alice", sessionAttributes.get(AdminConfig.USER_LOGIN_ATTRIBUTE));
        assertEquals(Boolean.FALSE, sessionAttributes.get(AdminConfig.IS_ADMIN_ATTRIBUTE));
        verify(client).fetchLogin("ghu_ok");
    }

    @Test
    public void failureToIdentifyTheUserFailsTheSignIn() throws Exception {
        sessionAttributes.put(GitHubLoginServlet.STATE_ATTRIBUTE, "S1");
        callback("CODE", "S1");
        GitHubOAuthClient client = mock(GitHubOAuthClient.class);
        when(client.exchangeCodeForToken("CODE")).thenReturn(token("ghu_ok"));
        when(client.fetchLogin("ghu_ok")).thenThrow(new GitHubApiException(502, GitHubApiException.ERROR, "down"));

        servlet(client).doGet(request, response);

        verify(request).setAttribute("message", AuthenticationCallbackServlet.FAILED);
        verify(session, never()).setAttribute(eq("accessToken"), any());
        verify(request, never()).changeSessionId();
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void missingOrDifferentStateIsRejectedWithoutExchangingTheCode() throws Exception {
        for (String state : new String[]{null, "OTHER"}) {
            sessionAttributes.put(GitHubLoginServlet.STATE_ATTRIBUTE, "S1");
            callback("CODE", state);
            GitHubOAuthClient client = mock(GitHubOAuthClient.class);

            servlet(client).doGet(request, response);

            verifyNoInteractions(client);
            verify(request, atLeastOnce()).setAttribute("message", AuthenticationCallbackServlet.NOT_VERIFIED);
            verify(response, never()).sendRedirect(anyString());
        }
    }

    @Test
    public void noSessionIsRejected() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        callback("CODE", "S1");
        GitHubOAuthClient client = mock(GitHubOAuthClient.class);

        servlet(client).doGet(request, response);

        verifyNoInteractions(client);
        verify(request).setAttribute("message", AuthenticationCallbackServlet.NOT_VERIFIED);
    }

    @Test
    public void reusedStateIsRejected() throws Exception {
        sessionAttributes.put(GitHubLoginServlet.STATE_ATTRIBUTE, "S1");
        callback("CODE", "S1");
        GitHubOAuthClient client = mock(GitHubOAuthClient.class);
        when(client.exchangeCodeForToken("CODE")).thenReturn(token("ghu_ok"));
        AuthenticationCallbackServlet servlet = servlet(client);

        servlet.doGet(request, response);
        servlet.doGet(request, response); // same URL again

        verify(client, times(1)).exchangeCodeForToken("CODE");
        verify(request).setAttribute("message", AuthenticationCallbackServlet.NOT_VERIFIED);
    }

    @Test
    public void errorFromGitHubIsShown() throws Exception {
        callback(null, null);
        when(request.getParameter("error_description")).thenReturn("The user has denied your application access.");

        servlet(mock(GitHubOAuthClient.class)).doGet(request, response);

        verify(request).setAttribute("message", "The user has denied your application access.");
        verify(loginPage).forward(request, response);
    }

    @Test
    public void failedExchangeShowsMessageInsteadOfError500() throws Exception {
        for (boolean throwsException : new boolean[]{true, false}) {
            sessionAttributes.put(GitHubLoginServlet.STATE_ATTRIBUTE, "S1");
            callback("CODE", "S1");
            GitHubOAuthClient client = mock(GitHubOAuthClient.class);
            if (throwsException) {
                when(client.exchangeCodeForToken("CODE")).thenThrow(new RuntimeException("network"));
            } else {
                when(client.exchangeCodeForToken("CODE")).thenReturn(null); // e.g. expired code
            }

            servlet(client).doGet(request, response);

            verify(session, never()).setAttribute(eq("accessToken"), any());
            verify(response, never()).sendRedirect(anyString());
        }
        verify(request, times(2)).setAttribute("message", AuthenticationCallbackServlet.FAILED);
    }

    @Test
    public void clientExchangesCodeAgainstConfiguredGitHub() {
        environment.set("GITHUB_BASE_URL", "http://localhost:" + wireMockRule.port() + "/");
        environment.set("GITHUB_CLIENT_ID", "XYZ");
        environment.set("GITHUB_CLIENT_SECRET", "1234");
        stubFor(post("/login/oauth/access_token")
                .withRequestBody(containing("client_id=XYZ"))
                .withRequestBody(containing("code=12345"))
                .willReturn(ok().withHeader("Content-Type", "application/json").withBodyFile("github_authenticated_with_success.json")));

        assertEquals("ghu_xyz", new GitHubOAuthClient(GitHubOAuthConfig.settings()).exchangeCodeForToken("12345").getAccessToken());
    }

    @Test
    public void clientReturnsNullWhenGitHubSendsNoToken() {
        environment.set("GITHUB_BASE_URL", "http://localhost:" + wireMockRule.port());
        environment.set("GITHUB_CLIENT_ID", "XYZ");
        environment.set("GITHUB_CLIENT_SECRET", "1234");
        stubFor(post("/login/oauth/access_token")
                .willReturn(okJson("{\"error\":\"bad_verification_code\",\"error_description\":\"The code passed is incorrect or expired.\"}")));

        assertNull(new GitHubOAuthClient(GitHubOAuthConfig.settings()).exchangeCodeForToken("expired"));
    }
}
