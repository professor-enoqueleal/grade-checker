package br.com.gradechecker.core.application;

import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.IdentityAliasRepository;
import br.com.gradechecker.core.port.MemberRepository;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Manual links of identities without a GitHub account to students (capability core-use-cases): the key is
 * {@code email:...} or {@code name:...} and the login must be a student of the group (no bots, no excluded logins).
 */
public class IdentityLinking {

    public static final String NOT_A_STUDENT = "The login must be a student of this group.";
    public static final String INVALID_IDENTITY = "Invalid identity key.";
    public static final String NOT_LINKED = "This identity is not linked.";

    static final Pattern IDENTITY_KEY = Pattern.compile("^(email|name):\\S.{0,500}$");

    private final GroupRepository groups;
    private final MemberRepository members;
    private final IdentityAliasRepository aliases;
    private final GradingConfig grading;

    public IdentityLinking(GroupRepository groups, MemberRepository members, IdentityAliasRepository aliases, GradingConfig grading) {
        this.groups = groups;
        this.members = members;
        this.aliases = aliases;
        this.grading = grading;
    }

    /** Links the identity to the student; returns the login with the spelling registered in the group. */
    public String link(long groupId, String identityKey, String login, String linkedBy) {
        activeGroup(groupId);
        identityKey(identityKey);
        String student = studentOf(groupId, login);
        if (student == null) throw DomainException.invalid("invalid_login", NOT_A_STUDENT);
        aliases.save(groupId, identityKey, student, linkedBy);
        return student;
    }

    public void unlink(long groupId, String identityKey) {
        activeGroup(groupId);
        identityKey(identityKey);
        if (!aliases.delete(groupId, identityKey)) throw DomainException.notFound("not_found", NOT_LINKED);
    }

    private void activeGroup(long groupId) {
        if (groups.findActive(groupId) == null) throw GroupManagement.notFound();
    }

    private static void identityKey(String value) {
        if (value == null || !IDENTITY_KEY.matcher(value).matches()) throw DomainException.invalid("invalid_identity", INVALID_IDENTITY);
    }

    /** Spelling of the login as registered in the group; null when it is not a student of the group. */
    private String studentOf(long groupId, String login) {
        if (login == null) return null;
        String wanted = login.toLowerCase(Locale.ROOT);
        for (String member : members.findLoginsByGroupId(groupId)) {
            if (member != null && member.trim().toLowerCase(Locale.ROOT).equals(wanted) && grading.isStudentLogin(member.trim())) {
                return member.trim();
            }
        }
        return null;
    }
}
