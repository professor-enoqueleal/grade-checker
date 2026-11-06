package br.com.gradechecker.config;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.junit.Assert.*;

public class GitHubOAuthConfigTest {

    @Rule
    public EnvironmentVariables environment = new EnvironmentVariables();

    @After
    public void clearProperties() {
        System.clearProperty(GitHubOAuthConfig.CLIENT_ID);
    }

    @Test
    public void readsEnvironmentOnEveryCall() {
        environment.set("GITHUB_CLIENT_ID", "Iv1.first");
        assertEquals("Iv1.first", GitHubOAuthConfig.clientId());

        environment.set("GITHUB_CLIENT_ID", "Iv1.second");
        assertEquals("Iv1.second", GitHubOAuthConfig.clientId());
    }

    @Test
    public void systemPropertyWinsOverEnvironment() {
        environment.set("GITHUB_CLIENT_ID", "Iv1.env");
        System.setProperty(GitHubOAuthConfig.CLIENT_ID, "Iv1.prop");

        assertEquals("Iv1.prop", GitHubOAuthConfig.clientId());
    }

    @Test
    public void blankMeansNotConfigured() {
        environment.set("GITHUB_CLIENT_ID", "  ");
        assertNull(GitHubOAuthConfig.clientId());
        assertFalse(GitHubOAuthConfig.isConfigured());
    }

    @Test
    public void baseUrlDefaultsToGitHubWithoutTrailingSlash() {
        environment.set("GITHUB_BASE_URL", null);
        assertEquals("https://github.com", GitHubOAuthConfig.baseUrl());

        environment.set("GITHUB_BASE_URL", "https://github.com/");
        assertEquals("https://github.com", GitHubOAuthConfig.baseUrl());
    }
}
