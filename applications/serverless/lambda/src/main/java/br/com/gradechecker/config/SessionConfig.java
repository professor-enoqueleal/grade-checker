package br.com.gradechecker.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Session lifetime (openspec capability serverless-session-auth): the session ends after SESSION_IDLE_MINUTES without
 * API calls (default 30) and, in any case, SESSION_MAX_HOURS after the sign-in (default 8, at most 8: the token of the
 * GitHub App expires in 8 hours and is not refreshed). System property first, then environment variable; an invalid
 * value falls back to the default with a warning. The idle time is never longer than the maximum.
 */
public final class SessionConfig {

    public static final String IDLE_MINUTES = "SESSION_IDLE_MINUTES";
    public static final String MAX_HOURS = "SESSION_MAX_HOURS";

    static final long DEFAULT_IDLE_MINUTES = 30;
    static final long DEFAULT_MAX_HOURS = 8;
    static final long MIN_IDLE_MINUTES = 5;
    static final long MAX_IDLE_MINUTES = 480;
    static final long MAX_MAX_HOURS = 8;

    private static final Logger logger = LogManager.getLogger(SessionConfig.class);

    private SessionConfig() {
    }

    public static long maxSeconds() {
        return read(MAX_HOURS, DEFAULT_MAX_HOURS, 1, MAX_MAX_HOURS) * 60 * 60;
    }

    public static long idleSeconds() {
        return Math.min(read(IDLE_MINUTES, DEFAULT_IDLE_MINUTES, MIN_IDLE_MINUTES, MAX_IDLE_MINUTES) * 60, maxSeconds());
    }

    private static long read(String name, long fallback, long min, long max) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            long n = Long.parseLong(value.trim());
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException e) {
            // falls through to the warning
        }
        logger.warn("{}={} is not between {} and {}; using {}", name, value.trim(), min, max, fallback);
        return fallback;
    }
}
