package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Commit;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.CommitRow;
import br.com.gradechecker.model.CommitsPage;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.service.PeriodResolver;
import br.com.gradechecker.service.PeriodResolver.ResolvedPeriod;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Commits of one author in a period (milestone buttons, All or Custom).
 * See openspec/changes/filtro-por-marcos (capabilities milestone-period-filter and commit-history-page).
 */
@WebServlet("/contributions-detail")
public class ContributionsDetailServlet extends HttpServlet {

    private final GitHubProxy gitHubProxy;
    private final PeriodResolver periodResolver;

    public ContributionsDetailServlet() {
        this(new GitHubProxy(), new PeriodResolver());
    }

    public ContributionsDetailServlet(GitHubProxy gitHubProxy, PeriodResolver periodResolver) {
        this.gitHubProxy = gitHubProxy;
        this.periodResolver = periodResolver;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        buildBody(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        buildBody(request, response);
    }

    private void buildBody(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String author = request.getParameter("author");
        String owner = request.getParameter("owner");
        String repo = request.getParameter("repo");
        String groupId = request.getParameter("groupId");

        ResolvedPeriod period = periodResolver.resolve(
                request.getParameter("period"), request.getParameter("date-since"), request.getParameter("date-until"));

        if (period.getError() != null) {
            request.setAttribute("periodWarning", period.getError() + " Showing all history instead.");
            period = periodResolver.all();
        }

        String accessToken = (String) request.getSession().getAttribute("accessToken");

        CommitRequestBody commitRequestBody = new CommitRequestBody(
                repo, owner, author, period.getSinceIso(), period.getUntilIso(), accessToken, groupId);

        List<CommitRow> rows = Collections.emptyList();
        boolean truncated = false;

        try {

            CommitsPage page = gitHubProxy.getCommits(commitRequestBody);
            rows = toRows(page.getCommits());
            truncated = page.isTruncated();

        } catch (GitHubApiException e) {

            request.setAttribute("githubError", e.getMessage());

        }

        request.setAttribute("author", author);
        request.setAttribute("owner", owner);
        request.setAttribute("repo", repo);
        request.setAttribute("groupId", groupId);
        request.setAttribute("commits", rows);
        request.setAttribute("totalCommits", rows.size());
        request.setAttribute("truncated", truncated);
        request.setAttribute("periodKey", period.getKey());
        request.setAttribute("periodText", periodResolver.describe(period));
        request.setAttribute("periodOptions", periodResolver.options(period.getKey()));
        request.setAttribute("customSince", periodResolver.toInputValue(period.getSince()));
        request.setAttribute("customUntil", periodResolver.toInputValue(period.getUntil()));

        request.getRequestDispatcher("commits-detail.jsp").forward(request, response);

    }

    private List<CommitRow> toRows(List<Commit> commits) {

        List<CommitRow> rows = new ArrayList<>();

        for (Commit commit : commits) {

            String date = commit.getCommit() != null && commit.getCommit().getCommitter() != null
                    ? periodResolver.formatDateTime(commit.getCommit().getCommitter().getIsoDate()) : "";
            String message = commit.getCommit() != null ? commit.getCommit().getMessage() : "";
            String url = commit.getHtmlUrl() != null && commit.getHtmlUrl().startsWith("https://github.com/") ? commit.getHtmlUrl() : null;

            rows.add(new CommitRow(date, message, commit.getSha(), url));
        }

        return rows;
    }

}
