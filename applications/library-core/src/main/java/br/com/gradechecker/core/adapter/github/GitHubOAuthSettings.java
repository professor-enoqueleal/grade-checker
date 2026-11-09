package br.com.gradechecker.core.adapter.github;

import java.util.function.Supplier;

/**
 * Settings of the GitHub App and of the GitHub API used at sign-in (capability core-boundaries), built by each
 * application from its environment (the Lambda also reads the client secret from SSM). Values are read on every call,
 * so a changed environment is seen without a restart, as before.
 */
public final class GitHubOAuthSettings {

    public static final String DEFAULT_BASE_URL = "https://github.com";
    public static final String DEFAULT_API_URL = "https://api.github.com";

    private final Supplier<String> baseUrl;
    private final Supplier<String> apiUrl;
    private final Supplier<String> clientId;
    private final Supplier<String> clientSecret;

    private GitHubOAuthSettings(Supplier<String> baseUrl, Supplier<String> apiUrl, Supplier<String> clientId,
                                Supplier<String> clientSecret) {
        this.baseUrl = baseUrl;
        this.apiUrl = apiUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Each supplier may return null or blank: defaults for the URLs, "not configured" for the client. */
    public static GitHubOAuthSettings of(Supplier<String> baseUrl, Supplier<String> apiUrl, Supplier<String> clientId,
                                         Supplier<String> clientSecret) {
        return new GitHubOAuthSettings(baseUrl, apiUrl, clientId, clientSecret);
    }

    /** OAuth base, e.g. https://github.com (never ends with "/"). */
    public String baseUrl() {
        return url(baseUrl, DEFAULT_BASE_URL);
    }

    /** REST API root, e.g. https://api.github.com (never ends with "/"). */
    public String apiUrl() {
        return url(apiUrl, DEFAULT_API_URL);
    }

    public String clientId() {
        return value(clientId);
    }

    public String clientSecret() {
        return value(clientSecret);
    }

    private static String url(Supplier<String> supplier, String fallback) {
        String value = value(supplier);
        if (value == null) return fallback;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String value(Supplier<String> supplier) {
        String value = supplier == null ? null : supplier.get();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
