package br.com.gradechecker.servlet;

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

    @Before
    public void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        loginPage = mock(RequestDispatcher.class);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(session); // a session already exists (login page visited)
        when(request.getRequestDispatcher("/authentication.jsp")).thenReturn(loginPage);
    }

    @Test
    public void validTokenRenewsSessionStoresTokenAndRedirects() throws Exception {
        when(request.getParameter("access-token")).thenReturn("ghp_abc");

        new LoginServlet().doPost(request, response);

        InOrder order = inOrder(request, session, response);
        order.verify(request).changeSessionId();
        order.verify(session).setAttribute("accessToken", "ghp_abc");
        order.verify(response).sendRedirect("/home");
    }

    @Test
    public void withoutPreviousSessionCreatesOneInsteadOfFailing() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.changeSessionId()).thenThrow(new IllegalStateException("no session")); // servlet container behaviour
        when(request.getParameter("access-token")).thenReturn("ghp_abc");

        new LoginServlet().doPost(request, response);

        verify(request, never()).changeSessionId();
        verify(session).setAttribute("accessToken", "ghp_abc");
        verify(response).sendRedirect("/home");
    }

    @Test
    public void tokenIsTrimmed() throws Exception {
        when(request.getParameter("access-token")).thenReturn("  ghp_abc  ");

        new LoginServlet().doPost(request, response);

        verify(session).setAttribute("accessToken", "ghp_abc");
    }

    @Test
    public void blankOrMissingTokenShowsLoginWith400InsteadOfError() throws Exception {
        for (String token : new String[]{"   ", "", null}) {
            setUp();
            when(request.getParameter("access-token")).thenReturn(token);

            new LoginServlet().doPost(request, response);

            verify(response).setStatus(400);
            verify(request).setAttribute("message", LoginServlet.MISSING_TOKEN);
            verify(request).setAttribute("tokenFormOpen", true);
            verify(loginPage).forward(request, response);
            verify(session, never()).setAttribute(eq("accessToken"), any());
            verify(request, never()).changeSessionId();
            verify(response, never()).sendRedirect(anyString());
        }
    }
}
