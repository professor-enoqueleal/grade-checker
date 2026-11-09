package br.com.gradechecker.core.history;

import java.util.List;

/** Contributions per author of a repository within a period (non-merge commits only). */
public final class ContributionsReport {

    private final String since;
    private final String until;
    private final int totalCommits;
    private final boolean truncated;
    private final List<ContributorStats> stats;

    public ContributionsReport(String since, String until, int totalCommits, boolean truncated, List<ContributorStats> stats) {
        this.since = since;
        this.until = until;
        this.totalCommits = totalCommits;
        this.truncated = truncated;
        this.stats = stats;
    }

    public String getSince() { return since; }
    public String getUntil() { return until; }
    public int getTotalCommits() { return totalCommits; }
    /** True when the history had more commits than the limit read from GitHub. */
    public boolean isTruncated() { return truncated; }
    /** One entry per author. */
    public List<ContributorStats> getStats() { return stats; }
}
