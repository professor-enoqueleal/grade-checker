package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.ContributionsQuery;
import br.com.gradechecker.core.application.ContributionsQuery.GradeKind;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.grading.GradeCalculator.StudentGrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contributions dashboard of a group (openspec capabilities contributions-dashboard and milestone-grading):
 * {@code GET /contributors-stats?groupId=&period=} (date-since/date-until for the custom period). The repository is the
 * one of the group, and the report and the grades come from the use case ContributionsQuery of grade-checker-core.
 */
@WebServlet("/contributors-stats")
public class ContributorsStatServlet extends HttpServlet {

    static final String GROUP_ID_REQUIRED = "The 'groupId' parameter is required.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final ContributionsQuery contributions;
    private final Gson gson = new GsonBuilder().serializeNulls().create(); // "since": null for All

    public ContributorsStatServlet() {
        this(AppContext.get().contributions);
    }

    public ContributorsStatServlet(ContributionsQuery contributions) {
        this.contributions = contributions;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        long groupId;
        try {
            groupId = Long.parseLong(request.getParameter("groupId").trim());
        } catch (NumberFormatException | NullPointerException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "bad_request", GROUP_ID_REQUIRED);
            return;
        }

        String accessToken = (String) request.getSession().getAttribute("accessToken");

        try {
            ContributionsQuery.Report report = contributions.report(groupId, request.getParameter("period"),
                    request.getParameter("date-since"), request.getParameter("date-until"), accessToken);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("message", "Contributions Dashboard");
            content.put("period", report.getPeriod().getKey());
            content.put("periodText", report.getPeriodText());
            content.put("since", report.getPeriod().getSinceIso());
            content.put("until", report.getPeriod().getUntilIso());
            content.put("totalCommits", report.getTotalCommits());
            content.put("truncated", report.isTruncated());
            if (report.getGrading() != null) content.put("grading", grading(report.getGrading()));
            content.put("stats", Collections.singletonMap("consolidatedStats", rows(report)));

            response.getWriter().write(gson.toJson(content));

        } catch (DomainException e) {
            writeError(response, DomainErrors.status(e), e.getCode(), e.getMessage());
        } catch (GitHubApiException e) {
            logger.warn("Contributions dashboard failed for group {}: {} ({})", groupId, e.getErrorCode(), e.getMessage());
            writeError(response, e.getHttpStatus(), e.getErrorCode(), e.getMessage());
        }
    }

    private static List<Map<String, Object>> rows(ContributionsQuery.Report report) {
        ContributionsQuery.Grading grades = report.getGrading();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ContributionsQuery.Row r : report.getRows()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("author", r.getAuthor());
            row.put("commits", r.getCommits());
            row.put("totalAdditions", r.getTotalAdditions());
            row.put("totalDeletions", r.getTotalDeletions());
            row.put("percentage", r.getPercentage());
            row.put("linked", r.isLinked());
            row.put("gitIdentities", r.getGitIdentities());
            row.put("identityKey", r.getIdentityKey());           // openspec capability identity-aliases
            row.put("manualIdentities", r.getManualIdentities());
            if (grades != null && grades.getKind() == GradeKind.MILESTONE) {
                StudentGrade g = r.getGrade();
                row.put("grade", g == null ? null : g.getGrade());
                row.put("gradeDetails", g == null ? null : details(g));
            } else if (grades != null) {
                row.put("milestoneGrades", r.getMilestoneGrades() == null ? null : r.getMilestoneGrades().getMilestones());
                row.put("total", r.getMilestoneGrades() == null ? null : r.getMilestoneGrades().getTotal());
            }
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Object> grading(ContributionsQuery.Grading g) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("maxPoints", g.getMaxPoints());
        info.put("tolerance", g.getTolerance());
        if (g.getKind() == GradeKind.MILESTONE) {
            info.put("fairShare", g.getFairShare());
            info.put("students", g.getStudents());
            info.put("excludedCommits", g.getExcludedCommits());
            info.put("excludedLines", g.getExcludedLines());
            info.put("provisional", g.isProvisional());
        } else {
            info.put("students", g.getStudents());
            info.put("provisionalMilestones", g.getProvisionalMilestones());
        }
        return info;
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
}
