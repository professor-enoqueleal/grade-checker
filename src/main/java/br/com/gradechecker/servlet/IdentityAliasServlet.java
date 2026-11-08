package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GradingConfig;
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
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

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
    static final Pattern IDENTITY_KEY = Pattern.compile("^(email|name):\\S.{0,500}$");

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final IdentityAliasRepository repository;
    private final Function<Long, List<String>> roster; // group id -> members' GitHub usernames
    private final GradingConfig grading;
    private final java.util.function.LongPredicate activeGroup;   // archived groups are missing (group-management)
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public IdentityAliasServlet() {
        this(new IdentityAliasRepository(), new MemberRepository()::findUsernamesByGroupId, GradingConfig.fromClasspath(),
                id -> new br.com.gradechecker.repository.GroupRepository().getGroupDetail(id) != null);
    }

    public IdentityAliasServlet(IdentityAliasRepository repository, Function<Long, List<String>> roster, GradingConfig grading) {
        this(repository, roster, grading, id -> true);
    }

    public IdentityAliasServlet(IdentityAliasRepository repository, Function<Long, List<String>> roster, GradingConfig grading,
                                java.util.function.LongPredicate activeGroup) {
        this.repository = repository;
        this.roster = roster;
        this.grading = grading;
        this.activeGroup = activeGroup;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Long groupId = authorize(request, response);
        if (groupId == null) return;

        String identityKey = request.getParameter("identityKey");
        String login = trim(request.getParameter("login"));

        String student = studentOf(groupId, login);
        if (student == null) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("invalid_login", "The login must be a student of this group."));
            return;
        }

        String admin = AdminConfig.userLogin(request.getSession(false));
        repository.save(groupId, identityKey, student, admin);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", groupId);
        body.put("identityKey", identityKey);
        body.put("login", student);
        write(response, HttpServletResponse.SC_OK, body);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Long groupId = authorize(request, response);
        if (groupId == null) return;

        if (!repository.delete(groupId, request.getParameter("identityKey"))) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", "This identity is not linked."));
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("removed", true);
        write(response, HttpServletResponse.SC_OK, body);
    }

    /** Common checks; returns the group id, or null after writing the error response. */
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

        Long groupId;
        try {
            groupId = Long.parseLong(trim(request.getParameter("groupId")));
        } catch (NumberFormatException | NullPointerException e) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "The 'groupId' parameter is required."));
            return null;
        }

        if (!activeGroup.test(groupId)) {
            write(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", "Group not found."));
            return null;
        }

        String identityKey = request.getParameter("identityKey");
        if (identityKey == null || !IDENTITY_KEY.matcher(identityKey).matches()) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, error("invalid_identity", "Invalid identity key."));
            return null;
        }

        return groupId;
    }

    /** Spelling of the login as registered in the group; null when it is not a student of the group. */
    private String studentOf(long groupId, String login) {
        if (login == null || login.isEmpty()) return null;
        String wanted = login.toLowerCase(Locale.ROOT);
        for (String member : roster.apply(groupId)) {
            if (member != null && member.trim().toLowerCase(Locale.ROOT).equals(wanted) && grading.isStudentLogin(member.trim())) {
                return member.trim();
            }
        }
        return null;
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
