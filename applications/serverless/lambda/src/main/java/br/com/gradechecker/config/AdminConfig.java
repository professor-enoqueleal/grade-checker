package br.com.gradechecker.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Platform administrators (capability session-auth): GitHub logins listed in GRADE_CHECKER_ADMINS (system property
 * first, then environment variable; comma separated, case-insensitive). Without the variable nobody is an
 * administrator. Read on every call.
 *
 * Copied from the Jetty version without the HttpSession methods: in the serverless version the session lives in
 * DynamoDB, and authorization is computed from the login of the session with {@link #isAdmin(String)}.
 */
public final class AdminConfig {

    public static final String ADMINS = "GRADE_CHECKER_ADMINS";

    private AdminConfig() {
    }

    /** Lower-case logins of the administrators; empty when the variable is missing or blank. */
    public static Set<String> admins() {
        String value = System.getProperty(ADMINS);
        if (value == null || value.trim().isEmpty()) value = System.getenv(ADMINS);
        if (value == null || value.trim().isEmpty()) return Collections.emptySet();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isAdmin(String login) {
        return login != null && !login.trim().isEmpty() && admins().contains(login.trim().toLowerCase(Locale.ROOT));
    }

}
