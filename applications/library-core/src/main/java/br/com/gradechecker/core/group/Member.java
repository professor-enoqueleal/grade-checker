package br.com.gradechecker.core.group;

/** A student (member) of a group, with the login as spelled by GitHub. */
public final class Member {

    private final String name;
    private final String login;
    private final long groupId;

    public Member(String name, String login, long groupId) {
        this.name = name;
        this.login = login;
        this.groupId = groupId;
    }

    public String getName() { return name; }
    public String getLogin() { return login; }
    public long getGroupId() { return groupId; }
}
