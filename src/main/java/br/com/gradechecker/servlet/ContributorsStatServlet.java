package br.com.gradechecker.servlet;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.service.ContributorsService;
import br.com.gradechecker.service.PeriodResolver;
import br.com.gradechecker.service.PeriodResolver.ResolvedPeriod;
import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import br.com.gradechecker.repository.MemberRepository;
import br.com.gradechecker.service.GradeCalculator;
import br.com.gradechecker.service.GradeCalculator.Indicators;
import br.com.gradechecker.service.GradeCalculator.StudentGrade;
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
import java.util.*;
import java.util.function.Function;

@WebServlet("/contributors-stats")
public class ContributorsStatServlet extends HttpServlet {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final ContributorsService contributorsService;
    private final PeriodResolver periodResolver;
    private final Function<Long, List<String>> roster;     // group id -> members' GitHub usernames
    private final GradingConfig grading;
    private final Gson gson;

    public ContributorsStatServlet() {
        this(new ContributorsService(), new PeriodResolver(), new MemberRepository()::findUsernamesByGroupId, GradingConfig.fromClasspath());
    }

    public ContributorsStatServlet(ContributorsService contributorsService, PeriodResolver periodResolver) {
        this(contributorsService, periodResolver, groupId -> Collections.emptyList(), GradingConfig.defaults());
    }

    public ContributorsStatServlet(ContributorsService contributorsService, PeriodResolver periodResolver,
                                   Function<Long, List<String>> roster, GradingConfig grading) {
        this.contributorsService = contributorsService;
        this.periodResolver = periodResolver;
        this.roster = roster;
        this.grading = grading;
        this.gson = new GsonBuilder().serializeNulls().create(); // "since": null for All
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String owner = request.getParameter("owner");
        String repo = request.getParameter("repo");

        if (isBlank(owner) || isBlank(repo)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "bad_request", "The 'owner' and 'repo' parameters are required.");
            return;
        }

        ResolvedPeriod period = periodResolver.resolve(
                request.getParameter("period"), request.getParameter("date-since"), request.getParameter("date-until"));

        if (period.getError() != null) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_period", period.getError());
            return;
        }

        String accessToken = (String) request.getSession().getAttribute("accessToken");

        CommitRequestBody commitRequestBody = new CommitRequestBody(
                repo, owner, null, period.getSinceIso(), period.getUntilIso(), accessToken, request.getParameter("groupId"));

        try {

            ContributionsReport report = contributorsService.getContributionsReport(commitRequestBody);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("message", "Contributions Dashboard");
            content.put("period", period.getKey());
            content.put("periodText", periodResolver.describe(period));
            content.put("since", report.getSince());
            content.put("until", report.getUntil());
            content.put("totalCommits", report.getTotalCommits());
            content.put("truncated", report.isTruncated());

            List<Map<String, Object>> rows = toRows(report.getConsolidatedStats());
            Map<String, Object> gradingInfo = addGrades(period, commitRequestBody, report, rows, rosterOf(request.getParameter("groupId")));
            if (gradingInfo != null) content.put("grading", gradingInfo);

            content.put("stats", Collections.singletonMap("consolidatedStats", rows));

            response.getWriter().write(gson.toJson(content));

        } catch (GitHubApiException e) {

            logger.warn("Contributions dashboard failed for {}/{}: {} ({})", owner, repo, e.getErrorCode(), e.getMessage());
            writeError(response, e.getHttpStatus(), e.getErrorCode(), e.getMessage());

        }

    }

    // ---------------------------------------------------------------- grades (openspec capability milestone-grading)

    private List<String> rosterOf(String groupId) {
        if (isBlank(groupId)) return null;
        try {
            return roster.apply(Long.parseLong(groupId.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Rows as maps: the cached report objects are never modified. */
    private static List<Map<String, Object>> toRows(List<ContributorStatsResponseBody> stats) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ContributorStatsResponseBody s : stats) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("author", s.getAuthor());
            row.put("commits", s.getCommits());
            row.put("totalAdditions", s.getTotalAdditions());
            row.put("totalDeletions", s.getTotalDeletions());
            row.put("percentage", s.getPercentage());
            row.put("linked", s.isLinked());
            row.put("gitIdentities", s.getGitIdentities());
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Indicators> indicators(List<ContributorStatsResponseBody> stats) {
        Map<String, Indicators> byAuthor = new LinkedHashMap<>();
        for (ContributorStatsResponseBody s : stats) {
            byAuthor.put(s.getAuthor(), new Indicators(s.getEffectiveLines(), s.getActiveDays(), s.getRelevantCommits(),
                    s.getCommits(), s.getTotalAdditions() + s.getTotalDeletions()));
        }
        return byAuthor;
    }

    /** Adds grade fields to the rows and returns the "grading" block; null when there is no grade (custom period or no group). */
    private Map<String, Object> addGrades(ResolvedPeriod period, CommitRequestBody request, ContributionsReport report,
                                          List<Map<String, Object>> rows, List<String> members) {

        if (members == null || PeriodResolver.CUSTOM.equals(period.getKey())) return null;

        GradeCalculator calculator = new GradeCalculator(grading);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("maxPoints", grading.getMaxPoints());
        info.put("tolerance", grading.getTolerance());

        if (period.getKey().startsWith("m")) {
            GradeCalculator.Result result = calculator.calculate(indicators(report.getConsolidatedStats()), members);

            addMissingStudents(rows, result.students());
            for (Map<String, Object> row : rows) {
                StudentGrade g = result.of((String) row.get("author"));
                row.put("grade", g == null ? null : g.getGrade());
                row.put("gradeDetails", g == null ? null : details(g));
            }

            info.put("fairShare", result.getFairShare());
            info.put("students", result.students().size());
            info.put("excludedCommits", result.getExcludedCommits());
            info.put("excludedLines", result.getExcludedLines());
            info.put("provisional", periodResolver.isInProgress(period));
            return info;
        }

        // all: grades of every milestone already started, and their sum
        Map<String, Map<String, Double>> milestoneGrades = new HashMap<>();
        List<String> provisional = new ArrayList<>();
        Set<StudentGrade> anyStudents = new LinkedHashSet<>();
        int students = 0;

        for (int n = 1; n <= 3; n++) {
            String key = "m" + n;
            ResolvedPeriod milestone = periodResolver.resolve(key, null, null);
            if (milestone.getError() != null) continue; // not configured or not started: "—"

            CommitRequestBody milestoneRequest = new CommitRequestBody(request.getRepository(), request.getOwner(), null,
                    milestone.getSinceIso(), milestone.getUntilIso(), request.getAccessToken(), request.getGroupId());
            GradeCalculator.Result result = calculator.calculate(
                    indicators(contributorsService.getContributionsReport(milestoneRequest).getConsolidatedStats()), members);

            students = result.students().size();
            anyStudents.addAll(result.students());
            for (StudentGrade g : result.students()) {
                milestoneGrades.computeIfAbsent(g.getLogin().toLowerCase(Locale.ROOT), k -> new LinkedHashMap<>()).put(key, g.getGrade());
            }
            if (periodResolver.isInProgress(milestone)) provisional.add(key);
        }

        addMissingStudents(rows, anyStudents);
        for (Map<String, Object> row : rows) {
            String login = ((String) row.get("author")).toLowerCase(Locale.ROOT);
            Map<String, Double> grades = milestoneGrades.get(login);
            if (grades == null) {
                row.put("milestoneGrades", null);
                row.put("total", null);
                continue;
            }
            Map<String, Double> all = new LinkedHashMap<>();
            double total = 0;
            for (int n = 1; n <= 3; n++) {
                Double g = grades.get("m" + n);
                all.put("m" + n, g);
                if (g != null) total += g;
            }
            row.put("milestoneGrades", all);
            row.put("total", GradeCalculator.round(total));
        }

        info.put("students", students);
        info.put("provisionalMilestones", provisional);
        return info;
    }

    /** Students without commits in the period still appear (zeros), so that a 0.00 grade is visible. */
    private static void addMissingStudents(List<Map<String, Object>> rows, Collection<StudentGrade> students) {
        Set<String> present = new HashSet<>();
        for (Map<String, Object> row : rows) present.add(((String) row.get("author")).toLowerCase(Locale.ROOT));
        for (StudentGrade g : students) {
            if (present.add(g.getLogin().toLowerCase(Locale.ROOT))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("author", g.getLogin());
                row.put("commits", 0);
                row.put("totalAdditions", 0);
                row.put("totalDeletions", 0);
                row.put("percentage", 0.0);
                row.put("linked", true);
                row.put("gitIdentities", Collections.emptyList());
                rows.add(row);
            }
        }
    }

    private static Map<String, Object> details(StudentGrade g) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("effectiveLines", g.getEffectiveLines());
        d.put("activeDays", g.getActiveDays());
        d.put("relevantCommits", g.getRelevantCommits());
        d.put("indexShare", g.getIndexShare());
        d.put("ratio", g.getRatio());
        return d;
    }

    private void writeError(HttpServletResponse response, int status, String error, String message) throws IOException {

        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);

        response.setStatus(status);
        response.getWriter().write(gson.toJson(body));

    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
