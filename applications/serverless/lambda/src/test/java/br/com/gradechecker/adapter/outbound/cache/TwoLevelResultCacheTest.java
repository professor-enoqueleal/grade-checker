package br.com.gradechecker.adapter.outbound.cache;

import br.com.gradechecker.core.port.ResultCache;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class TwoLevelResultCacheTest {

    /** Second level in memory, counting the reads. */
    private static final class MapLevel implements ResultCache {
        final Map<String, Object> values = new HashMap<>();
        int reads;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Type type) {
            reads++;
            return (T) values.get(key);
        }

        @Override
        public void put(String key, Object value) {
            values.put(key, value);
        }
    }

    @After
    public void tearDown() {
        System.clearProperty("GCACHE_ENABLED");
        System.clearProperty("GCACHE_TTL_SECONDS");
    }

    @Test
    public void putWritesBothLevelsAndGetReadsMemoryFirst() {
        MapLevel second = new MapLevel();
        TwoLevelResultCache cache = new TwoLevelResultCache(Duration.ofMinutes(1), 100, second);

        assertNull(cache.get("k", String.class));
        cache.put("k", "v");

        assertEquals("v", second.values.get("k"));
        int reads = second.reads;
        assertEquals("v", cache.get("k", String.class));
        assertEquals("memory answered, second level untouched", reads, second.reads);
    }

    @Test
    public void missInMemoryReadsTheSecondLevelAndKeepsTheValue() {
        MapLevel second = new MapLevel();
        second.values.put("k", "shared");
        TwoLevelResultCache cache = new TwoLevelResultCache(Duration.ofMinutes(1), 100, second);

        assertEquals("shared", cache.get("k", String.class));
        assertEquals("shared", cache.get("k", String.class));
        assertEquals(1, second.reads);
    }

    @Test
    public void nullIsNeverCached() {
        MapLevel second = new MapLevel();
        TwoLevelResultCache cache = new TwoLevelResultCache(Duration.ofMinutes(1), 100, second);
        cache.put("k", null);
        assertTrue(second.values.isEmpty());
    }

    @Test
    public void memoryEntriesExpireAfterTheTtl() throws InterruptedException {
        TwoLevelResultCache cache = new TwoLevelResultCache(Duration.ofMillis(200), 100, ResultCache.NONE);
        cache.put("k", "v");
        assertEquals("v", cache.get("k", String.class));
        Thread.sleep(400);
        assertNull(cache.get("k", String.class));
    }

    @Test
    public void disabledByTheEnvironmentMeansNoCache() {
        System.setProperty("GCACHE_ENABLED", "false");
        assertSame(ResultCache.NONE, TwoLevelResultCache.fromEnvironment(() -> { throw new AssertionError("second level not needed"); }));
    }

    @Test
    public void ttlFromTheEnvironmentWithDefault() {
        assertEquals(600L, TwoLevelResultCache.ttlSeconds());
        System.setProperty("GCACHE_TTL_SECONDS", "30");
        assertEquals(30L, TwoLevelResultCache.ttlSeconds());
        System.setProperty("GCACHE_TTL_SECONDS", "abc");
        assertEquals(600L, TwoLevelResultCache.ttlSeconds());
    }
}
