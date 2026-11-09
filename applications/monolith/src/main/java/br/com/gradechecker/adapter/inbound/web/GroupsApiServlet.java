package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.GroupManagement;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.group.Group;

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
 * Groups API (openspec capability group-management), administrators only:
 * <ul>
 *   <li>{@code POST /groups/api} (name, repositoryUrl): creates a group;</li>
 *   <li>{@code PUT /groups/api?id=&name=&repositoryUrl=}: edits an active group (members and aliases stay);</li>
 *   <li>{@code POST /groups/api/archive?id=} and {@code POST /groups/api/restore?id=}.</li>
 * </ul>
 * Requests must carry "X-Requested-With: XMLHttpRequest" (sent by the page), which a cross-site form cannot set.
 * The rules (repository validated on GitHub with the session token, stored with the spelling of GitHub, one group per
 * repository) are those of the use case GroupManagement of grade-checker-core.
 */
@WebServlet(urlPatterns = {"/groups/api", "/groups/api/archive", "/groups/api/restore"})
public class GroupsApiServlet extends HttpServlet {

    static final String FORBIDDEN = "Only administrators can manage groups.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final GroupManagement groups;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public GroupsApiServlet() {
        this(AppContext.get().groups);
    }

    public GroupsApiServlet(GroupManagement groups) {
        this.groups = groups;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!authorized(request, response)) return;

        String path = request.getServletPath();
        if ("/groups/api/archive".equals(path) || "/groups/api/restore".equals(path)) {
            archiveOrRestore(request, response, "/groups/api/archive".equals(path));
            return;
        }

        try {
            Group group = groups.create(request.getParameter("name"), request.getParameter("repositoryUrl"), accessToken(request));
            logger.info("Group {} ({}) created by {}", group.getId(), group.getRepositoryUrl(), AdminConfig.userLogin(request.getSession(false)));
            write(response, HttpServletResponse.SC_OK, body(group));
        } catch (DomainException | GitHubApiException e) {
            fail(response, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!authorized(request, response)) return;
        if (!"/groups/api".equals(request.getServletPath())) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", GroupManagement.GROUP_NOT_FOUND));
            return;
        }

        Long id = id(request, response);
        if (id == null) return;

        try {
            Group group = groups.update(id, request.getParameter("name"), request.getParameter("repositoryUrl"), accessToken(request));
            logger.info("Group {} edited ({}) by {}", id, group.getRepositoryUrl(), AdminConfig.userLogin(request.getSession(false)));
            write(response, HttpServletResponse.SC_OK, body(group));
        } catch (DomainException | GitHubApiException e) {
            fail(response, e);
        }
    }

    private void archiveOrRestore(HttpServletRequest request, HttpServletResponse response, boolean archive) throws IOException {

        Long id = id(request, response);
        if (id == null) return;

        try {
            Group group = archive ? groups.archive(id) : groups.restore(id);
            logger.info("Group {} {} by {}", id, archive ? "archived" : "restored", AdminConfig.userLogin(request.getSession(false)));
            write(response, HttpServletResponse.SC_OK, body(group));
        } catch (DomainException e) {
            fail(response, e);
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

    private boolean authorized(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!"XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            write(response, HttpServletResponse.SC_FORBIDDEN, error("forbidden", "Missing X-Requested-With header."));
            return false;
        }
        if (!AdminConfig.isAdmin(request.getSession(false))) {
            logger.warn("Group change refused for {}", AdminConfig.userLogin(request.getSession(false)));
            write(response, HttpServletResponse.SC_FORBIDDEN, error("FORBIDDEN", FORBIDDEN));
            return false;
        }
        return true;
    }

    private Long id(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return Long.parseLong(trim(request.getParameter("id")));
        } catch (NumberFormatException | NullPointerException e) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "The 'id' parameter is required."));
            return null;
        }
    }

    private static Map<String, Object> body(Group group) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", group.getId());
        body.put("name", group.getName());
        body.put("repositoryUrl", group.getRepositoryUrl());
        body.put("owner", group.getOwner());
        body.put("repository", group.getRepository());
        body.put("archived", group.isArchived());
        return body;
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
