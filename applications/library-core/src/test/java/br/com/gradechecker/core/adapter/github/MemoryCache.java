package br.com.gradechecker.core.adapter.github;

import br.com.gradechecker.core.port.ResultCache;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/** ResultCache in memory for the tests (the applications bring Caffeine, or Caffeine + DynamoDB). */
public class MemoryCache implements ResultCache {

    final Map<String, Object> values = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Type type) {
        return (T) values.get(key);
    }

    @Override
    public void put(String key, Object value) {
        values.put(key, value);
    }
}
