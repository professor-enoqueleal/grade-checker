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
    /** Date without the time ("Oct 22, 2026"): on phones the date and the time take one line each. */
    public String getDay() {
        int space = date == null ? -1 : date.lastIndexOf(' ');
        return space < 0 ? date : date.substring(0, space);
    }
    /** Time of {@link #getDate()} ("11:29"), or empty when the date has no time. */
    public String getTime() {
        int space = date == null ? -1 : date.lastIndexOf(' ');
        return space < 0 ? "" : date.substring(space + 1);
    }
    /** First 7 characters of the hash, as shown by GitHub. */
    public String getShortSha() {
        return sha == null || sha.length() <= 7 ? sha : sha.substring(0, 7);
    }
    public String getMessage() { return message; }
    public String getSha() { return sha; }
    /** Only GitHub URLs (https://github.com/...); null otherwise. */
    public String getUrl() { return url; }
    /** "name <email>" of a commit without account counted for the author; null when linked to the account. */
    public String getIdentity() { return identity; }
    /** true when the identity was linked manually by an administrator. */
    public boolean isManual() { return manual; }

}
