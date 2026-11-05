package br.com.gradechecker.model;

import java.util.List;

/** Commits of an author in a period, and whether the list was cut by the configured limit. */
public class CommitsPage {

    private final List<Commit> commits;
    private final boolean truncated;

    public CommitsPage(List<Commit> commits, boolean truncated) {
        this.commits = commits;
        this.truncated = truncated;
    }

    public List<Commit> getCommits() {
        return commits;
    }

    public boolean isTruncated() {
        return truncated;
    }

}
