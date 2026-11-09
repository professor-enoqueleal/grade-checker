package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.GitHubOAuthConfig;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Starts the GitHub sign-in: builds the authorize URL from the configured client id and a one-time
 * state stored in the session (openspec capability github-sign-in).
 */
@WebServlet("/login/github")
public class GitHubLoginServlet extends HttpServlet {

    public static final String STATE_ATTRIBUTE = "oauthState";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String clientId = GitHubOAuthConfig.clientId();

        if (clientId == null) {
            logger.error("GitHub sign-in requested but GITHUB_CLIENT_ID is not configured");
            request.setAttribute("message", "GitHub sign-in is not configured.");
            request.getRequestDispatcher("/authentication.jsp").forward(request, response);
            return;
        }

        String state = newState();
        request.getSession(true).setAttribute(STATE_ATTRIBUTE, state);

        response.sendRedirect(GitHubOAuthConfig.baseUrl() + "/login/oauth/authorize"
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
    }

    /** 256 random bits, base64url without padding. */
    static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
