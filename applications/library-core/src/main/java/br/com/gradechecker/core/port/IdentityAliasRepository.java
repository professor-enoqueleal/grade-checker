package br.com.gradechecker.core.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manual links of identities without a GitHub account ({@code email:...} or {@code name:...}) to students of a group
 * (capability core-ports).
 */
public interface IdentityAliasRepository {

    /** identity key -> login, ordered by key. */
    Map<String, String> findByGroupId(long groupId);

    /** Links (or re-links) the identity to the login. */
    void save(long groupId, String identityKey, String login, String createdBy);

    /** @return false when the identity was not linked */
    boolean delete(long groupId, String identityKey);

    /** Identity keys linked to the login (ignoring case). */
    default List<String> keysOfLogin(long groupId, String login) {
        List<String> keys = new ArrayList<>();
        String wanted = login.toLowerCase(Locale.ROOT);
        findByGroupId(groupId).forEach((key, value) -> {
            if (value != null && value.toLowerCase(Locale.ROOT).equals(wanted)) keys.add(key);
        });
        return keys;
    }

    default boolean existsForLogin(long groupId, String login) {
        return !keysOfLogin(groupId, login).isEmpty();
    }
}
