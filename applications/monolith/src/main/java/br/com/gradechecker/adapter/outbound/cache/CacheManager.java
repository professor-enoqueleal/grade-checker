package br.com.gradechecker.adapter.outbound.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple CacheManager backed by Caffeine.
 * Configuration via environment variables or system properties (for tests):
 *  - GCACHE_ENABLED (default: true)
 *  - GCACHE_TTL_SECONDS (default: 60)
 *  - GCACHE_MAX_SIZE (default: 10000)
 */
public final class CacheManager {

    private static boolean ENABLED;
    private static long TTL_SECONDS;
    private static long MAX_SIZE;
    private static Cache<String, Object> CACHE;

    private static final AtomicLong HIT_COUNT = new AtomicLong(0);
    private static final AtomicLong MISS_COUNT = new AtomicLong(0);

    static {
        initializeFromEnv();
    }

    private CacheManager() { }

    private static void initializeFromEnv() {
        String enabled = System.getProperty("GCACHE_ENABLED");
        if (enabled == null) enabled = System.getenv("GCACHE_ENABLED");
        ENABLED = enabled == null ? true : Boolean.parseBoolean(enabled);

        String ttl = System.getProperty("GCACHE_TTL_SECONDS");
        if (ttl == null) ttl = System.getenv("GCACHE_TTL_SECONDS");
        long ttlVal;
        try {
            ttlVal = ttl == null ? 60L : Long.parseLong(ttl);
        } catch (NumberFormatException e) {
            ttlVal = 60L;
        }
        TTL_SECONDS = ttlVal;

        String max = System.getProperty("GCACHE_MAX_SIZE");
        if (max == null) max = System.getenv("GCACHE_MAX_SIZE");
        long maxVal;
        try {
            maxVal = max == null ? 10000L : Long.parseLong(max);
        } catch (NumberFormatException e) {
            maxVal = 10000L;
        }
        MAX_SIZE = maxVal;

        if (ENABLED) {
            CACHE = Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
                    .maximumSize(MAX_SIZE)
                    .build();
        } else {
            CACHE = Caffeine.newBuilder().maximumSize(1).build(); // lightweight no-op
        }

        HIT_COUNT.set(0);
        MISS_COUNT.set(0);
    }

    /**
     * Rebuild the cache (used in tests to override env vars). null values mean keep defaults.
     */
    public static void rebuild(Boolean enabled, Long ttlSeconds, Long maxSize) {
        if (enabled != null) ENABLED = enabled;
        if (ttlSeconds != null) TTL_SECONDS = ttlSeconds;
        if (maxSize != null) MAX_SIZE = maxSize;

        if (ENABLED) {
            CACHE = Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
                    .maximumSize(MAX_SIZE)
                    .build();
        } else {
            CACHE = Caffeine.newBuilder().maximumSize(1).build();
        }

        HIT_COUNT.set(0);
        MISS_COUNT.set(0);
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Class<T> clazz) {
        if (!ENABLED) {
            MISS_COUNT.incrementAndGet();
            return null;
        }
        Object v = CACHE.getIfPresent(key);
        if (v == null) {
            MISS_COUNT.incrementAndGet();
            return null;
        } else {
            HIT_COUNT.incrementAndGet();
            return (T) v;
        }
    }

    public static void put(String key, Object value) {
        if (!ENABLED) return;
        if (value == null) return;
        CACHE.put(key, value);
    }

    public static void invalidate(String key) {
        CACHE.invalidate(key);
    }

    public static void invalidateAll() {
        CACHE.invalidateAll();
    }

    public static long getHitCount() {
        return HIT_COUNT.get();
    }

    public static long getMissCount() {
        return MISS_COUNT.get();
    }

    /**
     * Build a cache key from a prefix and optional parts joined by ':'.
     * The prefix should include any desired delimiter (for example "member.countByGroupId::").
     */
    public static String key(String prefix, Object... parts) {
        if (parts == null || parts.length == 0) return prefix;
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < parts.length; i++) {
            Object p = parts[i];
            sb.append(p == null ? "" : p.toString());
            if (i < parts.length - 1) sb.append(':');
        }
        return sb.toString();
    }

}
