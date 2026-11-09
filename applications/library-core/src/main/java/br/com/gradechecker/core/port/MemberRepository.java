package br.com.gradechecker.core.port;

import br.com.gradechecker.core.group.Member;

import java.util.List;

/**
 * Persistence of the members (students) of a group (capability core-ports). Logins are unique in a group ignoring
 * case; the stored spelling is the one returned by GitHub.
 */
public interface MemberRepository {

    enum RenameResult { OK, NOT_MEMBER, ALREADY_MEMBER }

    List<Member> findByGroupId(long groupId);

    /** GitHub logins of the members, sorted. */
    List<String> findLoginsByGroupId(long groupId);

    boolean isMember(long groupId, String login);

    /** @return false when the login (ignoring case) is already a member */
    boolean addIfAbsent(long groupId, String login, String name, boolean manual);

    /** @return false when the login is not a member */
    boolean delete(long groupId, String login);

    /**
     * Changes login and name of a member; the manual identity links of the old login move to the new one in the same
     * atomic operation.
     */
    RenameResult rename(long groupId, String login, String newLogin, String name);
}
