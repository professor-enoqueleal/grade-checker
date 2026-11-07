package br.com.gradechecker.model;

import java.util.Collections;
import java.util.List;

/** Non-merge commits of the period, each with its author (openspec capability commit-history-page). */
public class AttributedHistory {

    private final boolean truncated;
    private final List<AttributedCommit> commits;

    public AttributedHistory(boolean truncated, List<AttributedCommit> commits) {
        this.truncated = truncated;
        this.commits = Collections.unmodifiableList(commits);
    }

    public boolean isTruncated() { return truncated; }
    public List<AttributedCommit> getCommits() { return commits; }
}
