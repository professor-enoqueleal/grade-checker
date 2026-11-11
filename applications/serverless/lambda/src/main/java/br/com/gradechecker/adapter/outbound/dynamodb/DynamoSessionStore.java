package br.com.gradechecker.adapter.outbound.dynamodb;

import br.com.gradechecker.config.SessionConfig;
import br.com.gradechecker.session.Session;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.n;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.s;

/**
 * Sessions and OAuth states in the ephemeral table (capability serverless-session-auth). Items expire with the table TTL
 * (attribute expiresAt); since the TTL deletes late, every read also checks expiresAt.
 *
 * A session has two deadlines: expiresAt, the idle deadline, moved forward by every use, and maxExpiresAt, the absolute
 * one (sign-in + maximum). Sessions created before maxExpiresAt existed use their expiresAt as the absolute deadline.
 */
public class DynamoSessionStore {

    public static final long STATE_SECONDS = 10 * 60;
    /** an extension is stored only when it moves the idle deadline by at least this much (one write per minute) */
    static final long MIN_EXTENSION_SECONDS = 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DynamoClient dynamo;
    private final Clock clock;
    private final long idleSeconds;
    private final long maxSeconds;

    /** Lifetimes from SESSION_IDLE_MINUTES and SESSION_MAX_HOURS. */
    public DynamoSessionStore(DynamoClient dynamo, Clock clock) {
        this(dynamo, clock, SessionConfig.idleSeconds(), SessionConfig.maxSeconds());
    }

    public DynamoSessionStore(DynamoClient dynamo, Clock clock, long idleSeconds, long maxSeconds) {
        this.dynamo = dynamo;
        this.clock = clock;
        this.maxSeconds = maxSeconds;
        this.idleSeconds = Math.min(idleSeconds, maxSeconds);
    }

    /** Absolute lifetime of a session (Max-Age of the cookie). */
    public long maxSeconds() {
        return maxSeconds;
    }

    /**
     * New idle deadline after a use at {@code now}, or -1 when nothing needs to be stored (the deadline would move by
     * less than a minute).
     */
    static long extendedDeadline(long now, long expiresAt, long maxExpiresAt, long idleSeconds) {
        long next = Math.min(now + idleSeconds, maxExpiresAt);
        return next - expiresAt >= MIN_EXTENSION_SECONDS ? next : -1;
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

    /** New session for the login; the GitHub token stays here, never in the browser. */
    public Session create(String login, String accessToken) {
        String id = randomId();
        long now = now();
        Map<String, AttributeValue> item = key("SESSION#" + id);
        item.put("login", s(login));
        item.put("token", s(accessToken));
        item.put("expiresAt", n(now + idleSeconds));
        item.put("maxExpiresAt", n(now + maxSeconds));
        dynamo.client().putItem(PutItemRequest.builder().tableName(dynamo.ephemeralTable()).item(item).build());
        return new Session(id, login, accessToken);
    }

    /** The session, or null when missing or expired; a valid session is a use, which moves the idle deadline. */
    public Session find(String id) {
        if (id == null || id.isEmpty() || id.length() > 100) return null;
        Map<String, AttributeValue> item = dynamo.client().getItem(GetItemRequest.builder().tableName(dynamo.ephemeralTable())
                .key(key("SESSION#" + id)).consistentRead(true).build()).item();
        if (item == null || item.isEmpty()) return null;
        long now = now();
        long expiresAt = DynamoClient.num(item, "expiresAt", 0);
        if (expiresAt <= now) return null;
        long next = extendedDeadline(now, expiresAt, DynamoClient.num(item, "maxExpiresAt", expiresAt), idleSeconds);
        if (next > 0) extend(id, next);
        return new Session(id, DynamoClient.str(item, "login"), DynamoClient.str(item, "token"));
    }

    /** Moves the idle deadline; never recreates a session deleted meanwhile (sign out). */
    private void extend(String id, long expiresAt) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":e", n(expiresAt));
        try {
            dynamo.client().updateItem(UpdateItemRequest.builder().tableName(dynamo.ephemeralTable()).key(key("SESSION#" + id))
                    .updateExpression("SET expiresAt = :e").conditionExpression("attribute_exists(PK)")
                    .expressionAttributeValues(values).build());
        } catch (ConditionalCheckFailedException e) {
            // signed out in the meantime: nothing to extend
        }
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
