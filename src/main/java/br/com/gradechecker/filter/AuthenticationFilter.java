package br.com.gradechecker.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Protects every route by default. Only the routes listed in {@link #PUBLIC_PATHS}
 * and {@link #PUBLIC_PREFIXES} are reachable without an authenticated session.
 *
 * See openspec/changes/proteger-rotas.
 */
@WebFilter("/*")
public class AuthenticationFilter implements Filter {

    static final String SESSION_TOKEN_ATTRIBUTE = "accessToken";

    static final String UNAUTHORIZED_MESSAGE = "Please sign in to continue.";

    /** Routes matched by exact path (without context path). */
    static final Set<String> PUBLIC_PATHS = Set.of(
            "/",
            "/login",
            "/login/github",   // starts the GitHub OAuth flow
            "/logout",
            "/callback",
            "/authentication.jsp",
            "/console",
            "/favicon.ico",
            // AdminCacheServlet has its own authentication (X-ADMIN-TOKEN header)
            "/admin/cache",
            "/admin/cache/stats"
    );

    /** Routes matched by prefix (without context path). Each prefix ends with "/". */
    static final List<String> PUBLIC_PREFIXES = List.of(
            "/console/",
            "/css/",
            "/js/",
            "/img/",
            "/webjars/"
    );

    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = resolvePath(request);

        if (isPublic(path) || isUserLoggedOn(request)) {

            filterChain.doFilter(servletRequest, servletResponse);
            return;

        }

        logger.info("Unauthorized request to {}", path);

        if (expectsJson(request)) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + UNAUTHORIZED_MESSAGE + "\"}");
            return;

        }

        request.setAttribute("hasMessage", true);
        request.setAttribute("message", UNAUTHORIZED_MESSAGE);

        request.getRequestDispatcher("/authentication.jsp").forward(request, response);

    }

    /**
     * Path of the request without the context path. Uses servletPath + pathInfo, which the
     * container already decodes and normalizes (so "/css/../home" or "/css/%2e%2e/home"
     * resolve to "/home" and path parameters like ";jsessionid" are stripped).
     */
    static String resolvePath(HttpServletRequest request) {

        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();

        String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);

        return path.isEmpty() ? "/" : path;

    }

    static boolean isPublic(String path) {

        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }

        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;

    }

    private boolean isUserLoggedOn(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        return session != null && session.getAttribute(SESSION_TOKEN_ATTRIBUTE) != null;

    }

    private boolean expectsJson(HttpServletRequest request) {

        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");

        return (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith);

    }

}
