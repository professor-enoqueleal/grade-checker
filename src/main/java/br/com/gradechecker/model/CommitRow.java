package br.com.gradechecker.model;

/** A commit ready to be rendered: date already formatted in the configured zone, safe link or null. */
public class CommitRow {

    private final String date;
    private final String message;
    private final String sha;
    private final String url;
    private final String identity;
    private final boolean manual;

    public CommitRow(String date, String message, String sha, String url) {
        this(date, message, sha, url, null, false);
    }

    public CommitRow(String date, String message, String sha, String url, String identity, boolean manual) {
        this.date = date;
        this.message = message;
        this.sha = sha;
        this.url = url;
        this.identity = identity;
        this.manual = manual;
    }

    public String getDate() { return date; }
    public String getMessage() { return message; }
    public String getSha() { return sha; }
    /** Only GitHub URLs (https://github.com/...); null otherwise. */
    public String getUrl() { return url; }
    /** "name <email>" of a commit without account counted for the author; null when linked to the account. */
    public String getIdentity() { return identity; }
    /** true when the identity was linked manually by an administrator. */
    public boolean isManual() { return manual; }

}
