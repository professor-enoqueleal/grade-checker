package br.com.gradechecker.core.history;

/**
 * Commits of the default branch of {@code owner/repository} within [since, until] (ISO-8601 instants; null = open),
 * read with the token of the session.
 */
public final class HistoryRequest {

    private final String owner;
    private final String repository;
    private final String since;
    private final String until;
    private final String accessToken;

    public HistoryRequest(String owner, String repository, String since, String until, String accessToken) {
        this.owner = owner;
        this.repository = repository;
        this.since = since;
        this.until = until;
        this.accessToken = accessToken;
    }

    public String getOwner() { return owner; }
    public String getRepository() { return repository; }
    public String getSince() { return since; }
    public String getUntil() { return until; }
    public String getAccessToken() { return accessToken; }
}
