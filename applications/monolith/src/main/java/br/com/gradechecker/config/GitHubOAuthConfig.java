package br.com.gradechecker.config;

import br.com.gradechecker.core.adapter.github.GitHubOAuthSettings;

/**
 * Single source of the GitHub OAuth settings (openspec capability github-sign-in).
 * Values are read on every call: system property first, then environment variable.
 */
public final class GitHubOAuthConfig {

    public static final String BASE_URL = "GITHUB_BASE_URL";
    public static final String CLIENT_ID = "GITHUB_CLIENT_ID";
    public static final String CLIENT_SECRET = "GITHUB_CLIENT_SECRET";
    public static final String API_URL = "GITHUB_API_URL";

    private static final String DEFAULT_BASE_URL = "https://github.com";
    private static final String DEFAULT_API_URL = "https://api.github.com";

    private GitHubOAuthConfig() {
    }

    /** e.g. https://github.com (never ends with "/"). */
    public static String baseUrl() {
        String value = read(BASE_URL);
        if (value == null) return DEFAULT_BASE_URL;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** REST API root, e.g. https://api.github.com (never ends with "/"); used for GET /user at sign-in. */
    public static String apiUrl() {
        String value = read(API_URL);
        if (value == null) return DEFAULT_API_URL;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static String clientId() {
        return read(CLIENT_ID);
    }

    public static String clientSecret() {
        return read(CLIENT_SECRET);
    }

    /** Settings for the GitHub client of grade-checker-core, read from this class on every call. */
    public static GitHubOAuthSettings settings() {
        return GitHubOAuthSettings.of(
                GitHubOAuthConfig::baseUrl, GitHubOAuthConfig::apiUrl, GitHubOAuthConfig::clientId, GitHubOAuthConfig::clientSecret);
    }

    private static String read(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

}
