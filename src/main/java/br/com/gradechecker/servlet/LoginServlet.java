package br.com.gradechecker.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Login page (GET) and sign in with a personal access token (POST) - openspec capability token-sign-in. */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    static final String MISSING_TOKEN = "Please enter a personal access token.";

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletRequest.getRequestDispatcher("/authentication.jsp").forward(httpServletRequest, httpServletResponse);

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        final String parameter = request.getParameter("access-token");
        final String accessToken = parameter == null ? "" : parameter.trim();

        if (accessToken.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("message", MISSING_TOKEN);
            request.setAttribute("tokenFormOpen", true); // show the token form again, focused
            request.getRequestDispatcher("/authentication.jsp").forward(request, response);
            return;
        }

        // session fixation protection, same as the GitHub sign-in: renew an existing session id,
        // or start a new session (changeSessionId() throws when there is no session yet)
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true).setAttribute("accessToken", accessToken);

        response.sendRedirect("/home");

    }

}
