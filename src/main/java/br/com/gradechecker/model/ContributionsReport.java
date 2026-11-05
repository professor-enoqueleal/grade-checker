package br.com.gradechecker.model;

import java.util.List;

/**
 * Contributions per author of a repository within a period (see openspec change
 * corrigir-dashboard-contribuicoes).
 */
public class ContributionsReport {

    private final String since;
    private final String until;
    private final int totalCommits;
    private final boolean truncated;
    private final List<ContributorStatsResponseBody> consolidatedStats;

    public ContributionsReport(String since, String until, int totalCommits, boolean truncated, List<ContributorStatsResponseBody> consolidatedStats) {
        this.since = since;
        this.until = until;
        this.totalCommits = totalCommits;
        this.truncated = truncated;
        this.consolidatedStats = consolidatedStats;
    }

    public String getSince() {
        return since;
    }

    public String getUntil() {
        return until;
    }

    public int getTotalCommits() {
        return totalCommits;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public List<ContributorStatsResponseBody> getConsolidatedStats() {
        return consolidatedStats;
    }

}
