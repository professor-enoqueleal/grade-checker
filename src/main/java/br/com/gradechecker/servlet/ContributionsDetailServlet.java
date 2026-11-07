package br.com.gradechecker.servlet;

import br.com.gradechecker.model.AttributedCommit;
import br.com.gradechecker.model.AttributedHistory;
import br.com.gradechecker.model.AttributionSource;
import br.com.gradechecker.model.CommitActivity;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.CommitRow;
import br.com.gradechecker.model.HistoryCommit;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.IdentityAliasRepository;
import br.com.gradechecker.service.PeriodResolver;
import br.com.gradechecker.service.PeriodResolver.ResolvedPeriod;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Commits counted for one author in a period (milestone buttons, All or Custom), with the same attribution as the
 * dashboard, including the manual associations of the group (openspec capability commit-history-page).
 */
@WebServlet("/contributions-detail")
public class ContributionsDetailServlet extends HttpServlet {

    private final GitHubProxy gitHubProxy;
    private final PeriodResolver periodResolver;
    private final Function<Long, Map<String, String>> aliasesByGroup;
    private final Gson gson = new Gson();   // escapes < and >: safe inside <script type="application/json">

    public ContributionsDetailServlet() {
        this(new GitHubProxy(), new PeriodResolver(), new IdentityAliasRepository()::findByGroupId);
    }

    public ContributionsDetailServlet(GitHubProxy gitHubProxy, PeriodResolver periodResolver) {
        this(gitHubProxy, periodResolver, groupId -> Collections.emptyMap());
    }

    public ContributionsDetailServlet(GitHubProxy gitHubProxy, PeriodResolver periodResolver,
                                      Function<Long, Map<String, String>> aliasesByGroup) {
        this.gitHubProxy = gitHubProxy;
        this.periodResolver = periodResolver;
        this.aliasesByGroup = aliasesByGroup;
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

        List<CommitRow> rows = new ArrayList<>();
        List<Instant> commitDates = new ArrayList<>();   // same commits as the table, for the frequency chart
        Map<String, Boolean> alsoCounted = new LinkedHashMap<>(); // identity -> manual
        boolean truncated = false;

        try {

            AttributedHistory history = gitHubProxy.getCommitHistory(commitRequestBody, aliasesOf(groupId));
            truncated = history.isTruncated();

            for (AttributedCommit commit : history.getCommits()) {
                if (!isCountedFor(commit, author)) continue;
                boolean manual = commit.getSource() == AttributionSource.MANUAL;
                rows.add(toRow(commit, manual));
                Instant date = instant(commit.getCommit().getCommittedDate());
                if (date != null) commitDates.add(date);
                if (commit.getSource() != AttributionSource.ACCOUNT && commit.getIdentity() != null) {
                    alsoCounted.merge(commit.getIdentity(), manual, Boolean::logicalOr);
                }
            }

        } catch (GitHubApiException e) {

            rows.clear();
            commitDates.clear();
            alsoCounted.clear();
            request.setAttribute("githubError", e.getMessage());

        }

        List<String> alsoCountedLabels = new ArrayList<>();
        alsoCounted.forEach((identity, manual) -> alsoCountedLabels.add(manual ? identity + " (manual)" : identity));

        request.setAttribute("author", author);
        request.setAttribute("owner", owner);
        request.setAttribute("repo", repo);
        request.setAttribute("groupId", groupId);
        request.setAttribute("commits", rows);
        request.setAttribute("totalCommits", rows.size());
        request.setAttribute("alsoCounted", alsoCountedLabels);
        request.setAttribute("truncated", truncated);
        request.setAttribute("periodKey", period.getKey());
        request.setAttribute("periodText", periodResolver.describe(period));
        request.setAttribute("periodOptions", periodResolver.options(period.getKey()));
        request.setAttribute("customSince", periodResolver.toInputValue(period.getSince()));
        request.setAttribute("customUntil", periodResolver.toInputValue(period.getUntil()));

        // commit frequency chart (openspec capability commit-history-page): null without commits
        CommitActivity activity = CommitActivity.of(commitDates, period.getSince(), period.getUntil(),
                periodResolver.getMilestones(), periodResolver.getClock());
        request.setAttribute("commitActivity", activity);
        request.setAttribute("commitActivityJson", activity == null ? null : gson.toJson(activity));

        request.getRequestDispatcher("commits-detail.jsp").forward(request, response);

    }

    private static Instant instant(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso.trim()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Commits attributed to the login (case-insensitive); authors without account never match. */
    private static boolean isCountedFor(AttributedCommit commit, String author) {
        return author != null && commit.isLinked() && commit.getAuthor() != null
                && commit.getAuthor().trim().equalsIgnoreCase(author.trim());
    }

    private CommitRow toRow(AttributedCommit attributed, boolean manual) {
        HistoryCommit commit = attributed.getCommit();
        String date = commit.getCommittedDate() == null ? "" : periodResolver.formatDateTime(commit.getCommittedDate());
        String url = commit.getUrl() != null && commit.getUrl().startsWith("https://github.com/") ? commit.getUrl() : null;
        String identity = attributed.getSource() == AttributionSource.ACCOUNT ? null : attributed.getIdentity();
        return new CommitRow(date, commit.getMessage() == null ? "" : commit.getMessage(), commit.getOid(), url, identity, manual);
    }

    private Map<String, String> aliasesOf(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) return Collections.emptyMap();
        try {
            return aliasesByGroup.apply(Long.parseLong(groupId.trim()));
        } catch (NumberFormatException e) {
            return Collections.emptyMap();
        }
    }

}
