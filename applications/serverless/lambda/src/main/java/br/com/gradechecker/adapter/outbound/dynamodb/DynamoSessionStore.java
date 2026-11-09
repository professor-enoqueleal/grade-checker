package br.com.gradechecker.adapter.outbound.dynamodb;

import br.com.gradechecker.session.Session;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.n;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.s;

/**
 * Sessions and OAuth states in the ephemeral table (capability session-auth). Items expire with the table TTL
 * (attribute expiresAt); since the TTL deletes late, every read also checks expiresAt.
 */
public class DynamoSessionStore {

    public static final long SESSION_SECONDS = 8 * 60 * 60;
    public static final long STATE_SECONDS = 10 * 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DynamoClient dynamo;
    private final Clock clock;

    public DynamoSessionStore(DynamoClient dynamo, Clock clock) {
        this.dynamo = dynamo;
        this.clock = clock;
    }

    /** 256 random bits, base64url without padding. */
    public static String randomId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private static Map<String, AttributeValue> key(String pk) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s(pk));
        return key;
    }

    /** New session of 8 hours for the login; the GitHub token stays here, never in the browser. */
    public Session create(String login, String accessToken) {
        String id = randomId();
        long expiresAt = now() + SESSION_SECONDS;
        Map<String, AttributeValue> item = key("SESSION#" + id);
        item.put("login", s(login));
        item.put("token", s(accessToken));
        item.put("expiresAt", n(expiresAt));
        dynamo.client().putItem(PutItemRequest.builder().tableName(dynamo.ephemeralTable()).item(item).build());
        return new Session(id, login, accessToken);
    }

    /** The session, or null when missing or expired. */
    public Session find(String id) {
        if (id == null || id.isEmpty() || id.length() > 100) return null;
        Map<String, AttributeValue> item = dynamo.client().getItem(GetItemRequest.builder().tableName(dynamo.ephemeralTable())
                .key(key("SESSION#" + id)).consistentRead(true).build()).item();
        if (item == null || item.isEmpty()) return null;
        if (DynamoClient.num(item, "expiresAt", 0) <= now()) return null;
        return new Session(id, DynamoClient.str(item, "login"), DynamoClient.str(item, "token"));
    }

    public void delete(String id) {
        if (id == null || id.isEmpty() || id.length() > 100) return;
        dynamo.client().deleteItem(DeleteItemRequest.builder().tableName(dynamo.ephemeralTable()).key(key("SESSION#" + id)).build());
    }

    /** Stores a new OAuth state for 10 minutes and returns it. */
    public String createState() {
        String state = randomId();
        Map<String, AttributeValue> item = key("STATE#" + state);
        item.put("expiresAt", n(now() + STATE_SECONDS));
        dynamo.client().putItem(PutItemRequest.builder().tableName(dynamo.ephemeralTable()).item(item).build());
        return state;
    }

    /** Deletes the state (single use) and tells whether it existed and had not expired. */
    public boolean consumeState(String state) {
        if (state == null || state.isEmpty() || state.length() > 100) return false;
        Map<String, AttributeValue> old = dynamo.client().deleteItem(DeleteItemRequest.builder()
                .tableName(dynamo.ephemeralTable()).key(key("STATE#" + state)).returnValues(ReturnValue.ALL_OLD).build()).attributes();
        return old != null && !old.isEmpty() && DynamoClient.num(old, "expiresAt", 0) > now();
    }
}
