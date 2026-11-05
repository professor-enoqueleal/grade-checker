package br.com.gradechecker.proxy;

import br.com.gradechecker.model.Commit;
import br.com.gradechecker.model.CommitRequestBody;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubProxyCommitsCacheTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubProxy proxy;

    @Before
    public void setup() {
        String baseUrl = "http://localhost:" + wireMockRule.port();
        proxy = new GitHubProxy(baseUrl, "2022-11-28");
        br.com.gradechecker.cache.CacheManager.rebuild(true, 60L, 100L);
        br.com.gradechecker.cache.CacheManager.invalidateAll();
    }

    @Test
    public void getCommits_shouldUseCache_onSecondCall() {
        String owner = "octocat";
        String repo = "hello-world";

        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/commits"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"sha\":\"abc123\", \"message\": \"hello\"}]")
                        .withStatus(200)));

        CommitRequestBody body = new CommitRequestBody(repo, owner, "", "", "", "token", "");

        List<Commit> first = proxy.getCommits(body);
        assertNotNull(first);
        assertEquals(1, first.size());

        List<Commit> second = proxy.getCommits(body);
        assertNotNull(second);
        assertEquals(1, second.size());

        verify(1, getRequestedFor(urlPathEqualTo("/repos/" + owner + "/" + repo + "/commits")));
    }

}
