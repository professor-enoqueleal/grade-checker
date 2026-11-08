package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.model.GitHubRepository;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.GroupRepositoryUrl;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.GroupRepository;
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
 * The repository is validated on GitHub with the session token and stored with the spelling returned by GitHub.
 */
@WebServlet(urlPatterns = {"/groups/api", "/groups/api/archive", "/groups/api/restore"})
public class GroupsApiServlet extends HttpServlet {

    static final String FORBIDDEN = "Only administrators can manage groups.";
    static final String INVALID_REPOSITORY = "Repository not found or not accessible with this token.";
    static final String ALREADY_REGISTERED = "This repository is already registered.";
    static final String ALREADY_ARCHIVED = "This repository is already registered in an archived group. Restore it instead.";
    static final String NAME_REQUIRED = "The group name is required.";
    static final String NOT_FOUND = "Group not found.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final GroupRepository groups;
    private final GitHubProxy gitHub;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public GroupsApiServlet() {
        this(new GroupRepository(), new GitHubProxy());
    }

    public GroupsApiServlet(GroupRepository groups, GitHubProxy gitHub) {
        this.groups = groups;
        this.gitHub = gitHub;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!authorized(request, response)) return;

        String path = request.getServletPath();
        if ("/groups/api/archive".equals(path) || "/groups/api/restore".equals(path)) {
            archiveOrRestore(request, response, "/groups/api/archive".equals(path));
            return;
        }

        Group group = validated(request, response, null);
        if (group == null) return;

        long id = groups.create(group);
        logger.info("Group {} ({}) created by {}", id, group.getRepositoryUrl(), AdminConfig.userLogin(request.getSession(false)));
        group.setId(id);
        write(response, HttpServletResponse.SC_OK, body(group));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!authorized(request, response)) return;
        if (!"/groups/api".equals(request.getServletPath())) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", NOT_FOUND));
            return;
        }

        Long id = id(request, response);
        if (id == null) return;
        if (groups.getGroupDetail(id) == null) {               // missing or archived
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", NOT_FOUND));
            return;
        }

        Group group = validated(request, response, id);
        if (group == null) return;
        group.setId(id);

        if (!groups.update(group)) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", NOT_FOUND));
            return;
        }
        logger.info("Group {} edited ({}) by {}", id, group.getRepositoryUrl(), AdminConfig.userLogin(request.getSession(false)));
        write(response, HttpServletResponse.SC_OK, body(group));
    }

    private void archiveOrRestore(HttpServletRequest request, HttpServletResponse response, boolean archive) throws IOException {

        Long id = id(request, response);
        if (id == null) return;

        boolean changed = archive ? groups.archive(id) : groups.restore(id);
        if (!changed) {                                        // missing, or already in the requested state
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", NOT_FOUND));
            return;
        }
        logger.info("Group {} {} by {}", id, archive ? "archived" : "restored", AdminConfig.userLogin(request.getSession(false)));
        write(response, HttpServletResponse.SC_OK, body(groups.findAnyById(id)));
    }

    /**
     * Name and URL checks, duplicate check and GitHub validation; returns the group to store (spelling of GitHub), or
     * null after writing the error response.
     */
    private Group validated(HttpServletRequest request, HttpServletResponse response, Long editedId) throws IOException {

        String name = trim(request.getParameter("name"));
        if (name == null || name.isEmpty()) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", NAME_REQUIRED));
            return null;
        }
        if (name.length() > 255) name = name.substring(0, 255);

        GroupRepositoryUrl url = GroupRepositoryUrl.parse(request.getParameter("repositoryUrl"));
        if (url == null) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", GroupRepositoryUrl.INVALID));
            return null;
        }

        if (duplicate(request.getParameter("repositoryUrl"), editedId, response)) return null;

        GitHubRepository repository;
        try {
            repository = gitHub.getRepository(url.getOwner(), url.getRepository(), accessToken(request));
        } catch (GitHubApiException e) {
            write(response, e.getHttpStatus(), error(e.getErrorCode(), e.getMessage()));
            return null;
        }
        if (repository == null) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("invalid_repository", INVALID_REPOSITORY));
            return null;
        }

        // GitHub may answer with another spelling (or a renamed repository): check again with its URL
        if (!GroupRepositoryUrl.normalize(repository.getHtmlUrl()).equals(GroupRepositoryUrl.normalize(request.getParameter("repositoryUrl")))
                && duplicate(repository.getHtmlUrl(), editedId, response)) {
            return null;
        }

        Group group = new Group(name, repository.getHtmlUrl(), repository.getOwnerLogin(), repository.getName());
        group.setRepositoryPrivate(repository.isPrivateRepository());
        return group;
    }

    /** Writes 409 and returns true when another group (active or archived) uses the repository. */
    private boolean duplicate(String repositoryUrl, Long editedId, HttpServletResponse response) throws IOException {
        Group other = groups.findByRepository(repositoryUrl, editedId);
        if (other == null) return false;
        write(response, HttpServletResponse.SC_CONFLICT,
                error("already_registered", other.isArchived() ? ALREADY_ARCHIVED : ALREADY_REGISTERED));
        return true;
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
