package br.com.gradechecker.core.adapter.github;

import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.github.GitHubRepository;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

/** Contributors and users read from the GitHub REST API (openspec capability group-members). */
public class GitHubClientTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubClient client;

    @Before
    public void setUp() {
        client = new GitHubClient("http://localhost:" + wireMockRule.port(), "2022-11-28", new MemoryCache());
    }

    @Test
    public void getContributors_returnsListWithUpTo100PerPageAndTheToken() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/contributors"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"login\":\"octocat\", \"id\": 1, \"avatar_url\": \"a\", \"html_url\": \"h\", \"contributions\": 42}]")));

        List<Contributor> contributors = client.getContributors("octocat", "hello-world", "dummy-token");

        assertEquals(1, contributors.size());
        assertEquals("octocat", contributors.get(0).getLogin());
        assertEquals(42, contributors.get(0).getContributions());
        assertEquals("h", contributors.get(0).getHtmlUrl());
        assertEquals("a", contributors.get(0).getAvatarUrl());
        assertEquals(1, contributors.get(0).getId());
        verify(getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/contributors"))
                .withQueryParam("per_page", equalTo("100"))
                .withHeader("Authorization", equalTo("Bearer dummy-token")));
    }

    @Test
    public void emptyRepositoryHasNoContributors() {
        stubFor(get(urlPathEqualTo("/repos/octocat/empty/contributors")).willReturn(aResponse().withStatus(204)));

        assertTrue(client.getContributors("octocat", "empty", "t").isEmpty());
    }

    @Test
    public void errorsAreGitHubApiExceptionsAndAreNotCached() {
        stubFor(get(urlPathEqualTo("/repos/octocat/gone/contributors")).willReturn(aResponse().withStatus(404)));

        for (int i = 0; i < 2; i++) {
            try {
                client.getContributors("octocat", "gone", "t");
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
            client.getContributors("octocat", "x", "t");
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

        assertEquals("thiagofp77", client.getUser("THIAGOFP77", "tok").getLogin());
        assertNull(client.getUser("nobody-here", "tok"));
        assertNull(client.getUser(" ", "tok"));
        verify(getRequestedFor(urlPathEqualTo("/users/THIAGOFP77")).withHeader("Authorization", equalTo("Bearer tok")));
    }

    @Test(expected = GitHubApiException.class)
    public void getUserRateLimitIsAnError() {
        stubFor(get(urlPathEqualTo("/users/x")).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));
        client.getUser("x", "tok");
    }

    // ---------- repository of a group (openspec capability group-management)

    @Test
    public void getRepositoryReturnsTheSpellingOfGitHubWithTheToken() {
        stubFor(get(urlEqualTo("/repos/hugodev0711/clinica-pi")).withHeader("Authorization", equalTo("Bearer t"))
                .willReturn(okJson("{\"name\":\"clinica-pi\",\"html_url\":\"https://github.com/Hugodev0711/clinica-pi\","
                        + "\"private\":true,\"owner\":{\"login\":\"Hugodev0711\"}}")));

        GitHubRepository r = client.getRepository("hugodev0711", "clinica-pi", "t");

        assertEquals("https://github.com/Hugodev0711/clinica-pi", r.getHtmlUrl());
        assertEquals("Hugodev0711", r.getOwnerLogin());
        assertEquals("clinica-pi", r.getName());
        assertTrue(r.isPrivateRepository());
    }

    @Test
    public void missingRepositoryIsNullAndOtherErrorsAreExceptions() {
        stubFor(get(urlEqualTo("/repos/a/missing")).willReturn(notFound()));
        assertNull(client.getRepository("a", "missing", "t"));

        stubFor(get(urlEqualTo("/repos/a/forbidden")).willReturn(aResponse().withStatus(401).withBody("{\"message\":\"Bad credentials\"}")));
        try {
            client.getRepository("a", "forbidden", "t");
            fail("expected GitHubApiException");
        } catch (GitHubApiException e) {
            assertEquals(401, e.getHttpStatus());
        }
    }

}
