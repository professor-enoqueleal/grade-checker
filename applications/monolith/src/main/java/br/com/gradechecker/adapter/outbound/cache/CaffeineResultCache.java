package br.com.gradechecker.adapter.outbound.cache;

import br.com.gradechecker.core.port.ResultCache;

import java.lang.reflect.Type;

/**
 * Cache of the GitHub results given to grade-checker-core (port ResultCache): the Caffeine CacheManager of this
 * application, so the /admin/cache API keeps seeing and invalidating the GitHub entries, as before.
 */
public final class CaffeineResultCache implements ResultCache {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Type type) {
        return (T) CacheManager.get(key, Object.class);   // Caffeine keeps the objects themselves
    }

    @Override
    public void put(String key, Object value) {
        CacheManager.put(key, value);
    }
}
