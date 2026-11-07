package br.com.gradechecker.model;

/** One commit of the repository history read with GraphQL (shared by the dashboard and the commit history page). */
public class HistoryCommit {

    private final String oid;
    private final String message;
    private final String url;
    private final String committedDate;
    private final int additions;
    private final int deletions;
    private final int changedFiles;
    private final int parents;
    private final String authorName;
    private final String authorEmail;
    private final String authorLogin;

    public HistoryCommit(String oid, String message, String url, String committedDate, int additions, int deletions,
                         int changedFiles, int parents, String authorName, String authorEmail, String authorLogin) {
        this.oid = oid;
        this.message = message;
        this.url = url;
        this.committedDate = committedDate;
        this.additions = additions;
        this.deletions = deletions;
        this.changedFiles = changedFiles;
        this.parents = parents;
        this.authorName = authorName;
        this.authorEmail = authorEmail;
        this.authorLogin = authorLogin;
    }

    public boolean isMerge() {
        return parents > 1;
    }

    public String getOid() { return oid; }
    public String getMessage() { return message; }
    public String getUrl() { return url; }
    public String getCommittedDate() { return committedDate; }
    public int getAdditions() { return additions; }
    public int getDeletions() { return deletions; }
    public int getChangedFiles() { return changedFiles; }
    public int getParents() { return parents; }
    public String getAuthorName() { return authorName; }
    public String getAuthorEmail() { return authorEmail; }
    public String getAuthorLogin() { return authorLogin; }
}
