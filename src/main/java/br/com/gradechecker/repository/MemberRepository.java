package br.com.gradechecker.repository;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Member;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Members of a group: the students, marked by an administrator (openspec capability group-members).
 * Logins are compared ignoring case. Contributors (GitHub) are never stored here automatically.
 */
public class MemberRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    /** GitHub usernames of the members of a group: the students of the milestone grade (never cached). */
    public List<String> findUsernamesByGroupId(long groupId) {

        List<String> usernames = new ArrayList<>();

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_SELECT_USERNAMES_BY_GROUP_ID)) {

            ps.setLong(1, groupId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("GITHUB_USERNAME");
                    if (username != null && !username.trim().isEmpty()) usernames.add(username.trim());
                }
            }

        } catch (Exception e) {
            logger.error("Fail on select members of group {}: {}", groupId, e.getMessage());
        }

        return usernames;
    }

    /** Members of a group (login and name) sorted by login ignoring case; cached, invalidated on every write. */
    public List<Member> findByGroupId(long groupId) {

        final String cacheKey = CacheManager.key("member.findByGroupId::", groupId);
        @SuppressWarnings("unchecked")
        List<Member> cached = CacheManager.get(cacheKey, List.class);
        if (cached != null) return cached;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_SELECT_BY_GROUP_ID)) {

            ps.setLong(1, groupId);

            List<Member> members = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String login = rs.getString("GITHUB_USERNAME");
                    if (login == null || login.trim().isEmpty()) continue;
                    String name = rs.getString("NAME");
                    members.add(new Member(name == null || name.trim().isEmpty() ? login.trim() : name.trim(), login.trim(), String.valueOf(groupId)));
                }
            }

            List<Member> result = Collections.unmodifiableList(members);
            CacheManager.put(cacheKey, result);
            return result;

        } catch (Exception e) {
            logger.error("Fail on select members of group {}: {}", groupId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Number of members of a group (cached). */
    public int findById(Long id) {

        final String cacheKey = CacheManager.key("member.countByGroupId::", id);
        Integer cached = CacheManager.get(cacheKey, Integer.class);
        if (cached != null) return cached;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_COUNT_BY_GROUP_ID)) {

            ps.setLong(1, id);

            int total = 0;
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total = rs.getInt("TOTAL");
            }

            CacheManager.put(cacheKey, total);
            return total;

        } catch (SQLException e) {
            logger.error("Fail on count members of group {}: {}", id, e.getMessage());
            return 0;
        }
    }

    /** True when the login is a member of the group (ignoring case). */
    public boolean isMember(long groupId, String login) {

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_COUNT_BY_GROUP_ID_AND_USERNAME)) {

            ps.setLong(1, groupId);
            ps.setString(2, login == null ? "" : login.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("CNT") > 0;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Fail on select member", e);
        }
    }

    /**
     * Adds a member (GITHUB_ID is always NULL: databases of older versions still have a foreign key to CONTRIBUTORS).
     *
     * @return false when the login is already a member of the group (ignoring case)
     */
    public boolean addIfAbsent(long groupId, String login, String name, boolean createdManually) {

        if (isMember(groupId, login)) return false;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_INSERT)) {

            ps.setString(1, name == null || name.trim().isEmpty() ? login.trim() : name.trim());
            ps.setString(2, login.trim());
            ps.setLong(3, groupId);
            ps.setBoolean(4, createdManually);
            ps.executeUpdate();

            logger.info("Member {} added to group {}", login, groupId);

        } catch (SQLException e) {
            throw new RuntimeException("Fail on insert member", e);
        } finally {
            invalidate(groupId);
        }

        return true;
    }

    /** @return true when a member was removed */
    public boolean delete(long groupId, String login) {

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_DELETE)) {

            ps.setLong(1, groupId);
            ps.setString(2, login == null ? "" : login.trim());
            boolean removed = ps.executeUpdate() > 0;

            if (removed) logger.info("Member {} removed from group {}", login, groupId);
            return removed;

        } catch (SQLException e) {
            throw new RuntimeException("Fail on delete member", e);
        } finally {
            invalidate(groupId);
        }
    }

    /** Outcome of {@link #rename}. */
    public enum RenameResult { RENAMED, NOT_MEMBER, ALREADY_MEMBER }

    /**
     * Edits a member: login and name. In the same transaction, the manual identity associations (IDENTITY_ALIASES)
     * of the old login in the group move to the new login, so the student keeps them (openspec capability group-members).
     *
     * @return NOT_MEMBER when {@code login} is not a member; ALREADY_MEMBER when {@code newLogin} is another member
     *         (ignoring case; the member itself does not count). Nothing is changed in both cases.
     */
    public RenameResult rename(long groupId, String login, String newLogin, String name) {

        String current = login == null ? "" : login.trim();
        String target = newLogin == null || newLogin.trim().isEmpty() ? current : newLogin.trim();
        String finalName = name == null || name.trim().isEmpty() ? target : name.trim();

        try (Connection connection = ConnectionPoolConfig.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!exists(connection, groupId, current)) {
                    connection.rollback();
                    return RenameResult.NOT_MEMBER;
                }
                if (!target.equalsIgnoreCase(current) && exists(connection, groupId, target)) {
                    connection.rollback();
                    return RenameResult.ALREADY_MEMBER;
                }

                try (PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_UPDATE)) {
                    ps.setString(1, target);
                    ps.setString(2, finalName);
                    ps.setLong(3, groupId);
                    ps.setString(4, current);
                    ps.executeUpdate();
                }
                int moved = 0;
                if (!target.equals(current)) {
                    try (PreparedStatement ps = connection.prepareStatement(SqlQueries.ALIASES_UPDATE_LOGIN)) {
                        ps.setString(1, target);
                        ps.setLong(2, groupId);
                        ps.setString(3, current);
                        moved = ps.executeUpdate();
                    }
                }

                connection.commit();
                logger.info("Member {} of group {} edited: login {}, {} association(s) moved", current, groupId, target, moved);
                return RenameResult.RENAMED;

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Fail on edit member", e);
        } finally {
            invalidate(groupId);
        }
    }

    private static boolean exists(Connection connection, long groupId, String login) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_COUNT_BY_GROUP_ID_AND_USERNAME)) {
            ps.setLong(1, groupId);
            ps.setString(2, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("CNT") > 0;
            }
        }
    }

    private static void invalidate(long groupId) {
        CacheManager.invalidate(CacheManager.key("member.countByGroupId::", groupId));
        CacheManager.invalidate(CacheManager.key("member.findByGroupId::", groupId));
    }

}
