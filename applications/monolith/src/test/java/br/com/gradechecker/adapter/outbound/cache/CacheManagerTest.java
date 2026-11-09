package br.com.gradechecker.adapter.outbound.cache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CacheManagerTest {

    @Before
    public void setup() {
        // ensure a fresh cache enabled with small TTL for tests
        CacheManager.rebuild(true, 2L, 100L);
    }

    @After
    public void tearDown() {
        CacheManager.invalidateAll();
    }

    @Test
    public void testPutAndGetCountsHitsAndMisses() {
        String key = "test.key::1";

        // initially miss
        assertNull(CacheManager.get(key, String.class));
        long miss1 = CacheManager.getMissCount();

        CacheManager.put(key, "value");

        String v = CacheManager.get(key, String.class);
        assertEquals("value", v);

        long hit = CacheManager.getHitCount();
        long miss2 = CacheManager.getMissCount();

        assertTrue(hit >= 1);
        assertTrue(miss2 >= miss1);
    }

    @Test
    public void testInvalidateAndExpiration() throws InterruptedException {
        String key = "test.key::expire";
        CacheManager.put(key, "v");
        assertNotNull(CacheManager.get(key, String.class));

        CacheManager.invalidate(key);
        assertNull(CacheManager.get(key, String.class));

        // test TTL expiration working (ttl set to 2 seconds in setup)
        CacheManager.put(key, "v2");
        assertNotNull(CacheManager.get(key, String.class));
        Thread.sleep(2500);
        assertNull(CacheManager.get(key, String.class));
    }

    @Test
    public void testDisabledCache() {
        CacheManager.rebuild(false, 60L, 100L);
        String key = "disabled::1";
        CacheManager.put(key, "x");
        assertNull(CacheManager.get(key, String.class));
    }

}

