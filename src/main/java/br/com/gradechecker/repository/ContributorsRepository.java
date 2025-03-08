package br.com.gradechecker.repository;

import br.com.gradechecker.model.Contributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContributorsRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    public List<Contributor> getByGroupId(Long groupId) {

        final String sqlCommand = "SELECT * FROM CONTRIBUTORS WHERE GROUP_ID = ?";

        try {

            Connection connection = DriverManager.getConnection("jdbc:h2:~/test", "sa", "sa");

            logger.info("success in database connection");

            PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand);

            preparedStatement.setLong(1, groupId);

            ResultSet resultSet = preparedStatement.executeQuery();

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

            connection.close();

            return contributors;

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            return Collections.emptyList();

        }

    }

    public void create(List<Contributor> contributors, Long groupId) {

        String sqlCommand = "INSERT INTO CONTRIBUTORS (ID, LOGIN, AVATAR_URL, HTML_URL, CONTRIBUTIONS, GROUP_ID) VALUES (?, ?, ?, ?, ?, ?);";

        try {

            Connection connection = DriverManager.getConnection("jdbc:h2:~/test", "sa", "sa");

            logger.info("success in database connection");

            PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand);

            if(null != contributors) {

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
                connection.commit();

                logger.info("success in insert member on database");

            }

        } catch (Exception e) {

            logger.error("Fail on SQL operation {}", e.getMessage());

            throw new RuntimeException("Fail on SQL operation", e);

        }

    }
    
}
