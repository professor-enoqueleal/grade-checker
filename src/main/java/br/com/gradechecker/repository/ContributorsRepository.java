package br.com.gradechecker.repository;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Contributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContributorsRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    public List<Contributor> getByGroupId(Long groupId) {

        final String cacheKey = CacheManager.key("contributors.findByGroupId::", groupId);
        @SuppressWarnings("unchecked")
        List<Contributor> cached = CacheManager.get(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        final String sqlCommand = SqlQueries.CONTRIBUTORS_SELECT_BY_GROUP_ID;

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand)) {

            logger.info("success in database connection");

            preparedStatement.setLong(1, groupId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                logger.info("success in select contributors");

                if (!resultSet.isBeforeFirst()) {

                    return Collections.emptyList();

                }

                List<Contributor> contributors = new ArrayList<>();

                while (resultSet.next()) {

                    int id = resultSet.getInt("ID");
                    String login = resultSet.getString("LOGIN");
                    String avatarUrl = resultSet.getString("AVATAR_URL");
                    String htmlUrl = resultSet.getString("HTML_URL");
                    int contributions = resultSet.getInt("CONTRIBUTIONS");

                    Contributor contributor = new Contributor(login, id, avatarUrl, htmlUrl, contributions);

                    contributors.add(contributor);

                }

                CacheManager.put(cacheKey, contributors);

                return contributors;

            }

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            return Collections.emptyList();

        }

    }

    public void create(List<Contributor> contributors, Long groupId) {

        String sqlCommand = SqlQueries.CONTRIBUTORS_INSERT;

        if (contributors == null || contributors.isEmpty()) {
            return;
        }

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand)) {

            logger.info("success in database connection");

            for (Contributor contributor : contributors) {
                preparedStatement.setInt(1, contributor.getId());
                preparedStatement.setString(2, contributor.getLogin());
                preparedStatement.setString(3, contributor.getAvatarUrl());
                preparedStatement.setString(4, contributor.getHtmlUrl());
                preparedStatement.setInt(5, contributor.getContributions());
                preparedStatement.setLong(6, groupId);
                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();

            logger.info("success in insert contributors on database");

            // invalidate cache for this group
            CacheManager.invalidate(CacheManager.key("contributors.findByGroupId::", groupId));

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }

    public void createIfNotExists(Contributor contributor, Long groupId) {

        if (contributor == null) return;

        final String checkSql = SqlQueries.CONTRIBUTORS_EXISTS_BY_ID;
        final String insertSql = SqlQueries.CONTRIBUTORS_INSERT;

        try (Connection connection = ConnectionPoolConfig.getConnection()) {

            try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
                ps.setInt(1, contributor.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // already exists
                        return;
                    }
                }
            }

            // insert
            try (PreparedStatement ps2 = connection.prepareStatement(insertSql)) {
                ps2.setInt(1, contributor.getId());
                ps2.setString(2, contributor.getLogin());
                ps2.setString(3, contributor.getAvatarUrl());
                ps2.setString(4, contributor.getHtmlUrl());
                ps2.setInt(5, contributor.getContributions());
                ps2.setLong(6, groupId);
                ps2.execute();
            }

            CacheManager.invalidate(CacheManager.key("contributors.findByGroupId::", groupId));

        } catch (Exception e) {
            // ignore duplicate-key or other race conditions; log at debug
            logger.warn("Could not create contributor {}: {}", contributor.getLogin(), e.getMessage());
        }

    }

}
