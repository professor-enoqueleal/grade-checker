package br.com.gradechecker.servlet;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.model.ContributorStatsCommand;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.service.ContributorsService;
import br.com.gradechecker.service.PeriodResolver;
import br.com.gradechecker.service.PeriodResolver.ResolvedPeriod;
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
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet("/contributors-stats")
public class ContributorsStatServlet extends HttpServlet {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final ContributorsService contributorsService;
    private final PeriodResolver periodResolver;
    private final Gson gson;

    public ContributorsStatServlet() {
        this(new ContributorsService(), new PeriodResolver());
    }

    public ContributorsStatServlet(ContributorsService contributorsService, PeriodResolver periodResolver) {
        this.contributorsService = contributorsService;
        this.periodResolver = periodResolver;
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
            content.put("stats", new ContributorStatsCommand(report.getConsolidatedStats()));

            response.getWriter().write(gson.toJson(content));

        } catch (GitHubApiException e) {

            logger.warn("Contributions dashboard failed for {}/{}: {} ({})", owner, repo, e.getErrorCode(), e.getMessage());
            writeError(response, e.getHttpStatus(), e.getErrorCode(), e.getMessage());

        }

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
