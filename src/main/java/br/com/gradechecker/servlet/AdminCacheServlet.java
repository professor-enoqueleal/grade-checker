package br.com.gradechecker.servlet;

import br.com.gradechecker.cache.CacheManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Admin endpoint to manage CacheManager at runtime.
 * POST /admin/cache?action=stats
 * POST /admin/cache?action=invalidate&key={key}
 * POST /admin/cache?action=invalidateAll
 * POST /admin/cache?action=rebuild&enabled=true&ttl=60&max=10000
 *
 * Protect with header X-ADMIN-TOKEN matching env GCACHE_ADMIN_SECRET.
 */
@WebServlet(name = "AdminCacheServlet", urlPatterns = {"/admin/cache"})
public class AdminCacheServlet extends HttpServlet {

    private static final String ADMIN_TOKEN_ENV = "GCACHE_ADMIN_SECRET";
    private static final String ADMIN_HEADER = "X-ADMIN-TOKEN";

    private boolean authorize(HttpServletRequest req) {
        String secret = System.getenv(ADMIN_TOKEN_ENV);
        if (secret == null || secret.isEmpty()) {
            return false; // deny by default
        }
        String header = req.getHeader(ADMIN_HEADER);
        return secret.equals(header);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Read-only stats endpoint for GET /admin/cache/stats
        // Authorization is the same as POST
        if (!authorize(req)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json");
            try (PrintWriter w = resp.getWriter()) {
                w.print("{\"error\":\"forbidden\"}");
            }
            return;
        }

        // Only expose stats on GET; ignore other paths
        resp.setContentType("application/json");
        try (PrintWriter w = resp.getWriter()) {
            boolean enabled = CacheManager.isEnabled();
            long hits = CacheManager.getHitCount();
            long misses = CacheManager.getMissCount();
            w.printf("{\"enabled\":%b,\"hits\":%d,\"misses\":%d}", enabled, hits, misses);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!authorize(req)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json");
            try (PrintWriter w = resp.getWriter()) {
                w.print("{\"error\":\"forbidden\"}");
            }
            return;
        }

        String action = req.getParameter("action");
        if (action == null) action = "stats";

        resp.setContentType("application/json");
        try (PrintWriter w = resp.getWriter()) {
            switch (action) {
                case "stats":
                    boolean enabled = CacheManager.isEnabled();
                    long hits = CacheManager.getHitCount();
                    long misses = CacheManager.getMissCount();
                    w.printf("{\"enabled\":%b,\"hits\":%d,\"misses\":%d}", enabled, hits, misses);
                    break;

                case "invalidate":
                    String key = req.getParameter("key");
                    if (key == null || key.isEmpty()) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        w.print("{\"error\":\"missing key\"}");
                    } else {
                        CacheManager.invalidate(key);
                        w.printf("{\"invalidated\":\"%s\"}", escapeJson(key));
                    }
                    break;

                case "invalidateAll":
                    CacheManager.invalidateAll();
                    w.print("{\"invalidatedAll\":true}");
                    break;

                case "rebuild":
                    String enabledParam = req.getParameter("enabled");
                    String ttlParam = req.getParameter("ttl");
                    String maxParam = req.getParameter("max");

                    Boolean enabledB = enabledParam == null ? null : Boolean.parseBoolean(enabledParam);
                    Long ttl = null;
                    Long max = null;
                    try {
                        if (ttlParam != null && !ttlParam.isEmpty()) ttl = Long.parseLong(ttlParam);
                    } catch (NumberFormatException ignored) {}
                    try {
                        if (maxParam != null && !maxParam.isEmpty()) max = Long.parseLong(maxParam);
                    } catch (NumberFormatException ignored) {}

                    CacheManager.rebuild(enabledB, ttl, max);
                    w.printf("{\"rebuild\":true,\"enabled\":%b,\"ttl\":%s,\"max\":%s}",
                            CacheManager.isEnabled(),
                            ttl == null ? "\"default\"" : ttl.toString(),
                            max == null ? "\"default\"" : max.toString());
                    break;

                default:
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    w.printf("{\"error\":\"unknown action '%s'\"}", escapeJson(action));
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
