package br.com.gradechecker.core.github;

/**
 * A GitHub account: a contributor of a repository (with its number of contributions) or a user looked up by login
 * (contributions = 0).
 */
public class Contributor {

    private String login;
    private long id;
    private String avatarUrl;
    private String htmlUrl;
    private int contributions;

    public Contributor() {
        // for the cache of the applications (JSON)
    }

    public Contributor(String login, long id, String avatarUrl, String htmlUrl, int contributions) {
        this.login = login;
        this.id = id;
        this.avatarUrl = avatarUrl;
        this.htmlUrl = htmlUrl;
        this.contributions = contributions;
    }

    public String getLogin() { return login; }
    public long getId() { return id; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getHtmlUrl() { return htmlUrl; }
    public int getContributions() { return contributions; }
}
