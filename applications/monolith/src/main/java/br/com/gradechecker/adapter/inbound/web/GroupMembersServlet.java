package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.application.Membership.MemberChange;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.GitHubApiException;

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
 * Adds (POST), edits (PUT) and removes (DELETE) members of a group (openspec capability group-members). Administrators only.
 *
 * Parameters: groupId, login, name (POST only, optional; default = login), manual (POST only: "true" for the
 * "Add a new member" form, false for "Mark as member" on a contributor card).
 * Requests must carry "X-Requested-With: XMLHttpRequest" (sent by the page), which a cross-site form cannot set:
 * protection against CSRF on these grade-changing calls. The rules are those of the use case Membership of
 * grade-checker-core.
 */
@WebServlet("/group-members")
public class GroupMembersServlet extends HttpServlet {

    static final String FORBIDDEN = "Only administrators can manage members.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final Membership membership;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public GroupMembersServlet() {
        this(AppContext.get().membership);
    }

    public GroupMembersServlet(Membership membership) {
        this.membership = membership;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Long groupId = authorize(request, response);
        if (groupId == null) return;

        try {
            MemberChange added = membership.add(groupId, request.getParameter("login"), request.getParameter("name"),
                    "true".equalsIgnoreCase(request.getParameter("manual")), accessToken(request));
            logger.info("Member {} added to group {} by {}", added.getLogin(), groupId, AdminConfig.userLogin(request.getSession(false)));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("groupId", groupId);
            body.put("login", added.getLogin());
            body.put("name", added.getName());
            write(response, HttpServletResponse.SC_OK, body);
        } catch (DomainException | GitHubApiException e) {
            fail(response, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Long groupId = authorize(request, response);
        if (groupId == null) return;

        String login = trim(request.getParameter("login"));
        try {
            membership.remove(groupId, login);
            logger.info("Member {} removed from group {} by {}", login, groupId, AdminConfig.userLogin(request.getSession(false)));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("removed", true);
            write(response, HttpServletResponse.SC_OK, body);
        } catch (DomainException e) {
            fail(response, e);
        }
    }

    /**
     * Edits a member (name and login). Parameters in the query string: groupId, login (current), newLogin (optional,
     * default = login) and name (optional, default = final login). A new login is validated on GitHub and stored with
     * its spelling; the manual identity associations of the old login move to the new one.
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Long groupId = authorize(request, response);
        if (groupId == null) return;

        try {
            MemberChange edited = membership.edit(groupId, trim(request.getParameter("login")), request.getParameter("newLogin"),
                    request.getParameter("name"), accessToken(request));
            logger.info("Member {} of group {} edited to {} by {}", edited.getPreviousLogin(), groupId, edited.getLogin(),
                    AdminConfig.userLogin(request.getSession(false)));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("groupId", groupId);
            body.put("login", edited.getLogin());
            body.put("name", edited.getName());
            body.put("previousLogin", edited.getPreviousLogin());
            write(response, HttpServletResponse.SC_OK, body);
        } catch (DomainException | GitHubApiException e) {
            fail(response, e);
        }
    }

    /** CSRF header, administrator and group id; returns the group id, or null after writing the error response. */
    private Long authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!"XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            write(response, HttpServletResponse.SC_FORBIDDEN, error("forbidden", "Missing X-Requested-With header."));
            return null;
        }

        if (!AdminConfig.isAdmin(request.getSession(false))) {
            logger.warn("Member change refused for {}", AdminConfig.userLogin(request.getSession(false)));
            write(response, HttpServletResponse.SC_FORBIDDEN, error("FORBIDDEN", FORBIDDEN));
            return null;
        }

        try {
            return Long.parseLong(trim(request.getParameter("groupId")));
        } catch (NumberFormatException | NullPointerException e) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "The 'groupId' parameter is required."));
            return null;
        }
    }

    private void fail(HttpServletResponse response, RuntimeException e) throws IOException {
        if (e instanceof GitHubApiException) {
            GitHubApiException g = (GitHubApiException) e;
            write(response, g.getHttpStatus(), error(g.getErrorCode(), g.getMessage()));
        } else {
            DomainException d = (DomainException) e;
            write(response, DomainErrors.status(d), error(d.getCode(), d.getMessage()));
        }
    }

    private static String accessToken(HttpServletRequest request) {
        Object token = request.getSession().getAttribute("accessToken");
        return token instanceof String ? (String) token : null;
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return body;
    }

    private void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(gson.toJson(body));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
