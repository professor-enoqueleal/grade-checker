package br.com.gradechecker.session;

/** A signed-in user (capability serverless-session-auth): item SESSION#id of the ephemeral table. */
public final class Session {

    private final String id;
    private final String login;
    private final String accessToken;

    public Session(String id, String login, String accessToken) {
        this.id = id;
        this.login = login;
        this.accessToken = accessToken;
    }

    public String getId() { return id; }
    public String getLogin() { return login; }
    public String getAccessToken() { return accessToken; }

    @Override
    public String toString() {
        return "Session{login=" + login + "}";   // never the token
    }
}
