package br.com.gradechecker.adapter.inbound.http;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small builder for JSON objects in a fixed order: Maps.of("id", 3, "name", "x"). */
public final class Maps {

    private Maps() {
    }

    public static Map<String, Object> of(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return map;
    }
}
