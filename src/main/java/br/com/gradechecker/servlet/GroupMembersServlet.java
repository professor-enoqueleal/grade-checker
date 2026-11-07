package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.model.Contributor;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.IdentityAliasRepository;
import br.com.gradechecker.repository.MemberRepository;
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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Adds (POST), edits (PUT) and removes (DELETE) members of a group (openspec capability group-members). Administrators only.
 *
 * Parameters: groupId, login, name (POST only, optional; default = login), manual (POST only: "true" for the
 * "Add a new member" form, false for "Mark as member" on a contributor card).
 * Requests must carry "X-Requested-With: XMLHttpRequest" (sent by the page), which a cross-site form cannot set:
 * protection against CSRF on these grade-changing calls.
 */
@WebServlet("/group-members")
public class GroupMembersServlet extends HttpServlet {

    static final String FORBIDDEN = "Only administrators can manage members.";
    static final String HAS_ALIASES = "Unlink the identities linked to this student before removing them.";
    static final String INVALID_LOGIN = "This GitHub user does not exist.";
    static final String ALREADY_MEMBER = "This user is already a member of the group.";

    /** GitHub logins (letters, digits, hyphens); "[", "]", "." and "_" too, so that old members such as "x[bot]" can be removed. */
    private static final Pattern LOGIN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._\\-\\[\\]]{0,254}$");

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final Supplier<List<Group>> groups;
    private final MemberRepository members;
    private final IdentityAliasRepository aliases;
    private final GitHubProxy gitHub;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public GroupMembersServlet() {
        this(new GroupRepository()::findAll, new MemberRepository(), new IdentityAliasRepository(), new GitHubProxy());
    }

    public GroupMembersServlet(Supplier<List<Group>> groups, MemberRepository members, IdentityAliasRepository aliases, GitHubProxy gitHub) {
        this.groups = groups;
        this.members = members;
        this.aliases = aliases;
        this.gitHub = gitHub;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Target target = authorize(request, response);
        if (target == null) return;

        Contributor user;
        try {
            user = gitHub.getUser(target.login, accessToken(request));
        } catch (GitHubApiException e) {
            write(response, e.getHttpStatus(), error(e.getErrorCode(), e.getMessage()));
            return;
        }
        if (user == null) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("invalid_login", INVALID_LOGIN));
            return;
        }

        String login = user.getLogin();                         // spelling returned by GitHub
        String name = trim(request.getParameter("name"));
        if (name == null || name.isEmpty()) name = login;
        if (name.length() > 255) name = name.substring(0, 255);
        boolean manual = "true".equalsIgnoreCase(request.getParameter("manual"));

        if (!members.addIfAbsent(target.groupId, login, name, manual)) {
            write(response, HttpServletResponse.SC_CONFLICT, error("already_member", ALREADY_MEMBER));
            return;
        }

        logger.info("Member {} added to group {} by {}", login, target.groupId, AdminConfig.userLogin(request.getSession(false)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", target.groupId);
        body.put("login", login);
        body.put("name", name);
        write(response, HttpServletResponse.SC_OK, body);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Target target = authorize(request, response);
        if (target == null) return;

        if (!members.isMember(target.groupId, target.login)) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", "This user is not a member of the group."));
            return;
        }

        if (aliases.existsForLogin(target.groupId, target.login)) {
            write(response, HttpServletResponse.SC_CONFLICT, error("has_aliases", HAS_ALIASES));
            return;
        }

        members.delete(target.groupId, target.login);
        logger.info("Member {} removed from group {} by {}", target.login, target.groupId, AdminConfig.userLogin(request.getSession(false)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("removed", true);
        write(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * Edits a member (name and login). Parameters in the query string: groupId, login (current), newLogin (optional,
     * default = login) and name (optional, default = final login). A new login is validated on GitHub and stored with
     * its spelling; the manual identity associations of the old login move to the new one.
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Target target = authorize(request, response);
        if (target == null) return;

        String newLogin = trim(request.getParameter("newLogin"));
        if (newLogin == null || newLogin.isEmpty()) newLogin = target.login;
        if (!LOGIN.matcher(newLogin).matches()) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "A valid GitHub login is required."));
            return;
        }

        if (!members.isMember(target.groupId, target.login)) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", "This user is not a member of the group."));
            return;
        }

        if (!newLogin.equals(target.login)) {
            Contributor user;
            try {
                user = gitHub.getUser(newLogin, accessToken(request));
            } catch (GitHubApiException e) {
                write(response, e.getHttpStatus(), error(e.getErrorCode(), e.getMessage()));
                return;
            }
            if (user == null) {
                write(response, HttpServletResponse.SC_BAD_REQUEST, error("invalid_login", INVALID_LOGIN));
                return;
            }
            newLogin = user.getLogin();                         // spelling returned by GitHub
        }

        String name = trim(request.getParameter("name"));
        if (name == null || name.isEmpty()) name = newLogin;
        if (name.length() > 255) name = name.substring(0, 255);

        switch (members.rename(target.groupId, target.login, newLogin, name)) {
            case NOT_MEMBER:
                write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", "This user is not a member of the group."));
                return;
            case ALREADY_MEMBER:
                write(response, HttpServletResponse.SC_CONFLICT, error("already_member", ALREADY_MEMBER));
                return;
            default:
                break;
        }

        logger.info("Member {} of group {} edited to {} by {}", target.login, target.groupId, newLogin,
                AdminConfig.userLogin(request.getSession(false)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", target.groupId);
        body.put("login", newLogin);
        body.put("name", name);
        body.put("previousLogin", target.login);
        write(response, HttpServletResponse.SC_OK, body);
    }

    private static final class Target {
        final long groupId;
        final String login;

        Target(long groupId, String login) {
            this.groupId = groupId;
            this.login = login;
        }
    }

    /** Common checks; returns the group and login, or null after writing the error response. */
    private Target authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!"XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            write(response, HttpServletResponse.SC_FORBIDDEN, error("forbidden", "Missing X-Requested-With header."));
            return null;
        }

        if (!AdminConfig.isAdmin(request.getSession(false))) {
            logger.warn("Member change refused for {}", AdminConfig.userLogin(request.getSession(false)));
            write(response, HttpServletResponse.SC_FORBIDDEN, error("FORBIDDEN", FORBIDDEN));
            return null;
        }

        long groupId;
        try {
            groupId = Long.parseLong(trim(request.getParameter("groupId")));
        } catch (NumberFormatException | NullPointerException e) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "The 'groupId' parameter is required."));
            return null;
        }

        String login = trim(request.getParameter("login"));
        if (login == null || !LOGIN.matcher(login).matches()) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "A valid GitHub login is required."));
            return null;
        }

        if (groups.get().stream().noneMatch(g -> g.getId() == groupId)) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", "Group not found."));
            return null;
        }

        return new Target(groupId, login);
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
