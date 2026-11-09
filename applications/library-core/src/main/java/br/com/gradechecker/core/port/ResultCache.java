package br.com.gradechecker.core.port;

import java.lang.reflect.Type;

/**
 * Cache of the GitHub results (capability core-boundaries), implemented by each application: Caffeine in the Jetty
 * version, Caffeine + DynamoDB in the serverless version. The library only puts complete results, with keys that
 * include a hash of the token. Implementations must never throw: a failure is a miss.
 */
public interface ResultCache {

    /** The value of the key, rebuilt as {@code type}, or null on a miss. */
    <T> T get(String key, Type type);

    void put(String key, Object value);

    /** No cache: every call goes to GitHub. */
    ResultCache NONE = new ResultCache() {
        @Override
        public <T> T get(String key, Type type) {
            return null;
        }

        @Override
        public void put(String key, Object value) {
            // nothing
        }
    };
}
