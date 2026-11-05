package br.com.gradechecker.proxy;

import br.com.gradechecker.model.Commit;
import br.com.gradechecker.model.CommitRequestBody;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubProxyCommitDetailCacheTest {

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
    public void getCommitDetail_shouldUseCache_onSecondCall() {
        String owner = "octocat";
        String repo = "hello-world";
        String sha = "abc123";

        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/commits/" + sha))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sha\":\"abc123\", \"message\": \"hello\"}")
                        .withStatus(200)));

        CommitRequestBody body = new CommitRequestBody(repo, owner, "", "", "", "token", "");

        Commit first = proxy.getCommitDetail(body, sha);
        assertNotNull(first);
        assertEquals("abc123", first.getSha());

        Commit second = proxy.getCommitDetail(body, sha);
        assertNotNull(second);
        assertEquals("abc123", second.getSha());

        verify(1, getRequestedFor(urlPathEqualTo("/repos/" + owner + "/" + repo + "/commits/" + sha)));
    }

}
