package br.com.gradechecker.model;

public class Member {

    private final String name;

    private final String gitHubUserName;

    private final String groupId;

    public Member(String name, String gitHubUserName, String groupId) {
        this.name = name;
        this.gitHubUserName = gitHubUserName;
        this.groupId = groupId;
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

}
