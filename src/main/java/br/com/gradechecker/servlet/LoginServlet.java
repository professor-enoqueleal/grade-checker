package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubUserProxy;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Login page (GET) and sign in with a personal access token (POST) - openspec capabilities
 * token-sign-in and platform-admin (the token is validated with GET /user and the login kept in the session).
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    static final String MISSING_TOKEN = "Please enter a personal access token.";
    static final String REJECTED_TOKEN = "GitHub rejected this personal access token.";
    static final String UNVERIFIED_TOKEN = "Could not verify the token with GitHub. Please try again.";

    private final GitHubUserProxy userProxy;

    public LoginServlet() {
        this(new GitHubUserProxy());
    }

    public LoginServlet(GitHubUserProxy userProxy) {
        this.userProxy = userProxy;
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletRequest.getRequestDispatcher("/authentication.jsp").forward(httpServletRequest, httpServletResponse);

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        final String parameter = request.getParameter("access-token");
        final String accessToken = parameter == null ? "" : parameter.trim();

        if (accessToken.isEmpty()) {
            showTokenForm(request, response, HttpServletResponse.SC_BAD_REQUEST, MISSING_TOKEN);
            return;
        }

        // validated before any session change: a rejected token never creates an authenticated session
        final String userLogin;
        try {
            userLogin = userProxy.fetchLogin(accessToken);
        } catch (GitHubApiException e) {
            if (e.getHttpStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                showTokenForm(request, response, HttpServletResponse.SC_UNAUTHORIZED, REJECTED_TOKEN);
            } else {
                showTokenForm(request, response, HttpServletResponse.SC_BAD_GATEWAY, UNVERIFIED_TOKEN);
            }
            return;
        }

        // session fixation protection, same as the GitHub sign-in: renew an existing session id,
        // or start a new session (changeSessionId() throws when there is no session yet)
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true).setAttribute("accessToken", accessToken);
        request.getSession(true).setAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE, userLogin);

        response.sendRedirect("/home");

    }

    private void showTokenForm(HttpServletRequest request, HttpServletResponse response, int status, String message) throws ServletException, IOException {
        response.setStatus(status);
        request.setAttribute("message", message);
        request.setAttribute("tokenFormOpen", true); // show the token form again, focused
        request.getRequestDispatcher("/authentication.jsp").forward(request, response);
    }

}
