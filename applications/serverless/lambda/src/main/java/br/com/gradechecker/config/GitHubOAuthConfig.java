package br.com.gradechecker.config;

import br.com.gradechecker.core.adapter.github.GitHubOAuthSettings;

import java.util.function.Supplier;

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

    /**
     * GITHUB_CLIENT_SECRET (local); otherwise the secret supplier set by the Lambda (SSM SecureString named in
     * GITHUB_CLIENT_SECRET_PARAMETER, capability serverless-backend-project).
     */
    public static String clientSecret() {
        String value = read(CLIENT_SECRET);
        if (value != null) return value;
        Supplier<String> supplier = secretSupplier;
        return supplier == null ? null : supplier.get();
    }

    private static volatile Supplier<String> secretSupplier;

    /** Where the client secret comes from when GITHUB_CLIENT_SECRET is not set (the SSM parameter in AWS). */
    public static void setSecretSupplier(Supplier<String> supplier) {
        secretSupplier = supplier;
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
