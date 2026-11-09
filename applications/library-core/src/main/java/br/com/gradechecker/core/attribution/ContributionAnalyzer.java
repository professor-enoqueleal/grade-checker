package br.com.gradechecker.core.attribution;

import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.history.ContributionsReport;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.RepositoryHistory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reports and attributions computed from a history already read (capability core-structure): no call to GitHub.
 * Merge commits are ignored; the manual associations of the group (identity key -> login) are applied.
 */
public final class ContributionAnalyzer {

    private final GradingConfig grading;
    private final ZoneId zone;

    /** @param zone zone of the milestones, used to count the active days */
    public ContributionAnalyzer(GradingConfig grading, ZoneId zone) {
        this.grading = grading;
        this.zone = zone;
    }

    /** Contributions per author of the whole history. */
    public ContributionsReport report(RepositoryHistory history, Map<String, String> aliases) {
        return report(history.getSince(), history.getUntil(), history.isTruncated(), history.getCommits(), aliases);
    }

    /**
     * Contributions per author of the commits whose committedDate is inside [since, until] (ISO-8601; null = open),
     * aggregated exactly as {@link #report(RepositoryHistory, Map)}. Used to grade every milestone with a single read
     * of the history (capability gradebook-api).
     */
    public ContributionsReport report(RepositoryHistory history, String since, String until, Map<String, String> aliases) {
        Instant from = since == null ? null : Instant.parse(since);
        Instant to = until == null ? null : Instant.parse(until);
        List<HistoryCommit> inside = new ArrayList<>();
        for (HistoryCommit commit : history.getCommits()) {
            Instant date = committedAt(commit);
            if (date == null) continue;
            if (from != null && date.isBefore(from)) continue;
            if (to != null && date.isAfter(to)) continue;
            inside.add(commit);
        }
        return report(since, until, history.isTruncated(), inside, aliases);
    }

    /**
     * Non-merge commits, each with the author it is counted for (same rules as the report) - capability
     * commit-history-page.
     */
    public AttributedHistory attribute(RepositoryHistory history, Map<String, String> aliases) {
        AuthorAggregator aggregator = aggregate(history.getCommits(), aliases);
        List<AttributedCommit> commits = new ArrayList<>();
        for (HistoryCommit commit : history.getCommits()) {
            if (!commit.isMerge()) commits.add(aggregator.attributionOf(commit));
        }
        return new AttributedHistory(history.isTruncated(), commits);
    }

    private ContributionsReport report(String since, String until, boolean truncated, List<HistoryCommit> commits,
                                       Map<String, String> aliases) {
        int totalCommits = 0;
        for (HistoryCommit commit : commits) if (!commit.isMerge()) totalCommits++;
        return new ContributionsReport(since, until, totalCommits, truncated, aggregate(commits, aliases).result());
    }

    private AuthorAggregator aggregate(List<HistoryCommit> commits, Map<String, String> aliases) {
        AuthorAggregator aggregator = new AuthorAggregator(grading, zone, aliases == null ? Collections.emptyMap() : aliases);
        for (HistoryCommit c : commits) {
            if (c.isMerge()) continue;
            aggregator.add(c.getAuthorLogin(), c.getAuthorName(), c.getAuthorEmail(), c.getAdditions(), c.getDeletions(),
                    c.getCommittedDate(), c.getChangedFiles());
        }
        return aggregator;
    }

    private static Instant committedAt(HistoryCommit commit) {
        try {
            return commit.getCommittedDate() == null ? null : OffsetDateTime.parse(commit.getCommittedDate()).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
