package br.com.gradechecker.config;

import jakarta.servlet.http.HttpSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Platform administrators (openspec capability platform-admin): GitHub logins listed in
 * GRADE_CHECKER_ADMINS (system property first, then environment variable; comma separated,
 * case-insensitive). Without the variable nobody is an administrator. Read on every call.
 */
public final class AdminConfig {

    public static final String ADMINS = "GRADE_CHECKER_ADMINS";

    /** Session attribute with the GitHub login of the signed-in user (set at sign-in). */
    public static final String USER_LOGIN_ATTRIBUTE = "userLogin";

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

    /** False for a missing session or a session without userLogin (e.g. created before this change). */
    public static boolean isAdmin(HttpSession session) {
        return session != null && isAdmin(userLogin(session));
    }

    public static String userLogin(HttpSession session) {
        if (session == null) return null;
        Object login = session.getAttribute(USER_LOGIN_ATTRIBUTE);
        return login instanceof String ? (String) login : null;
    }

}
