package br.com.gradechecker.proxy;

import br.com.gradechecker.model.Contributor;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubProxyCacheTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubProxy proxy;

    @Before
    public void setup() {
        String baseUrl = "http://localhost:" + wireMockRule.port();
        proxy = new GitHubProxy(baseUrl, "2022-11-28");
        // enable cache with long TTL for test
        br.com.gradechecker.cache.CacheManager.rebuild(true, 60L, 100L);
        br.com.gradechecker.cache.CacheManager.invalidateAll();
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

        List<Contributor> first = proxy.getContributors("dummy-token", owner, repo);
        assertNotNull(first);
        assertEquals(1, first.size());

        // second call should hit cache; verify that WireMock received only 1 request
        List<Contributor> second = proxy.getContributors("dummy-token", owner, repo);
        assertNotNull(second);
        assertEquals(1, second.size());

        verify(1, getRequestedFor(urlPathEqualTo("/repos/" + owner + "/" + repo + "/contributors")));

    }

}

