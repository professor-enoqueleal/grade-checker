package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.IdentityLinking;
import br.com.gradechecker.core.error.DomainException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Links (POST) and unlinks (DELETE) a git identity without account to a student of the group
 * (openspec capability identity-aliases). Administrators only (GRADE_CHECKER_ADMINS).
 *
 * Parameters: groupId, identityKey ("email:..." or "name:..."), login (POST only).
 * Requests must carry "X-Requested-With: XMLHttpRequest" (sent by the dashboard), which a cross-site
 * form cannot set: protection against CSRF on these state-changing calls.
 */
@WebServlet("/identity-aliases")
public class IdentityAliasServlet extends HttpServlet {

    static final String FORBIDDEN = "Only administrators can link identities.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final IdentityLinking linking;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public IdentityAliasServlet() {
        this(AppContext.get().identityLinking);
    }

    public IdentityAliasServlet(IdentityLinking linking) {
        this.linking = linking;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Long groupId = authorize(request, response);
        if (groupId == null) return;

        String identityKey = request.getParameter("identityKey");
        try {
            String student = linking.link(groupId, identityKey, trim(request.getParameter("login")),
                    AdminConfig.userLogin(request.getSession(false)));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("groupId", groupId);
            body.put("identityKey", identityKey);
            body.put("login", student);
            write(response, HttpServletResponse.SC_OK, body);
        } catch (DomainException e) {
            write(response, DomainErrors.status(e), error(e.getCode(), e.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Long groupId = authorize(request, response);
        if (groupId == null) return;

        try {
            linking.unlink(groupId, request.getParameter("identityKey"));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("removed", true);
            write(response, HttpServletResponse.SC_OK, body);
        } catch (DomainException e) {
            write(response, DomainErrors.status(e), error(e.getCode(), e.getMessage()));
        }
    }

    /** CSRF header, administrator and group id; returns the group id, or null after writing the error response. */
    private Long authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!"XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            write(response, HttpServletResponse.SC_FORBIDDEN, error("forbidden", "Missing X-Requested-With header."));
            return null;
        }

        if (!AdminConfig.isAdmin(request.getSession(false))) {
            logger.warn("Identity alias change refused for {}", AdminConfig.userLogin(request.getSession(false)));
            write(response, HttpServletResponse.SC_FORBIDDEN, error("forbidden", FORBIDDEN));
            return null;
        }

        try {
            return Long.parseLong(trim(request.getParameter("groupId")));
        } catch (NumberFormatException | NullPointerException e) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "The 'groupId' parameter is required."));
            return null;
        }
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return body;
    }

    private void write(HttpServletResponse response, int status, Map<String, Object> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(gson.toJson(body));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
