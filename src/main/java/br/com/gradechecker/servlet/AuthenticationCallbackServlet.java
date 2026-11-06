package br.com.gradechecker.servlet;

import br.com.gradechecker.model.AccessToken;
import br.com.gradechecker.proxy.AuthenticationProxy;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * GitHub OAuth callback: validates the one-time state, exchanges the code for a token and renews the
 * session id (openspec capability github-sign-in).
 */
@WebServlet("/callback")
public class AuthenticationCallbackServlet extends HttpServlet {

    static final String NOT_VERIFIED = "GitHub sign-in could not be verified. Please try again.";
    static final String FAILED = "GitHub sign-in failed. Please try again.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final AuthenticationProxy authenticationProxy;

    public AuthenticationCallbackServlet() {
        this(new AuthenticationProxy());
    }

    public AuthenticationCallbackServlet(AuthenticationProxy authenticationProxy) {
        this.authenticationProxy = authenticationProxy;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String code = request.getParameter("code");

        if (code == null || code.isEmpty()) {
            // e.g. the user denied access on GitHub
            String errorDescription = request.getParameter("error_description");
            showLogin(request, response, errorDescription != null ? errorDescription : FAILED);
            return;
        }

        if (!consumeState(request)) {
            logger.warn("GitHub callback rejected: missing or invalid state");
            showLogin(request, response, NOT_VERIFIED);
            return;
        }

        AccessToken accessToken;

        try {
            accessToken = authenticationProxy.exchangeCodePerToken(code);
        } catch (RuntimeException e) {
            logger.error("GitHub sign-in failed: {}", e.getMessage());
            showLogin(request, response, FAILED);
            return;
        }

        if (accessToken == null) {
            showLogin(request, response, FAILED);
            return;
        }

        request.changeSessionId(); // session fixation protection
        request.getSession().setAttribute("accessToken", accessToken.getAccessToken());

        response.sendRedirect("/home");
    }

    /** True when the state parameter matches the one stored by /login/github; the stored state is always discarded. */
    private boolean consumeState(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        String received = request.getParameter("state");

        if (session == null) return false;

        Object expected = session.getAttribute(GitHubLoginServlet.STATE_ATTRIBUTE);
        session.removeAttribute(GitHubLoginServlet.STATE_ATTRIBUTE);

        if (!(expected instanceof String) || received == null) return false;

        return MessageDigest.isEqual(
                ((String) expected).getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    private void showLogin(HttpServletRequest request, HttpServletResponse response, String message) throws ServletException, IOException {
        request.setAttribute("message", message);
        request.getRequestDispatcher("/authentication.jsp").forward(request, response);
    }

}
