package br.com.gradechecker.servlet;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.model.ContributorStatsCommand;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.service.ContributorsService;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet("/contributors-stats")
public class ContributorsStatServlet extends HttpServlet {

    static final String DEFAULT_SINCE = "2024-01-01T00:01:00Z";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final ContributorsService contributorsService;
    private final Gson gson;

    public ContributorsStatServlet() {
        this(new ContributorsService());
    }

    public ContributorsStatServlet(ContributorsService contributorsService) {
        this.contributorsService = contributorsService;
        this.gson = new Gson();
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

        CommitRequestBody commitRequestBody = getParameters(request, owner, repo);

        try {

            ContributionsReport report = contributorsService.getContributionsReport(commitRequestBody);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("message", "Contributions Dashboard");
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

    private CommitRequestBody getParameters(HttpServletRequest httpServletRequest, String owner, String repo) {

        String accessToken = (String) httpServletRequest.getSession().getAttribute("accessToken");

        String author = httpServletRequest.getParameter("author");
        String groupId = httpServletRequest.getParameter("groupId");
        String since = isBlank(httpServletRequest.getParameter("date-since")) ? DEFAULT_SINCE : httpServletRequest.getParameter("date-since");
        String until = isBlank(httpServletRequest.getParameter("date-until")) ? defaultUntil(Instant.now()) : httpServletRequest.getParameter("date-until");

        return new CommitRequestBody(repo, owner, author, since, until, accessToken, groupId);

    }

    /** Now rounded up to the next minute: stable cache key within the minute, no recent commit left out. */
    static String defaultUntil(Instant now) {

        Instant truncated = now.truncatedTo(ChronoUnit.MINUTES);

        return (truncated.equals(now) ? truncated : truncated.plus(1, ChronoUnit.MINUTES)).toString();

    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
