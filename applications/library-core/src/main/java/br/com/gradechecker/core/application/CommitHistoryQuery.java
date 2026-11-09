package br.com.gradechecker.core.application;

import br.com.gradechecker.core.attribution.AttributedCommit;
import br.com.gradechecker.core.attribution.AttributedHistory;
import br.com.gradechecker.core.attribution.AttributionSource;
import br.com.gradechecker.core.attribution.ContributionAnalyzer;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.history.CommitActivity;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.period.PeriodResolver.ResolvedPeriod;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.IdentityAliasRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commits attributed to a login in a period (capability core-use-cases), with the identities without account also
 * counted for it ("Also counted") and the data of the frequency chart. An invalid period shows all the history, with a
 * warning.
 */
public class CommitHistoryQuery {

    public static final String AUTHOR_REQUIRED = "The 'author' parameter is required.";

    /** The history of the login. */
    public static final class History {
        private final String author;
        private final ResolvedPeriod period;
        private final String periodWarning;
        private final List<AttributedCommit> commits;
        private final List<String> alsoCounted;
        private final boolean truncated;
        private final CommitActivity activity;

        History(String author, ResolvedPeriod period, String periodWarning, List<AttributedCommit> commits,
                List<String> alsoCounted, boolean truncated, CommitActivity activity) {
            this.author = author;
            this.period = period;
            this.periodWarning = periodWarning;
            this.commits = commits;
            this.alsoCounted = alsoCounted;
            this.truncated = truncated;
            this.activity = activity;
        }

        public String getAuthor() { return author; }
        /** The period shown (All when the asked one was invalid). */
        public ResolvedPeriod getPeriod() { return period; }
        public String getPeriodWarning() { return periodWarning; }
        /** Commits counted for the login, newest first. */
        public List<AttributedCommit> getCommits() { return commits; }
        /** Identities without account counted for the login, " (manual)" for the manual links. */
        public List<String> getAlsoCounted() { return alsoCounted; }
        public boolean isTruncated() { return truncated; }
        /** Data of the frequency chart; null without commits. */
        public CommitActivity getActivity() { return activity; }
    }

    /** The period shown: the asked one, or All with a warning when the asked one is invalid. */
    public static final class ShownPeriod {
        private final ResolvedPeriod period;
        private final String warning;

        ShownPeriod(ResolvedPeriod period, String warning) {
            this.period = period;
            this.warning = warning;
        }

        public ResolvedPeriod getPeriod() { return period; }
        public String getWarning() { return warning; }
    }

    private final GroupManagement groups;
    private final IdentityAliasRepository aliases;
    private final GitHubGateway gitHub;
    private final PeriodResolver periods;
    private final ContributionAnalyzer analyzer;

    public CommitHistoryQuery(GroupManagement groups, IdentityAliasRepository aliases, GitHubGateway gitHub,
                              PeriodResolver periods, GradingConfig grading) {
        this.groups = groups;
        this.aliases = aliases;
        this.gitHub = gitHub;
        this.periods = periods;
        this.analyzer = new ContributionAnalyzer(grading, periods.getZone());
    }

    /** @throws br.com.gradechecker.core.github.GitHubApiException when GitHub cannot be read */
    public History history(long groupId, String author, String period, String since, String until, String accessToken) {
        if (author == null) throw DomainException.invalid("bad_request", AUTHOR_REQUIRED);
        Group group = groups.activeGroup(groupId);

        ShownPeriod shown = period(period, since, until);
        ResolvedPeriod resolved = shown.getPeriod();

        RepositoryHistory read = gitHub.getHistory(new HistoryRequest(group.getOwner(), group.getRepository(),
                resolved.getSinceIso(), resolved.getUntilIso(), accessToken));
        AttributedHistory history = analyzer.attribute(read, aliases.findByGroupId(group.getId()));

        List<AttributedCommit> counted = new ArrayList<>();
        List<Instant> dates = new ArrayList<>();          // same commits as the table, for the frequency chart
        Map<String, Boolean> alsoCounted = new LinkedHashMap<>();
        for (AttributedCommit commit : history.getCommits()) {
            if (!isCountedFor(commit, author)) continue;
            counted.add(commit);
            Instant date = instant(commit.getCommit().getCommittedDate());
            if (date != null) dates.add(date);
            if (commit.getSource() != AttributionSource.ACCOUNT && commit.getIdentity() != null) {
                alsoCounted.merge(commit.getIdentity(), commit.getSource() == AttributionSource.MANUAL, Boolean::logicalOr);
            }
        }
        List<String> labels = new ArrayList<>();
        alsoCounted.forEach((identity, manual) -> labels.add(manual ? identity + " (manual)" : identity));

        CommitActivity activity = CommitActivity.of(dates, resolved.getSince(), resolved.getUntil(), periods.getMilestones(), periods.getClock());
        return new History(author, resolved, shown.getWarning(), counted, labels, history.isTruncated(), activity);
    }

    /** The period of the page: an invalid one shows all the history, with a warning. */
    public ShownPeriod period(String period, String since, String until) {
        ResolvedPeriod resolved = periods.resolve(period, since, until);
        if (resolved.getError() == null) return new ShownPeriod(resolved, null);
        return new ShownPeriod(periods.all(), resolved.getError() + " Showing all history instead.");
    }

    /** Commits attributed to the login (case-insensitive); authors without account never match. */
    private static boolean isCountedFor(AttributedCommit commit, String author) {
        return commit.isLinked() && commit.getAuthor() != null && commit.getAuthor().trim().equalsIgnoreCase(author.trim());
    }

    private static Instant instant(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso.trim()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
