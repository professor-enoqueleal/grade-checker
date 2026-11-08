package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

/** Repository as returned by GET /repos/{owner}/{repo} (only the fields used to register a group). */
public class GitHubRepository {

    public static class Owner {
        private String login;
        public String getLogin() { return login; }
    }

    private String name;
    @SerializedName("html_url")
    private String htmlUrl;
    @SerializedName("private")
    private boolean privateRepository;
    private Owner owner;

    public String getName() { return name; }
    public String getHtmlUrl() { return htmlUrl; }
    public boolean isPrivateRepository() { return privateRepository; }
    public String getOwnerLogin() { return owner == null ? null : owner.getLogin(); }

}
