package br.com.gradechecker.model;

public class Member {

    private final String name;

    private final String gitHubUserName;

    private final String groupId;

    // optional GitHub contributor id
    private final Integer gitHubId;

    public Member(String name, String gitHubUserName, String groupId) {
        this.name = name;
        this.gitHubUserName = gitHubUserName;
        this.groupId = groupId;
        this.gitHubId = null;
    }

    public Member(String name, String gitHubUserName, String groupId, Integer gitHubId) {
        this.name = name;
        this.gitHubUserName = gitHubUserName;
        this.groupId = groupId;
        this.gitHubId = gitHubId;
    }

    public String getName() {
        return name;
    }

    public String getGitHubUserName() {
        return gitHubUserName;
    }

    public String getGroupId() {
        return groupId;
    }

    public Integer getGitHubId() {
        return gitHubId;
    }

}
