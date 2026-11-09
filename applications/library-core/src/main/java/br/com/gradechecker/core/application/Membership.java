package br.com.gradechecker.core.application;

import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.group.Member;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.IdentityAliasRepository;
import br.com.gradechecker.core.port.MemberRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Members of a group (capability core-use-cases): the login must have a valid format and exist on GitHub (stored with
 * the spelling of GitHub), is a member once ignoring case, and a student with linked identities is not removed.
 */
public class Membership {

    public static final String LOGIN_REQUIRED = "A valid GitHub login is required.";
    public static final String INVALID_LOGIN = "This GitHub user does not exist.";
    public static final String ALREADY_MEMBER = "This user is already a member of the group.";
    public static final String NOT_MEMBER = "This user is not a member of the group.";
    public static final String HAS_ALIASES = "Unlink the identities linked to this student before removing them.";

    static final Pattern LOGIN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._\\-\\[\\]]{0,254}$");

    /** A member as stored, and the login it had before an edit (null otherwise). */
    public static final class MemberChange {
        private final long groupId;
        private final String login;
        private final String name;
        private final String previousLogin;

        MemberChange(long groupId, String login, String name, String previousLogin) {
            this.groupId = groupId;
            this.login = login;
            this.name = name;
            this.previousLogin = previousLogin;
        }

        public long getGroupId() { return groupId; }
        public String getLogin() { return login; }
        public String getName() { return name; }
        public String getPreviousLogin() { return previousLogin; }
    }

    /** An active group and its members. */
    public static final class GroupMembers {
        private final Group group;
        private final List<Member> members;

        GroupMembers(Group group, List<Member> members) {
            this.group = group;
            this.members = members;
        }

        public Group getGroup() { return group; }
        public List<Member> getMembers() { return members; }
    }

    private final GroupRepository groups;
    private final MemberRepository members;
    private final IdentityAliasRepository aliases;
    private final GitHubGateway gitHub;
    private final GradingConfig grading;

    public Membership(GroupRepository groups, MemberRepository members, IdentityAliasRepository aliases,
                      GitHubGateway gitHub, GradingConfig grading) {
        this.groups = groups;
        this.members = members;
        this.aliases = aliases;
        this.gitHub = gitHub;
        this.grading = grading;
    }

    /** The active group and its members. */
    public GroupMembers members(long groupId) {
        Group group = activeGroup(groupId);
        return new GroupMembers(group, members.findByGroupId(groupId));
    }

    /** Students of the group (members without bots and excluded logins), unique ignoring case, sorted by login. */
    public List<String> students(long groupId) {
        Map<String, String> unique = new TreeMap<>();
        for (String member : members.findLoginsByGroupId(groupId)) {
            if (member != null && grading.isStudentLogin(member)) unique.putIfAbsent(member.trim().toLowerCase(Locale.ROOT), member.trim());
        }
        return new ArrayList<>(unique.values());
    }

    /** Adds the GitHub user (spelling of GitHub) to the active group; the name defaults to the login. */
    public MemberChange add(long groupId, String typedLogin, String name, boolean manual, String accessToken) {
        requireLogin(typedLogin);
        activeGroup(groupId);

        String login = gitHubLogin(typedLogin, accessToken);
        name = name(name, login);
        if (!members.addIfAbsent(groupId, login, name, manual)) {
            throw DomainException.conflict("already_member", ALREADY_MEMBER);
        }
        return new MemberChange(groupId, login, name, null);
    }

    /** Changes login (checked on GitHub when it changes) and name; the manual identity links follow the login. */
    public MemberChange edit(long groupId, String currentLogin, String newLogin, String name, String accessToken) {
        requireLogin(currentLogin);
        activeGroup(groupId);

        newLogin = GroupManagement.blankToNull(newLogin);
        if (newLogin == null) newLogin = currentLogin;
        requireLogin(newLogin);

        if (!members.isMember(groupId, currentLogin)) throw notMember();
        if (!newLogin.equals(currentLogin)) newLogin = gitHubLogin(newLogin, accessToken);
        name = name(name, newLogin);

        MemberRepository.RenameResult result = members.rename(groupId, currentLogin, newLogin, name);
        if (result == MemberRepository.RenameResult.NOT_MEMBER) throw notMember();
        if (result == MemberRepository.RenameResult.ALREADY_MEMBER) throw DomainException.conflict("already_member", ALREADY_MEMBER);
        return new MemberChange(groupId, newLogin, name, currentLogin);
    }

    /** Removes a member without linked identities. */
    public void remove(long groupId, String login) {
        requireLogin(login);
        activeGroup(groupId);

        if (!members.isMember(groupId, login)) throw notMember();
        if (aliases.existsForLogin(groupId, login)) throw DomainException.conflict("has_aliases", HAS_ALIASES);
        if (!members.delete(groupId, login)) throw notMember();
    }

    private Group activeGroup(long groupId) {
        Group group = groups.findActive(groupId);
        if (group == null) throw GroupManagement.notFound();
        return group;
    }

    private String gitHubLogin(String login, String accessToken) {
        Contributor user = gitHub.getUser(login, accessToken);
        if (user == null) throw DomainException.invalid("invalid_login", INVALID_LOGIN);
        return user.getLogin();
    }

    private static void requireLogin(String login) {
        if (login == null || !LOGIN.matcher(login).matches()) throw DomainException.invalid("bad_request", LOGIN_REQUIRED);
    }

    private static String name(String name, String login) {
        name = GroupManagement.blankToNull(name);
        if (name == null) name = login;
        return name.length() > GroupManagement.MAX_NAME ? name.substring(0, GroupManagement.MAX_NAME) : name;
    }

    private static DomainException notMember() {
        return DomainException.notFound("not_found", NOT_MEMBER);
    }
}
