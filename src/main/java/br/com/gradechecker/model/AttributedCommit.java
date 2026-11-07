package br.com.gradechecker.model;

/** A non-merge commit and the author it is counted for, with the same rules as the dashboard. */
public class AttributedCommit {

    private final HistoryCommit commit;
    private final String author;
    private final boolean linked;
    private final AttributionSource source;
    private final String identity;

    public AttributedCommit(HistoryCommit commit, String author, boolean linked, AttributionSource source, String identity) {
        this.commit = commit;
        this.author = author;
        this.linked = linked;
        this.source = source;
        this.identity = identity;
    }

    public HistoryCommit getCommit() { return commit; }
    /** login (linked) or display name of an author without account */
    public String getAuthor() { return author; }
    public boolean isLinked() { return linked; }
    public AttributionSource getSource() { return source; }
    /** "name <email>" of the git identity; null for commits linked to the account */
    public String getIdentity() { return identity; }
}
