package br.com.gradechecker.adapter.outbound.jdbc;

import br.com.gradechecker.adapter.outbound.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.group.RepositoryUrl;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.RepositoryTakenException;

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
 * Groups in H2 (port GroupRepository of grade-checker-core, openspec capability group-management). Archived groups
 * (ARCHIVED_AT not null) stay in the database with their members and aliases, but {@link #findActive()} and
 * {@link #findActive(long)} only see active groups.
 */
public class JdbcGroupRepository implements GroupRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    /** Active groups, by id, with their number of members (the list is cached; the counts are not). */
    @Override
    public List<Group> findActive() {

        final String cacheKey = CacheManager.key("group.findAll::");
        @SuppressWarnings("unchecked")
        List<Group> groups = CacheManager.get(cacheKey, List.class);
        if (groups == null) {
            groups = select(SqlQueries.GROUPS_SELECT_ALL);
            CacheManager.put(cacheKey, groups);
        }
        return withMemberCounts(groups);
    }

    /** Archived groups, by id, with their number of members (not cached: only the group management page reads them). */
    @Override
    public List<Group> findArchived() {
        return withMemberCounts(select(SqlQueries.GROUPS_SELECT_ARCHIVED));
    }

    /** Active group by id (cached), or null when it does not exist or is archived. */
    @Override
    public Group findActive(long groupId) {

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
    @Override
    public Group findByRepository(String repositoryUrl, Long exceptId) {

        String wanted = RepositoryUrl.normalize(repositoryUrl);
        Long found = null;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SqlQueries.GROUPS_SELECT_REPOSITORIES)) {

            while (rs.next()) {
                long id = rs.getLong("ID");
                if (exceptId != null && exceptId == id) continue;
                if (wanted.equals(RepositoryUrl.normalize(rs.getString("REPOSITORY_URL")))) {
                    found = id;
                    break;
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Fail on SQL operation", e);
        }

        return found == null ? null : find(found);
    }

    /** Group by id, active or archived; null when it does not exist. */
    @Override
    public Group find(long groupId) {
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

    /** Copies of the groups with their number of members: the cached objects are never changed. */
    private List<Group> withMemberCounts(List<Group> groups) {
        Map<Long, Integer> counts = countMembers();
        List<Group> result = new ArrayList<>();
        for (Group g : groups) {
            Group copy = new Group(g.getId(), g.getName(), g.getRepositoryUrl(), g.getOwner(), g.getRepository());
            copy.setRepositoryPrivate(g.isRepositoryPrivate());
            copy.setArchivedAt(g.getArchivedAt());
            copy.setMemberCount(counts.getOrDefault(g.getId(), 0));
            result.add(copy);
        }
        return Collections.unmodifiableList(result);
    }

    /** Number of members of every group (id -> count); groups without members are absent. */
    private Map<Long, Integer> countMembers() {
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

    /**
     * Creates the group and sets its id. H2 has no unique constraint on the URL: the repository is checked here, and
     * the use case has already checked it before calling GitHub.
     *
     * @throws RepositoryTakenException when another group uses the repository
     */
    @Override
    public long create(Group group) {

        if (findByRepository(group.getRepositoryUrl(), null) != null) throw new RepositoryTakenException();

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
            group.setId(id);
            return id;

        } catch (SQLException e) {
            logger.error("Fail on insert group: {}", e.getMessage());
            throw new RuntimeException("Fail on SQL operation", e);
        } finally {
            invalidate(null);
        }
    }

    /**
     * Name and repository of an active group; members and aliases stay.
     *
     * @return false when it is not an active group
     * @throws RepositoryTakenException when another group uses the new repository
     */
    @Override
    public boolean update(Group group) {

        if (findByRepository(group.getRepositoryUrl(), group.getId()) != null) throw new RepositoryTakenException();
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
    @Override
    public boolean archive(long groupId) {
        return change(SqlQueries.GROUPS_ARCHIVE, groupId, "archived");
    }

    /** @return false when it is not an archived group */
    @Override
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
