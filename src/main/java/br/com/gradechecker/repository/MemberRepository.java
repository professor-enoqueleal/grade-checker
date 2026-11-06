package br.com.gradechecker.repository;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Member;
import br.com.gradechecker.repository.queries.Queries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class MemberRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    public void create(Member group) {

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SqlQueries.MEMBERS_INSERT)) {

            logger.info("success in database connection");

            preparedStatement.setString(1, group.getName());
            preparedStatement.setString(2, group.getGitHubUserName());
            preparedStatement.setInt(3, Integer.parseInt(group.getGroupId()));
            // GITHUB_ID
            preparedStatement.setNull(4, Types.INTEGER);
            // CREATED_MANUALLY -> false for programmatic inserts
            preparedStatement.setBoolean(5, false);
            // CONTRIBUTIONS default 0
            preparedStatement.setInt(6, 0);

            preparedStatement.execute();

            logger.info("success in insert member on database");

            // invalidate cache for member counts for this group
            try {
                long gid = Long.parseLong(group.getGroupId());
                CacheManager.invalidate(CacheManager.key("member.countByGroupId::", gid));
            } catch (NumberFormatException ignored) {
                // if parsing fails, skip invalidation
            }

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }

    public boolean createManual(Member member) {

        try (Connection connection = ConnectionPoolConfig.getConnection()) {

            // check existence
            try (PreparedStatement checkPs = connection.prepareStatement(SqlQueries.MEMBERS_COUNT_BY_GROUP_ID_AND_USERNAME)) {
                checkPs.setLong(1, Integer.parseInt(member.getGroupId()));
                checkPs.setString(2, member.getGitHubUserName());
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next() && rs.getInt("CNT") > 0) {
                        return false; // already exists
                    }
                }
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement(SqlQueries.MEMBERS_INSERT)) {

                logger.info("success in database connection");

                preparedStatement.setString(1, member.getName());
                preparedStatement.setString(2, member.getGitHubUserName());
                preparedStatement.setInt(3, Integer.parseInt(member.getGroupId()));
                // set github id if provided
                if (member.getGitHubId() != null) {
                    preparedStatement.setInt(4, member.getGitHubId());
                } else {
                    preparedStatement.setNull(4, Types.INTEGER);
                }
                // mark as created manually
                preparedStatement.setBoolean(5, true);
                // contributions start at zero
                preparedStatement.setInt(6, 0);

                preparedStatement.execute();

                logger.info("success in insert manual member on database");

                try {
                    long gid = Long.parseLong(member.getGroupId());
                    CacheManager.invalidate(CacheManager.key("member.countByGroupId::", gid));
                } catch (NumberFormatException ignored) {
                }

                return true;

            }

        } catch (Exception e) {
            logger.error("Fail on SQL operation {}", e.getMessage());
            throw new RuntimeException("Fail on SQL operation", e);
        }

    }

    public void createBulkIfNotExists(List<Member> members, long groupId) {

        if (members == null || members.isEmpty()) {
            return;
        }

        // collect usernames, skip null/empty
        List<String> usernames = members.stream()
                .map(Member::getGitHubUserName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (usernames.isEmpty()) {
            return;
        }

        // build dynamic IN clause placeholders
        String inClause = usernames.stream().map(s -> "?").collect(Collectors.joining(", "));
        String selectSqlTemplate = Queries.get("member.selectUsernamesByGroupIdAndUsernames");
        String selectSql = String.format(selectSqlTemplate, inClause);

        // find existing usernames
        Set<String> existing = new HashSet<>();

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectSql)) {

            ps.setLong(1, groupId);
            int idx = 2;
            for (String u : usernames) {
                ps.setString(idx++, u);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existing.add(rs.getString(1));
                }
            }

        } catch (SQLException e) {
            logger.error("Fail on SQL operation while selecting existing members {}", e.getMessage());
            throw new RuntimeException(e);
        }

        // filter missing
        List<Member> toInsert = new ArrayList<>();
        for (Member m : members) {
            String u = m.getGitHubUserName();
            if (u == null) continue;
            if (!existing.contains(u)) {
                toInsert.add(m);
            }
        }

        if (toInsert.isEmpty()) {
            return;
        }

        // batch insert new members
        String insertSql = SqlQueries.MEMBERS_INSERT;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(insertSql)) {

            for (Member m : toInsert) {
                ps.setString(1, m.getName());
                ps.setString(2, m.getGitHubUserName());
                ps.setLong(3, Long.parseLong(m.getGroupId()));
                // GITHUB_ID may be null
                if (m.getGitHubId() != null) {
                    ps.setInt(4, m.getGitHubId());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                // created via API (not manual)
                ps.setBoolean(5, false);
                // contributions default to 0; if Member has contributions field in future, set here
                ps.setInt(6, 0);
                ps.addBatch();
            }

            ps.executeBatch();

            // invalidate cache for this group
            CacheManager.invalidate(CacheManager.key("member.countByGroupId::", groupId));

        } catch (SQLException e) {
            logger.error("Fail on SQL operation while inserting members {}", e.getMessage());
            throw new RuntimeException(e);
        }

    }

    public int findById(Long id) {

        int totalOfMembers = 0;

        final String cacheKey = CacheManager.key("member.countByGroupId::", id);
        Integer cached = CacheManager.get(cacheKey, Integer.class);
        if (cached != null) {
            return cached;
        }

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SqlQueries.MEMBERS_COUNT_BY_GROUP_ID)) {

            logger.info("success in database connection");

            preparedStatement.setLong(1, id);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                logger.info("success in select groups");

                while (resultSet.next()) {

                    totalOfMembers = resultSet.getInt("TOTAL");

                }

            }

            // cache the result
            CacheManager.put(cacheKey, totalOfMembers);

            return totalOfMembers;

        } catch (SQLException e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            return totalOfMembers;

        }

    }

    /**
     * For existing members in MEMBERS table that were created manually or have null GITHUB_ID,
     * set the GITHUB_ID and CONTRIBUTIONS based on the provided contributors list.
     */
    public void syncMembersWithContributors(List<br.com.gradechecker.model.Contributor> contributors, long groupId) {

        if (contributors == null || contributors.isEmpty()) return;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.MEMBERS_UPDATE_GITHUB_ID_AND_CONTRIBUTIONS)) {

            for (br.com.gradechecker.model.Contributor c : contributors) {
                if (c.getLogin() == null) continue;
                ps.setInt(1, c.getId());
                ps.setInt(2, c.getContributions());
                ps.setLong(3, groupId);
                ps.setString(4, c.getLogin());
                ps.addBatch();
            }

            ps.executeBatch();

            // invalidate caches that depend on member counts or contributors
            CacheManager.invalidate(CacheManager.key("member.countByGroupId::", groupId));
            CacheManager.invalidate(CacheManager.key("contributors.findByGroupId::", groupId));

        } catch (Exception e) {
            logger.warn("Failed to sync members with contributors: {}", e.getMessage());
        }

    }

}
