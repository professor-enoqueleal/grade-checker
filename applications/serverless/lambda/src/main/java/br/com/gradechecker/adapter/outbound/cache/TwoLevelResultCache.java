package br.com.gradechecker.adapter.outbound.cache;

import br.com.gradechecker.core.port.ResultCache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cache of the GitHub results given to grade-checker-core (port ResultCache, capability github-cache): first level in
 * the memory of the Lambda instance (Caffeine); the second level (DynamoDB) is shared by every instance. A miss in
 * memory reads the second level and keeps the value in memory; put writes both.
 *
 * Environment: GCACHE_ENABLED (default true), GCACHE_TTL_SECONDS (default 600), GCACHE_MAX_SIZE (default 10000).
 */
public final class TwoLevelResultCache implements ResultCache {

    private final Cache<String, Object> memory;
    private final ResultCache secondLevel;

    public TwoLevelResultCache(Duration ttl, long maxSize, ResultCache secondLevel) {
        this.memory = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
        this.secondLevel = secondLevel;
    }

    /** The cache configured by the environment, or no cache at all when GCACHE_ENABLED is false. */
    public static ResultCache fromEnvironment(Supplier<ResultCache> secondLevel) {
        if (!Boolean.parseBoolean(read("GCACHE_ENABLED", "true"))) return ResultCache.NONE;
        return new TwoLevelResultCache(Duration.ofSeconds(ttlSeconds()), number("GCACHE_MAX_SIZE", 10000L), secondLevel.get());
    }

    /** GCACHE_TTL_SECONDS, also the expiration of the second level. */
    public static long ttlSeconds() {
        return number("GCACHE_TTL_SECONDS", 600L);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Type type) {
        Object value = memory.getIfPresent(key);
        if (value == null) {
            value = secondLevel.get(key, type);
            if (value != null) memory.put(key, value);
        }
        return (T) value;
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) return;
        memory.put(key, value);
        secondLevel.put(key, value);
    }

    private static String read(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null) value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long number(String name, long fallback) {
        try {
            return Long.parseLong(read(name, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
