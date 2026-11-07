package br.com.gradechecker.repository;

import br.com.gradechecker.config.ConnectionPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Manual associations of git identities without account to logins of a group (openspec capability identity-aliases). */
public class IdentityAliasRepository {

    private final Logger logger = LogManager.getLogger(this.getClass());

    /** identity key -> login; empty on failure (the dashboard then works without manual associations). */
    public Map<String, String> findByGroupId(long groupId) {

        Map<String, String> aliases = new LinkedHashMap<>();

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.ALIASES_SELECT_BY_GROUP_ID)) {

            ps.setLong(1, groupId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) aliases.put(rs.getString("IDENTITY_KEY"), rs.getString("LOGIN"));
            }

        } catch (Exception e) {
            logger.error("Fail on select identity aliases of group {}: {}", groupId, e.getMessage());
        }

        return aliases;
    }

    /** Creates the association, or points an existing one of the same identity to the new login. */
    public void save(long groupId, String identityKey, String login, String createdBy) {

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.ALIASES_SAVE)) {

            ps.setLong(1, groupId);
            ps.setString(2, identityKey);
            ps.setString(3, login);
            ps.setString(4, createdBy);
            ps.executeUpdate();

            logger.info("Identity {} of group {} linked to {} by {}", identityKey, groupId, login, createdBy);

        } catch (Exception e) {
            throw new RuntimeException("Fail on save identity alias", e);
        }
    }

    /** @return true when an association was removed */
    public boolean delete(long groupId, String identityKey) {

        try (Connection connection = ConnectionPoolConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(SqlQueries.ALIASES_DELETE)) {

            ps.setLong(1, groupId);
            ps.setString(2, identityKey);
            boolean removed = ps.executeUpdate() > 0;

            if (removed) logger.info("Identity {} of group {} unlinked", identityKey, groupId);

            return removed;

        } catch (Exception e) {
            throw new RuntimeException("Fail on delete identity alias", e);
        }
    }

}
