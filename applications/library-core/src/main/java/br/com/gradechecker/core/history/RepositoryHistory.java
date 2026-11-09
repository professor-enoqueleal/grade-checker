package br.com.gradechecker.core.history;

import java.util.Collections;
import java.util.List;

/** Commits of the default branch in [since, until] (merges included), as read from GitHub; cached without aliases. */
public class RepositoryHistory {

    private final String since;
    private final String until;
    private final boolean truncated;
    private final List<HistoryCommit> commits;

    public RepositoryHistory(String since, String until, boolean truncated, List<HistoryCommit> commits) {
        this.since = since;
        this.until = until;
        this.truncated = truncated;
        this.commits = Collections.unmodifiableList(commits);
    }

    public String getSince() { return since; }
    public String getUntil() { return until; }
    /** true when GH_DASHBOARD_MAX_COMMITS was reached with pages left. */
    public boolean isTruncated() { return truncated; }
    public List<HistoryCommit> getCommits() { return commits; }
}
