package br.com.gradechecker.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class AuthenticationFilterTest {

    private AuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private RequestDispatcher dispatcher;
    private StringWriter body;

    @Before
    public void setUp() throws Exception {
        filter = new AuthenticationFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        dispatcher = mock(RequestDispatcher.class);
        body = new StringWriter();

        when(request.getRequestDispatcher("/authentication.jsp")).thenReturn(dispatcher);
        when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    /** servletPath/pathInfo as the container would expose them (already decoded and normalized). */
    private void path(String servletPath, String pathInfo) {
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getPathInfo()).thenReturn(pathInfo);
    }

    private void loggedIn() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("accessToken")).thenReturn("token");
        when(request.getSession(false)).thenReturn(session);
    }

    private void assertPassesThrough() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(dispatcher);
    }

    private void assertForwardedToLogin() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain, never()).doFilter(any(), any());
        verify(request).setAttribute("hasMessage", true);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void protectedRouteWithoutSessionIsForwardedToLogin() throws Exception {
        path("/home", null);
        assertForwardedToLogin();
    }

    @Test
    public void protectedRouteWithSessionWithoutTokenIsForwardedToLogin() throws Exception {
        path("/home", null);
        when(request.getSession(false)).thenReturn(mock(HttpSession.class));
        assertForwardedToLogin();
    }

    @Test
    public void previouslyOpenRoutesAreNowProtected() throws Exception {
        for (String route : new String[]{"/group/create", "/member/create", "/database", "/detail.jsp", "/create-group.html"}) {
            setUp();
            path(route, null);
            assertForwardedToLogin();
        }
    }

    @Test
    public void protectedRouteWithSessionPassesThrough() throws Exception {
        path("/group/create", null);
        loggedIn();
        assertPassesThrough();
    }

    @Test
    public void publicRoutesPassThroughWithoutSession() throws Exception {
        String[][] routes = {
                {"", "/"},                      // root mapped to welcome file
                {"/", null},
                {"/login", null},
                {"/logout", null},
                {"/callback", null},
                {"/authentication.jsp", null},
                {"/console", null},
                {"/console", "/login.jsp"},     // H2 console servlet mapped to /console/*
                {"/css/style.css", null},
                {"/js/color-modes.js", null},
                {"/img/group.png", null},
                {"/webjars/bootstrap/5.3.0/css/bootstrap.min.css", null},
                {"/favicon.ico", null},
                {"/admin/cache", null},         // own auth via X-ADMIN-TOKEN
                {"/admin/cache/stats", null}
        };
        for (String[] route : routes) {
            setUp();
            path(route[0], route[1]);
            assertPassesThrough();
        }
    }

    @Test
    public void pathContainingLoginIsNotPublic() throws Exception {
        path("/group/create/login", null);
        assertForwardedToLogin();
    }

    @Test
    public void otherAdminRoutesRemainProtected() throws Exception {
        for (String route : new String[]{"/admin", "/admin/", "/admin/other", "/admin/cache/other"}) {
            setUp();
            path(route, null);
            assertForwardedToLogin();
        }
    }

    @Test
    public void pathStartingWithPublicNameButNotPrefixIsNotPublic() throws Exception {
        path("/consolefake", null);
        assertForwardedToLogin();
    }

    @Test
    public void normalizedTraversalPathIsEvaluatedAsRealPath() throws Exception {
        // the container normalizes "/css/../home" (and "/css/%2e%2e/home") to servletPath "/home"
        path("/home", null);
        assertForwardedToLogin();
    }

    @Test
    public void ajaxRequestWithoutSessionReceives401Json() throws Exception {
        path("/contributors-stats", null);
        when(request.getHeader("Accept")).thenReturn("application/json, text/plain, */*");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(dispatcher);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertTrue(body.toString().contains("\"error\":\"unauthorized\""));
    }

    @Test
    public void xRequestedWithWithoutSessionReceives401() throws Exception {
        path("/contributors-stats", null);
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(dispatcher);
    }

    @Test
    public void isPublicMatchesExactAndPrefixOnly() {
        assertTrue(AuthenticationFilter.isPublic("/login"));
        assertTrue(AuthenticationFilter.isPublic("/console/"));
        assertFalse(AuthenticationFilter.isPublic("/login/extra"));
        assertFalse(AuthenticationFilter.isPublic("/css"));
        assertFalse(AuthenticationFilter.isPublic("/home"));
    }

    @Test
    public void resolvePathCombinesServletPathAndPathInfo() {
        path("/console", "/login.jsp");
        assertEquals("/console/login.jsp", AuthenticationFilter.resolvePath(request));
    }
}
