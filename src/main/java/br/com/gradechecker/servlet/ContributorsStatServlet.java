package br.com.gradechecker.servlet;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributorStats;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import br.com.gradechecker.model.ContributorStatsCommand;
import br.com.gradechecker.service.ContributorsService;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/contributors-stats")
public class ContributorsStatServlet extends HttpServlet {

    private final ContributorsService contributorsService;
    private final Gson gson;

    public ContributorsStatServlet() {
        this.contributorsService = new ContributorsService();
        this.gson = new Gson();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        CommitRequestBody commitRequestBody = getParameters(request);

        List<ContributorStats> contributorStatsList = contributorsService.getCommitsDetails(commitRequestBody);

        Map<String, Object> content = new HashMap<>();
        content.put("message", "Contributions Dashboard");

        ContributorStatsCommand responseBody = new ContributorStatsCommand(consolidateStats(contributorStatsList));

        content.put("stats", responseBody);

        String jsonResponse = gson.toJson(content);

        response.getWriter().write(jsonResponse);

    }

    private List<ContributorStatsResponseBody> consolidateStats(List<ContributorStats> contributorStatsList) {

        List<ContributorStatsResponseBody> body = new ArrayList<>();

        for (ContributorStats stat : contributorStatsList) {

            String author = stat.getAuthor().getLogin();
            int additions = stat.getTotalAdditions();
            int deletions = stat.getTotalDeletions();

            ContributorStatsResponseBody committerStat = new ContributorStatsResponseBody(author, additions, deletions);

            body.add(committerStat);

        }

        return body;

    }

    private CommitRequestBody getParameters(HttpServletRequest httpServletRequest) {

        String accessToken = (String) httpServletRequest.getSession().getAttribute("accessToken");

        String repository = httpServletRequest.getParameter("repo");
        String owner = httpServletRequest.getParameter("owner");
        String author = httpServletRequest.getParameter("author");
        String groupId = httpServletRequest.getParameter("groupId");
        String since = null == httpServletRequest.getParameter("date-since") ? "2024-01-01T00:01:00Z" : httpServletRequest.getParameter("date-since");
        String until = null == httpServletRequest.getParameter("date-until") ? Instant.now().toString() : httpServletRequest.getParameter("date-until");

        return new CommitRequestBody(repository, owner, author, since, until, accessToken, groupId);

    }

}
