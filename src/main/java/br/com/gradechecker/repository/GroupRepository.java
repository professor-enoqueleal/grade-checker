package br.com.gradechecker.repository;

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

        final String SQLCommand = "SELECT * FROM GROUPS";

        try {

            Connection connection = ConnectionPoolConfig.getConnection();

            logger.info("success in database connection");

            ResultSet resultSet = connection.prepareStatement(SQLCommand).executeQuery();

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

            connection.close();

            return groups;

        } catch (SQLException e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            return Collections.emptyList();

        }

    }

    public Group getGroupDetail(long groupId) {

        String SQLCommand = "SELECT * FROM GROUPS WHERE ID = ?";

        try {

            Connection connection = ConnectionPoolConfig.getConnection();

            logger.info("success in database connection");

            PreparedStatement preparedStatement = connection.prepareStatement(SQLCommand);

            preparedStatement.setLong(1, groupId);

            ResultSet resultSet = preparedStatement.executeQuery();

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

            connection.close();

            return group;

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }

    public void create(Group group){

        String SQLCommand = "INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES (?, ?, ?, ?);";

        try {

            Connection connection = ConnectionPoolConfig.getConnection();

            logger.info("success in database connection");

            PreparedStatement preparedStatement = connection.prepareStatement(SQLCommand);

            preparedStatement.setString(1, group.getName());
            preparedStatement.setString(2, group.getRepositoryUrl());
            preparedStatement.setString(3, group.getOwner());
            preparedStatement.setString(4, group.getRepository());

            preparedStatement.execute();

            logger.info("success in insert group on database");

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }

}
