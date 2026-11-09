package br.com.gradechecker.adapter.inbound.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Test;

import static org.mockito.Mockito.*;

/** openspec capability sign-out. */
public class LogoutServletTest {

    @Test
    public void invalidatesSessionAndShowsSuccessMessage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher loginPage = mock(RequestDispatcher.class);
        when(request.getSession()).thenReturn(session);
        when(request.getRequestDispatcher("/authentication.jsp")).thenReturn(loginPage);

        new LogoutServlet().doGet(request, response);

        verify(session).invalidate();
        verify(request).setAttribute("message", "You have been signed out.");
        verify(request).setAttribute("messageType", "success");
        verify(loginPage).forward(request, response);
    }
}
