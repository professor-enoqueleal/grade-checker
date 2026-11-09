package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.CommitHistoryQuery;
import br.com.gradechecker.core.attribution.AttributedCommit;
import br.com.gradechecker.core.attribution.AttributionSource;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.history.CommitActivity;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.period.PeriodResolver.ResolvedPeriod;
import br.com.gradechecker.core.view.CommitRow;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Commits counted for one author in a period (milestone buttons, All or Custom), with the same attribution as the
 * dashboard, including the manual associations of the group (openspec capability commit-history-page). The repository
 * is the one of the group ({@code groupId}); the commits come from the use case CommitHistoryQuery of grade-checker-core.
 */
@WebServlet("/contributions-detail")
public class ContributionsDetailServlet extends HttpServlet {

    static final String GROUP_ID_REQUIRED = "The 'groupId' parameter is required.";

    private final CommitHistoryQuery history;
    private final PeriodResolver periodResolver;
    private final Gson gson = new Gson();   // escapes < and >: safe inside <script type="application/json">

    public ContributionsDetailServlet() {
        this(AppContext.get().commitHistory, AppContext.get().periods);
    }

    public ContributionsDetailServlet(CommitHistoryQuery history, PeriodResolver periodResolver) {
        this.history = history;
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
        String groupId = request.getParameter("groupId");
        long id;
        try {
            id = Long.parseLong(groupId.trim());
        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, GROUP_ID_REQUIRED);
            return;
        }

        String period = request.getParameter("period");
        String since = request.getParameter("date-since");
        String until = request.getParameter("date-until");
        String accessToken = (String) request.getSession().getAttribute("accessToken");

        ResolvedPeriod shown;
        String periodWarning;
        List<CommitRow> rows = new ArrayList<>();
        List<String> alsoCounted = new ArrayList<>();
        boolean truncated = false;
        CommitActivity activity = null;

        try {
            CommitHistoryQuery.History h = history.history(id, author, period, since, until, accessToken);
            shown = h.getPeriod();
            periodWarning = h.getPeriodWarning();
            for (AttributedCommit commit : h.getCommits()) rows.add(toRow(commit));
            alsoCounted = h.getAlsoCounted();
            truncated = h.isTruncated();
            activity = h.getActivity();
        } catch (DomainException e) {                   // no author, or a missing or archived group
            response.sendError(DomainErrors.status(e), e.getMessage());
            return;
        } catch (GitHubApiException e) {                // the page is shown with the message of GitHub
            CommitHistoryQuery.ShownPeriod p = history.period(period, since, until);
            shown = p.getPeriod();
            periodWarning = p.getWarning();
            request.setAttribute("githubError", e.getMessage());
        }

        if (periodWarning != null) request.setAttribute("periodWarning", periodWarning);
        request.setAttribute("author", author);
        request.setAttribute("owner", request.getParameter("owner"));
        request.setAttribute("repo", request.getParameter("repo"));
        request.setAttribute("groupId", groupId);
        request.setAttribute("commits", rows);
        request.setAttribute("totalCommits", rows.size());
        request.setAttribute("alsoCounted", alsoCounted);
        request.setAttribute("truncated", truncated);
        request.setAttribute("periodKey", shown.getKey());
        request.setAttribute("periodText", periodResolver.describe(shown));
        request.setAttribute("periodOptions", periodResolver.options(shown.getKey()));
        request.setAttribute("customSince", periodResolver.toInputValue(shown.getSince()));
        request.setAttribute("customUntil", periodResolver.toInputValue(shown.getUntil()));

        // commit frequency chart (openspec capability commit-history-page): null without commits
        request.setAttribute("commitActivity", activity);
        request.setAttribute("commitActivityJson", activity == null ? null : gson.toJson(activity));

        request.getRequestDispatcher("commits-detail.jsp").forward(request, response);
    }

    private CommitRow toRow(AttributedCommit attributed) {
        HistoryCommit commit = attributed.getCommit();
        boolean manual = attributed.getSource() == AttributionSource.MANUAL;
        String date = commit.getCommittedDate() == null ? "" : periodResolver.formatDateTime(commit.getCommittedDate());
        String url = commit.getUrl() != null && commit.getUrl().startsWith("https://github.com/") ? commit.getUrl() : null;
        String identity = attributed.getSource() == AttributionSource.ACCOUNT ? null : attributed.getIdentity();
        return new CommitRow(date, commit.getMessage() == null ? "" : commit.getMessage(), commit.getOid(), url, identity, manual);
    }
}
