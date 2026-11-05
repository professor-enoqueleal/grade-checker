package br.com.gradechecker.model;

/** A commit ready to be rendered: date already formatted in the configured zone, safe link or null. */
public class CommitRow {

    private final String date;
    private final String message;
    private final String sha;
    private final String url;

    public CommitRow(String date, String message, String sha, String url) {
        this.date = date;
        this.message = message;
        this.sha = sha;
        this.url = url;
    }

    public String getDate() { return date; }
    public String getMessage() { return message; }
    public String getSha() { return sha; }
    /** Only GitHub URLs (https://github.com/...); null otherwise. */
    public String getUrl() { return url; }

}
