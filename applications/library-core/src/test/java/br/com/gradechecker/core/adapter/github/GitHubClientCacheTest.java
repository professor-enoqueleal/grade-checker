package br.com.gradechecker.core.adapter.github;

import br.com.gradechecker.core.github.Contributor;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubClientCacheTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubClient client;

    @Before
    public void setup() {
        String baseUrl = "http://localhost:" + wireMockRule.port();
        client = new GitHubClient(baseUrl, "2022-11-28", new MemoryCache());   // a fresh cache per test
    }

    @Test
    public void getContributors_shouldUseCache_onSecondCall() {
        String owner = "octocat";
        String repo = "hello-world";

        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/contributors"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"login\":\"octocat\", \"contributions\": 42}]")
                        .withStatus(200)));

        List<Contributor> first = client.getContributors(owner, repo, "dummy-token");
        assertNotNull(first);
        assertEquals(1, first.size());

        // second call should hit cache; verify that WireMock received only 1 request
        List<Contributor> second = client.getContributors(owner, repo, "dummy-token");
        assertNotNull(second);
        assertEquals(1, second.size());

        verify(1, getRequestedFor(urlPathEqualTo("/repos/" + owner + "/" + repo + "/contributors")));

    }

}

