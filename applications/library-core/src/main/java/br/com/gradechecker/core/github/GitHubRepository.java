package br.com.gradechecker.core.github;

/** A repository as GitHub spells it (only what is needed to register a group). */
public final class GitHubRepository {

    private final String ownerLogin;
    private final String name;
    private final String htmlUrl;
    private final boolean privateRepository;

    public GitHubRepository(String ownerLogin, String name, String htmlUrl, boolean privateRepository) {
        this.ownerLogin = ownerLogin;
        this.name = name;
        this.htmlUrl = htmlUrl;
        this.privateRepository = privateRepository;
    }

    public String getOwnerLogin() { return ownerLogin; }
    public String getName() { return name; }
    public String getHtmlUrl() { return htmlUrl; }
    public boolean isPrivateRepository() { return privateRepository; }
}
