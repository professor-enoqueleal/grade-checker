package br.com.gradechecker.proxy;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.model.Contributor;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

/** Contributors and users read from the GitHub REST API (openspec capability group-members). */
public class GitHubProxyTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubProxy proxy;

    @Before
    public void setUp() {
        proxy = new GitHubProxy("http://localhost:" + wireMockRule.port(), "2022-11-28");
        CacheManager.rebuild(true, 60L, 100L);
        CacheManager.invalidateAll();
    }

    @Test
    public void getContributors_returnsListWithUpTo100PerPageAndTheToken() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/contributors"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"login\":\"octocat\", \"id\": 1, \"avatar_url\": \"a\", \"html_url\": \"h\", \"contributions\": 42}]")));

        List<Contributor> contributors = proxy.getContributors("dummy-token", "octocat", "hello-world");

        assertEquals(1, contributors.size());
        assertEquals("octocat", contributors.get(0).getLogin());
        assertEquals(42, contributors.get(0).getContributions());
        assertEquals("h", contributors.get(0).getHtmlUrl());
        verify(getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/contributors"))
                .withQueryParam("per_page", equalTo("100"))
                .withHeader("Authorization", equalTo("Bearer dummy-token")));
    }

    @Test
    public void emptyRepositoryHasNoContributors() {
        stubFor(get(urlPathEqualTo("/repos/octocat/empty/contributors")).willReturn(aResponse().withStatus(204)));

        assertTrue(proxy.getContributors("t", "octocat", "empty").isEmpty());
    }

    @Test
    public void errorsAreGitHubApiExceptionsAndAreNotCached() {
        stubFor(get(urlPathEqualTo("/repos/octocat/gone/contributors")).willReturn(aResponse().withStatus(404)));

        for (int i = 0; i < 2; i++) {
            try {
                proxy.getContributors("t", "octocat", "gone");
                fail("expected GitHubApiException");
            } catch (GitHubApiException e) {
                assertEquals(404, e.getHttpStatus());
                assertEquals("Repository not found or not accessible with this token.", e.getMessage());
            }
        }
        verify(2, getRequestedFor(urlPathEqualTo("/repos/octocat/gone/contributors")));
    }

    @Test
    public void unauthorizedTokenIs401() {
        stubFor(get(urlPathEqualTo("/repos/octocat/x/contributors")).willReturn(aResponse().withStatus(401)));
        try {
            proxy.getContributors("t", "octocat", "x");
            fail("expected GitHubApiException");
        } catch (GitHubApiException e) {
            assertEquals(401, e.getHttpStatus());
        }
    }

    @Test
    public void getUserUsesTheTokenAndReturnsNullWhenItDoesNotExist() {
        stubFor(get(urlPathEqualTo("/users/THIAGOFP77")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"login\":\"thiagofp77\", \"id\": 7}")));
        stubFor(get(urlPathEqualTo("/users/nobody-here")).willReturn(aResponse().withStatus(404)));

        assertEquals("thiagofp77", proxy.getUser("THIAGOFP77", "tok").getLogin());
        assertNull(proxy.getUser("nobody-here", "tok"));
        assertNull(proxy.getUser(" ", "tok"));
        verify(getRequestedFor(urlPathEqualTo("/users/THIAGOFP77")).withHeader("Authorization", equalTo("Bearer tok")));
    }

    @Test(expected = GitHubApiException.class)
    public void getUserRateLimitIsAnError() {
        stubFor(get(urlPathEqualTo("/users/x")).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));
        proxy.getUser("x", "tok");
    }

    // ---------- repository of a group (openspec capability group-management)

    @Test
    public void getRepositoryReturnsTheSpellingOfGitHubWithTheToken() {
        stubFor(get(urlEqualTo("/repos/hugodev0711/clinica-pi")).withHeader("Authorization", equalTo("Bearer t"))
                .willReturn(okJson("{\"name\":\"clinica-pi\",\"html_url\":\"https://github.com/Hugodev0711/clinica-pi\","
                        + "\"private\":true,\"owner\":{\"login\":\"Hugodev0711\"}}")));

        br.com.gradechecker.model.GitHubRepository r = proxy.getRepository("hugodev0711", "clinica-pi", "t");

        assertEquals("https://github.com/Hugodev0711/clinica-pi", r.getHtmlUrl());
        assertEquals("Hugodev0711", r.getOwnerLogin());
        assertEquals("clinica-pi", r.getName());
        assertTrue(r.isPrivateRepository());
    }

    @Test
    public void missingRepositoryIsNullAndOtherErrorsAreExceptions() {
        stubFor(get(urlEqualTo("/repos/a/missing")).willReturn(notFound()));
        assertNull(proxy.getRepository("a", "missing", "t"));

        stubFor(get(urlEqualTo("/repos/a/forbidden")).willReturn(aResponse().withStatus(401).withBody("{\"message\":\"Bad credentials\"}")));
        try {
            proxy.getRepository("a", "forbidden", "t");
            fail("expected GitHubApiException");
        } catch (GitHubApiException e) {
            assertEquals(401, e.getHttpStatus());
        }
    }

}
