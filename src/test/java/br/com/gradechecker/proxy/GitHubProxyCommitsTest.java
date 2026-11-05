package br.com.gradechecker.proxy;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.CommitsPage;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubProxyCommitsTest {

    private static final String PATH = "/repos/octocat/hello-world/commits";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubProxy proxy;

    @Before
    public void setUp() {
        proxy = new GitHubProxy("http://localhost:" + wireMockRule.port(), "2022-11-28");
        CacheManager.rebuild(true, 60L, 100L);
        CacheManager.invalidateAll();
        System.clearProperty(GitHubProxy.MAX_COMMITS_PROPERTY);
    }

    @After
    public void tearDown() {
        System.clearProperty(GitHubProxy.MAX_COMMITS_PROPERTY);
    }

    private static CommitRequestBody request(String since, String until) {
        return new CommitRequestBody("hello-world", "octocat", "alice", since, until, "token", "1");
    }

    private static String commits(int count, int offset) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"sha\":\"sha").append(offset + i).append("\",\"html_url\":\"https://github.com/octocat/hello-world/commit/sha")
              .append(offset + i).append("\",\"commit\":{\"message\":\"msg\",\"committer\":{\"date\":\"2026-03-01T14:05:00Z\"}}}");
        }
        return sb.append(']').toString();
    }

    private static String link(int next) {
        return "<http://x" + PATH + "?page=" + next + ">; rel=\"next\"";
    }

    @Test
    public void sendsPeriodAuthorAndTokenOnlyWhenDefined() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(commits(1, 0))));

        CommitsPage page = proxy.getCommits(request("2026-08-13T03:00:00Z", "2026-09-18T02:59:59Z"));

        assertEquals(1, page.getCommits().size());
        assertEquals("https://github.com/octocat/hello-world/commit/sha0", page.getCommits().get(0).getHtmlUrl());
        verify(getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer token"))
                .withQueryParam("author", equalTo("alice"))
                .withQueryParam("since", equalTo("2026-08-13T03:00:00Z"))
                .withQueryParam("until", equalTo("2026-09-18T02:59:59Z"))
                .withQueryParam("per_page", equalTo("100"))
                .withQueryParam("page", equalTo("1")));
    }

    @Test
    public void allPeriodDoesNotSendSince() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("[]")));

        proxy.getCommits(request(null, "2026-11-05T15:01:00Z"));

        verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("since", absent()));
    }

    @Test
    public void followsLinkHeaderAcrossPages() {
        stubFor(get(urlPathEqualTo(PATH)).withQueryParam("page", equalTo("1"))
                .willReturn(okJson(commits(100, 0)).withHeader("Link", link(2))));
        stubFor(get(urlPathEqualTo(PATH)).withQueryParam("page", equalTo("2"))
                .willReturn(okJson(commits(100, 100)).withHeader("Link", link(3))));
        stubFor(get(urlPathEqualTo(PATH)).withQueryParam("page", equalTo("3"))
                .willReturn(okJson(commits(30, 200))));

        CommitsPage page = proxy.getCommits(request(null, null));

        assertEquals(230, page.getCommits().size());
        assertFalse(page.isTruncated());
        verify(3, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    public void stopsAtLimitAndMarksTruncated() {
        System.setProperty(GitHubProxy.MAX_COMMITS_PROPERTY, "100");
        stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(commits(100, 0)).withHeader("Link", link(2))));

        CommitsPage page = proxy.getCommits(request(null, null));

        assertEquals(100, page.getCommits().size());
        assertTrue(page.isTruncated());
        verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    public void emptyRepositoryReturnsNoCommits() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(409)));

        assertTrue(proxy.getCommits(request(null, null)).getCommits().isEmpty());
    }

    @Test
    public void errorsAreClassified() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403).withHeader("X-RateLimit-Remaining", "0")));
        assertEquals(GitHubApiException.RATE_LIMITED, error().getErrorCode());

        wireMockRule.resetAll();
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));
        assertEquals(GitHubApiException.NOT_FOUND, error().getErrorCode());

        wireMockRule.resetAll();
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(401)));
        assertEquals(GitHubApiException.UNAUTHORIZED, error().getErrorCode());
    }

    @Test
    public void successIsCachedAndErrorIsNot() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));
        error();

        wireMockRule.resetAll();
        stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(commits(1, 0))));

        proxy.getCommits(request(null, null));
        proxy.getCommits(request(null, null));

        verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    private GitHubApiException error() {
        try {
            proxy.getCommits(request(null, null));
            fail("expected GitHubApiException");
            return null;
        } catch (GitHubApiException e) {
            return e;
        }
    }
}
