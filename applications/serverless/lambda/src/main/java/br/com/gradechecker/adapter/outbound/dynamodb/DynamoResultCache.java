package br.com.gradechecker.adapter.outbound.dynamodb;

import br.com.gradechecker.core.port.ResultCache;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.n;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.s;

/**
 * Second cache level (capability serverless-github-cache), shared by every Lambda instance: CACHE#&lt;sha256 of the key&gt; in
 * the ephemeral table, JSON compressed with gzip (items are limited to 400 KB), expiring after GCACHE_TTL_SECONDS.
 * A failure only logs a warning: the request goes on to GitHub.
 */
public class DynamoResultCache implements ResultCache {

    private static final Logger logger = LogManager.getLogger(DynamoResultCache.class);
    /** The objects of the library are plain (no Gson annotations, no transient fields): default JSON by field name. */
    private static final Gson GSON = new Gson();

    private final DynamoClient dynamo;
    private final Clock clock;
    private final long ttlSeconds;

    public DynamoResultCache(DynamoClient dynamo, Clock clock, long ttlSeconds) {
        this.dynamo = dynamo;
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
    }

    static String pk(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return "CACHE#" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <T> T get(String key, Type type) {
        try {
            Map<String, AttributeValue> item = dynamo.client().getItem(GetItemRequest.builder().tableName(dynamo.ephemeralTable())
                    .key(Map.of("PK", s(pk(key)))).build()).item();
            if (item == null || item.isEmpty()) return null;
            if (DynamoClient.num(item, "expiresAt", 0) <= clock.instant().getEpochSecond()) return null;
            AttributeValue value = item.get("value");
            if (value == null || value.b() == null) return null;
            try (Reader reader = new InputStreamReader(new GZIPInputStream(
                    new ByteArrayInputStream(value.b().asByteArray())), StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, type);
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Cache read failed ({}): going to GitHub", e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, Object value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
            }
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", s(pk(key)));
            item.put("value", AttributeValue.builder().b(SdkBytes.fromByteArray(bytes.toByteArray())).build());
            item.put("expiresAt", n(clock.instant().getEpochSecond() + ttlSeconds));
            dynamo.client().putItem(PutItemRequest.builder().tableName(dynamo.ephemeralTable()).item(item).build());
        } catch (IOException | RuntimeException e) {
            logger.warn("Cache write failed ({}): the result is not shared", e.getMessage());
        }
    }
}
