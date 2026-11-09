package br.com.gradechecker.core.adapter.github;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Settings of the GitHub App built by the applications (capability core-boundaries). */
public class GitHubOAuthSettingsTest {

    @Test
    public void defaultsWithoutValuesAndNoTrailingSlash() {
        GitHubOAuthSettings empty = GitHubOAuthSettings.of(() -> null, () -> " ", () -> "", null);
        assertEquals("https://github.com", empty.baseUrl());
        assertEquals("https://api.github.com", empty.apiUrl());
        assertNull(empty.clientId());
        assertNull(empty.clientSecret());

        GitHubOAuthSettings custom = GitHubOAuthSettings.of(() -> "http://localhost:9/", () -> " http://api.local/ ", () -> " id ", () -> "s");
        assertEquals("http://localhost:9", custom.baseUrl());
        assertEquals("http://api.local", custom.apiUrl());
        assertEquals("id", custom.clientId());
        assertEquals("s", custom.clientSecret());
    }

    @Test
    public void valuesAreReadOnEveryCall() {
        AtomicReference<String> secret = new AtomicReference<>("first");
        GitHubOAuthSettings settings = GitHubOAuthSettings.of(null, null, () -> "id", secret::get);
        assertEquals("first", settings.clientSecret());
        secret.set("second");
        assertEquals("second", settings.clientSecret());
    }
}
