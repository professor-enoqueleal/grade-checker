package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.MemberRepository;
import br.com.gradechecker.service.ContributorsService;
import br.com.gradechecker.service.GroupGradesService;
import br.com.gradechecker.service.GroupGradesService.GroupGrades;
import br.com.gradechecker.service.GroupGradesService.StudentGrades;
import br.com.gradechecker.service.PeriodResolver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class gradebook (openspec capability class-gradebook), administrators only:
 * <ul>
 *   <li>{@code GET /grades}: page with every group and its students (grades are loaded per group by the page);</li>
 *   <li>{@code GET /grades/group?id=N}: JSON with the grades of one group;</li>
 *   <li>{@code GET /grades.csv}: every grade as CSV (pt-BR: ";" and decimal comma).</li>
 * </ul>
 * The grades come from {@link GroupGradesService}, the same source of the "All" grades of /detail.
 */
@WebServlet(urlPatterns = {"/grades", "/grades/group", "/grades.csv"})
public class GradebookServlet extends HttpServlet {

    static final String FORBIDDEN = "Only administrators can see class grades.";
    static final String CSV_HEADER = "group;student;m1;m2;m3;total;provisional;error";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final Supplier<List<Group>> groups;
    private final Function<Long, List<String>> roster;   // group id -> members' GitHub usernames
    private final GradingConfig grading;
    private final GroupGradesService grades;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public GradebookServlet() {
        this(new GroupRepository()::findAll, new MemberRepository()::findUsernamesByGroupId, GradingConfig.fromClasspath(), null);
    }

    public GradebookServlet(Supplier<List<Group>> groups, Function<Long, List<String>> roster, GradingConfig grading,
                            GroupGradesService grades) {
        this.groups = groups;
        this.roster = roster;
        this.grading = grading;
        this.grades = grades != null ? grades : new GroupGradesService(new ContributorsService(), new PeriodResolver(), grading);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String path = request.getServletPath();

        // authorization first: nothing is read from the database or GitHub for other users
        if (!AdminConfig.isAdmin(request.getSession(false))) {
            logger.warn("Class grades refused for {} ({})", AdminConfig.userLogin(request.getSession(false)), path);
            if ("/grades".equals(path)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                request.setAttribute("forbidden", FORBIDDEN);
                request.getRequestDispatcher("/grades.jsp").forward(request, response);
            } else {
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, error("FORBIDDEN", FORBIDDEN));
            }
            return;
        }

        switch (path) {
            case "/grades/group":
                group(request, response);
                break;
            case "/grades.csv":
                csv(request, response);
                break;
            default:
                page(request, response);
        }
    }

    // ---------------------------------------------------------------- page

    /** One group of the page: its students (sorted by login), or none ("No students registered"). */
    public static final class GroupRow {
        private final long id;
        private final String name;
        private final List<String> students;

        GroupRow(long id, String name, List<String> students) {
            this.id = id;
            this.name = name;
            this.students = students;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public List<String> getStudents() { return students; }
    }

    private void page(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        List<GroupRow> rows = new ArrayList<>();
        for (Group g : sortedGroups()) rows.add(new GroupRow(g.getId(), g.getName(), studentsOf(g.getId())));
        request.setAttribute("groups", rows);
        request.getRequestDispatcher("/grades.jsp").forward(request, response);
    }

    // ---------------------------------------------------------------- one group (JSON)

    private void group(HttpServletRequest request, HttpServletResponse response) throws IOException {

        long id;
        try {
            id = Long.parseLong(request.getParameter("id").trim());
        } catch (NumberFormatException | NullPointerException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, error("bad_request", "The 'id' parameter is required."));
            return;
        }

        Group group = groups.get().stream().filter(g -> g.getId() == id).findFirst().orElse(null);
        if (group == null) {
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, error("not_found", "Group not found."));
            return;
        }

        List<String> students = studentsOf(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", id);

        if (students.isEmpty()) {
            body.put("students", Collections.emptyList());
            body.put("provisionalMilestones", Collections.emptyList());
            body.put("excludedCommits", 0);
            writeJson(response, HttpServletResponse.SC_OK, body);
            return;
        }

        try {
            GroupGrades result = grades.of(id, group.getOwner(), group.getRepository(), accessToken(request), students);
            List<Map<String, Object>> list = new ArrayList<>();
            for (StudentGrades s : result.getStudents()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("login", s.getLogin());
                for (String key : GroupGradesService.MILESTONES) item.put(key, s.get(key));
                item.put("total", s.getTotal());
                list.add(item);
            }
            body.put("students", list);
            body.put("provisionalMilestones", result.getProvisionalMilestones());
            body.put("excludedCommits", result.getExcludedCommits());
            writeJson(response, HttpServletResponse.SC_OK, body);
        } catch (GitHubApiException e) {
            logger.warn("Class grades failed for group {}: {} ({})", id, e.getErrorCode(), e.getMessage());
            writeJson(response, e.getHttpStatus(), error(e.getErrorCode(), e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("Class grades failed for group {}", id, e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error("error", "Could not calculate the grades of this group."));
        }
    }

    // ---------------------------------------------------------------- CSV

    private void csv(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String token = accessToken(request);
        StringBuilder out = new StringBuilder("﻿").append(CSV_HEADER).append("\r\n");

        for (Group group : sortedGroups()) {
            List<String> students = studentsOf(group.getId());
            if (students.isEmpty()) continue; // no student, no grade

            GroupGrades result = null;
            String error = null;
            try {
                result = grades.of(group.getId(), group.getOwner(), group.getRepository(), token, students);
            } catch (GitHubApiException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                logger.error("Class grades CSV failed for group {}", group.getId(), e);
                error = "Could not calculate the grades of this group.";
            }

            String provisional = result == null ? "" : String.join(",", result.getProvisionalMilestones());
            for (String login : students) {
                StudentGrades s = result == null ? null : result.of(login);
                List<String> cells = new ArrayList<>();
                cells.add(group.getName());
                cells.add(login);
                for (String key : GroupGradesService.MILESTONES) cells.add(s == null ? "" : decimal(s.get(key)));
                cells.add(s == null ? "" : decimal(s.getTotal()));
                cells.add(provisional);
                cells.add(error == null ? "" : error);
                out.append(csvLine(cells)).append("\r\n");
            }
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"grades.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write(out.toString());
        writer.flush();
    }

    static String csvLine(List<String> cells) {
        StringJoiner line = new StringJoiner(";");
        for (String c : cells) line.add(csvCell(c));
        return line.toString();
    }

    /** Neutralizes formulas (=, +, -, @, tab, CR) and quotes fields with ";", '"' or line breaks. */
    static String csvCell(String value) {
        if (value == null || value.isEmpty()) return "";
        String v = value;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') v = "'" + v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** 2 decimal places with a comma (pt-BR); empty when absent. */
    static String decimal(Double value) {
        return value == null ? "" : String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
    }

    // ---------------------------------------------------------------- helpers

    private List<Group> sortedGroups() {
        List<Group> list = new ArrayList<>(groups.get());
        list.sort(Comparator.comparing((Group g) -> g.getName() == null ? "" : g.getName().toLowerCase(Locale.ROOT))
                .thenComparingLong(Group::getId));
        return list;
    }

    /** Students of the group (members without bots and excluded logins), unique ignoring case, sorted by login. */
    private List<String> studentsOf(long groupId) {
        Map<String, String> unique = new TreeMap<>();
        for (String member : roster.apply(groupId)) {
            if (member != null && grading.isStudentLogin(member)) unique.putIfAbsent(member.trim().toLowerCase(Locale.ROOT), member.trim());
        }
        return new ArrayList<>(unique.values());
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

    private void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(gson.toJson(body));
    }

}
