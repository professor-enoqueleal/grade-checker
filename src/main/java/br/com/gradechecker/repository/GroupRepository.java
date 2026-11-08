package br.com.gradechecker.repository;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.GroupRepositoryUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups (openspec capability group-management). Archived groups (ARCHIVED_AT not null) stay in the database with
 * their members and aliases, but {@link #findAll()} and {@link #getGroupDetail(long)} only see active groups.
 */
public class GroupRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    /** Active groups, by id (cached). Used by the home, /grades, the CSV and the member and alias APIs. */
    public List<Group> findAll() {

        final String cacheKey = CacheManager.key("group.findAll::");
        @SuppressWarnings("unchecked")
        List<Group> cached = CacheManager.get(cacheKey, List.class);
        if (cached != null) return cached;

        List<Group> groups = select(SqlQueries.GROUPS_SELECT_ALL);
        CacheManager.put(cacheKey, groups);
        return groups;
    }

    /** Archived groups, by id (not cached: only the group management page reads them). */
    public List<Group> findArchived() {
        return select(SqlQueries.GROUPS_SELECT_ARCHIVED);
    }

    /** Active group by id (cached), or null when it does not exist or is archived. */
    public Group getGroupDetail(long groupId) {

        final String cacheKey = CacheManager.key("group.findById::", groupId);
        Group cached = CacheManager.get(cacheKey, Group.class);
        if (cached != null) return cached;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.GROUPS_SELECT_BY_ID)) {

            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Group group = map(rs);
                if (group.isArchived()) return null;
                CacheManager.put(cacheKey, group);
                return group;
            }

        } catch (SQLException e) {
            logger.error("Fail on select group {}: {}", groupId, e.getMessage());
            throw new RuntimeException("Fail on SQL operation", e);
        }
    }

    /**
     * Group (active or archived) that already uses this repository, comparing normalized URLs (case, ".git" and
     * trailing slash ignored); null when none. {@code exceptId} is ignored (the group being edited).
     */
    public Group findByRepository(String repositoryUrl, Long exceptId) {

        String wanted = GroupRepositoryUrl.normalize(repositoryUrl);
        Long found = null;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SqlQueries.GROUPS_SELECT_REPOSITORIES)) {

            while (rs.next()) {
                long id = rs.getLong("ID");
                if (exceptId != null && exceptId == id) continue;
                if (wanted.equals(GroupRepositoryUrl.normalize(rs.getString("REPOSITORY_URL")))) {
                    found = id;
                    break;
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Fail on SQL operation", e);
        }

        return found == null ? null : findAnyById(found);
    }

    /** Group by id, active or archived; null when it does not exist. */
    public Group findAnyById(long groupId) {
        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.GROUPS_SELECT_BY_ID)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fail on SQL operation", e);
        }
    }

    /** Number of members of every group (id -> count); groups without members are absent. */
    public Map<Long, Integer> countMembers() {
        Map<Long, Integer> counts = new HashMap<>();
        try (Connection connection = ConnectionPoolConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SqlQueries.MEMBERS_COUNT_ALL_GROUPS)) {
            while (rs.next()) counts.put(rs.getLong("GROUP_ID"), rs.getInt("TOTAL"));
        } catch (SQLException e) {
            throw new RuntimeException("Fail on SQL operation", e);
        }
        return counts;
    }

    /** @return the id of the new group */
    public long create(Group group) {

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.GROUPS_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, group.getName());
            ps.setString(2, group.getRepositoryUrl());
            ps.setString(3, group.getOwner());
            ps.setString(4, group.getRepository());
            ps.setBoolean(5, group.isRepositoryPrivate());
            ps.executeUpdate();

            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                id = keys.next() ? keys.getLong(1) : 0;
            }
            logger.info("Group {} created: {}", id, group.getRepositoryUrl());
            return id;

        } catch (SQLException e) {
            logger.error("Fail on insert group: {}", e.getMessage());
            throw new RuntimeException("Fail on SQL operation", e);
        } finally {
            invalidate(null);
        }
    }

    /** Name and repository of an active group; members and aliases stay. @return false when it is not an active group */
    public boolean update(Group group) {
        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.GROUPS_UPDATE)) {

            ps.setString(1, group.getName());
            ps.setString(2, group.getRepositoryUrl());
            ps.setString(3, group.getOwner());
            ps.setString(4, group.getRepository());
            ps.setBoolean(5, group.isRepositoryPrivate());
            ps.setLong(6, group.getId());
            boolean updated = ps.executeUpdate() > 0;
            if (updated) logger.info("Group {} updated: {}", group.getId(), group.getRepositoryUrl());
            return updated;

        } catch (SQLException e) {
            throw new RuntimeException("Fail on SQL operation", e);
        } finally {
            invalidate(group.getId());
        }
    }

    /** @return false when it is not an active group */
    public boolean archive(long groupId) {
        return change(SqlQueries.GROUPS_ARCHIVE, groupId, "archived");
    }

    /** @return false when it is not an archived group */
    public boolean restore(long groupId) {
        return change(SqlQueries.GROUPS_RESTORE, groupId, "restored");
    }

    private boolean change(String sql, long groupId, String what) {
        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            boolean changed = ps.executeUpdate() > 0;
            if (changed) logger.info("Group {} {}", groupId, what);
            return changed;
        } catch (SQLException e) {
            throw new RuntimeException("Fail on SQL operation", e);
        } finally {
            invalidate(groupId);
        }
    }

    private List<Group> select(String sql) {
        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Group> groups = new ArrayList<>();
            while (rs.next()) groups.add(map(rs));
            return Collections.unmodifiableList(groups);

        } catch (SQLException e) {
            logger.error("Fail on select groups: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Group map(ResultSet rs) throws SQLException {
        Group group = new Group(rs.getLong("ID"), rs.getString("NAME"), rs.getString("REPOSITORY_URL"),
                rs.getString("REPOSITORY_OWNER"), rs.getString("REPOSITORY_NAME"));
        group.setRepositoryPrivate(rs.getBoolean("REPOSITORY_PRIVATE"));
        Timestamp archived = rs.getTimestamp("ARCHIVED_AT");
        group.setArchivedAt(archived == null ? null : archived.toInstant());
        return group;
    }

    private static void invalidate(Long groupId) {
        CacheManager.invalidate(CacheManager.key("group.findAll::"));
        if (groupId != null) CacheManager.invalidate(CacheManager.key("group.findById::", groupId));
    }

}
