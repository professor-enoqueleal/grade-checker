package br.com.gradechecker.config;

/**
 * Single source of the GitHub OAuth settings (openspec capability github-sign-in).
 * Values are read on every call: system property first, then environment variable.
 */
public final class GitHubOAuthConfig {

    public static final String BASE_URL = "GITHUB_BASE_URL";
    public static final String CLIENT_ID = "GITHUB_CLIENT_ID";
    public static final String CLIENT_SECRET = "GITHUB_CLIENT_SECRET";

    private static final String DEFAULT_BASE_URL = "https://github.com";

    private GitHubOAuthConfig() {
    }

    /** e.g. https://github.com (never ends with "/"). */
    public static String baseUrl() {
        String value = read(BASE_URL);
        if (value == null) return DEFAULT_BASE_URL;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static String clientId() {
        return read(CLIENT_ID);
    }

    public static String clientSecret() {
        return read(CLIENT_SECRET);
    }

    /** GitHub sign-in can start only when the client id is known. */
    public static boolean isConfigured() {
        return clientId() != null;
    }

    private static String read(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

}
