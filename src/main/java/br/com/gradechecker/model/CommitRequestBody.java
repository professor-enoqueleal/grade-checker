package br.com.gradechecker.model;

public class CommitRequestBody {

    private String repository;
    private String owner;
    private String commitAuthor;
    private String since;
    private String until;
    private String accessToken;
    private String groupId;

    public CommitRequestBody(String repository, String owner, String commitAuthor, String since, String until, String accessToken, String groupId) {
        this.repository = repository;
        this.owner = owner;
        this.commitAuthor = commitAuthor;
        this.since = since;
        this.until = until;
        this.accessToken = accessToken;
        this.groupId = groupId;
    }

    public String getRepository() {
        return repository;
    }

    public String getOwner() {
        return owner;
    }

    public String getCommitAuthor() {
        return commitAuthor;
    }

    public String getSince() {
        return since;
    }

    public String getUntil() {
        return until;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getGroupId() {
        return groupId;
    }

}
