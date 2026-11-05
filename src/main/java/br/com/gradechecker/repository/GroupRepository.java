package br.com.gradechecker.repository;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Group;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroupRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    public List<Group> findAll() {

        final String cacheKey = CacheManager.key("group.findAll::");
        @SuppressWarnings("unchecked")
        List<Group> cached = CacheManager.get(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.GROUPS_SELECT_ALL);
             ResultSet resultSet = ps.executeQuery()) {

            logger.info("success in database connection");

            logger.info("success in select groups");

            List<Group> groups = new ArrayList<>();

            while (resultSet.next()) {

                long id = resultSet.getLong("id");
                String groupName = resultSet.getString("name");
                String repositoryUrl = resultSet.getString("repository_url");
                String repositoryOwner = resultSet.getString("repository_owner");
                String repositoryName = resultSet.getString("repository_name");

                Group group = new Group(id, groupName, repositoryUrl, repositoryOwner, repositoryName);

                groups.add(group);

            }

            CacheManager.put(cacheKey, groups);

            return groups;

        } catch (SQLException e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            return Collections.emptyList();

        }

    }

    public Group getGroupDetail(long groupId) {

        final String cacheKey = CacheManager.key("group.findById::", groupId);
        Group cached = CacheManager.get(cacheKey, Group.class);
        if (cached != null) {
            return cached;
        }

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SqlQueries.GROUPS_SELECT_BY_ID)) {

            logger.info("success in database connection");

            preparedStatement.setLong(1, groupId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                logger.info("success in select group");

                Group group = new Group();

                while (resultSet.next()) {

                    long id = resultSet.getLong("id");
                    String groupName = resultSet.getString("name");
                    String repositoryUrl = resultSet.getString("repository_url");
                    String repositoryOwner = resultSet.getString("repository_owner");
                    String repositoryName = resultSet.getString("repository_name");

                    group.setId(id);
                    group.setName(groupName);
                    group.setRepositoryUrl(repositoryUrl);
                    group.setOwner(repositoryOwner);
                    group.setRepository(repositoryName);

                }

                CacheManager.put(cacheKey, group);

                return group;

            }

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }

    public void create(Group group){

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SqlQueries.GROUPS_INSERT)) {

            logger.info("success in database connection");

            preparedStatement.setString(1, group.getName());
            preparedStatement.setString(2, group.getRepositoryUrl());
            preparedStatement.setString(3, group.getOwner());
            preparedStatement.setString(4, group.getRepository());

            preparedStatement.execute();

            logger.info("success in insert group on database");

            // invalidate related caches
            CacheManager.invalidate(CacheManager.key("group.findAll::"));

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }

}
