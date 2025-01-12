package br.com.gradechecker.repository;

import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.Member;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemberRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    public void create(Member group) {

        String SQLCommand = "INSERT INTO MEMBERS (NAME, GITHUB_USERNAME, GROUP_ID) VALUES (?, ?, ?)";

        try {

            Connection connection = DriverManager.getConnection("jdbc:h2:~/test", "sa", "sa");

            logger.info("success in database connection");

            PreparedStatement preparedStatement = connection.prepareStatement(SQLCommand);

            preparedStatement.setString(1, group.getName());
            preparedStatement.setString(2, group.getGitHubUserName());
            preparedStatement.setInt(3, Integer.parseInt(group.getGroupId()));

            preparedStatement.execute();

            logger.info("success in insert member on database");

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }

    public int findById(Long id) {

        int totalOfMembers = 0;

        final String SQLCommand = "SELECT COUNT(*) AS TOTAL FROM MEMBERS WHERE GROUP_ID = ?";

        try {

            Connection connection = DriverManager.getConnection("jdbc:h2:~/test", "sa", "sa");

            logger.info("success in database connection");

            PreparedStatement preparedStatement = connection.prepareStatement(SQLCommand);

            preparedStatement.setLong(1, id);

            ResultSet resultSet = preparedStatement.executeQuery();

            logger.info("success in select groups");

            List<Group> groups = new ArrayList<>();

            while (resultSet.next()) {

                totalOfMembers = resultSet.getInt("TOTAL");

            }

            connection.close();

            return totalOfMembers;

        } catch (SQLException e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            return totalOfMembers;

        }

    }

}
