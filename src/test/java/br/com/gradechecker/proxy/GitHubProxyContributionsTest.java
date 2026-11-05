package br.com.gradechecker.proxy;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubProxyContributionsTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubProxy proxy;

    private final CommitRequestBody request = new CommitRequestBody(
            "hello-world", "octocat", null, "2026-01-01T00:00:00Z", "2026-06-30T00:00:00Z", "token", "1");

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

    // ---------- helpers ----------

    private static String commit(String login, String name, int additions, int deletions, int parents) {
        String user = login == null ? "null" : "{\"login\":\"" + login + "\"}";
        String authorName = name == null ? "null" : "\"" + name + "\"";
        return "{\"additions\":" + additions + ",\"deletions\":" + deletions
                + ",\"parents\":{\"totalCount\":" + parents + "}"
                + ",\"author\":{\"name\":" + authorName + ",\"user\":" + user + "}}";
    }

    private static String page(boolean hasNextPage, String endCursor, String... commits) {
        return "{\"data\":{\"repository\":{\"defaultBranchRef\":{\"target\":{\"history\":{"
                + "\"pageInfo\":{\"hasNextPage\":" + hasNextPage + ",\"endCursor\":" + (endCursor == null ? "null" : "\"" + endCursor + "\"") + "},"
                + "\"nodes\":[" + String.join(",", commits) + "]}}}}}}";
    }

    private void stubGraphQl(String body) {
        stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private GitHubApiException expectError() {
        try {
            proxy.getContributionsReport(request);
            fail("expected GitHubApiException");
            return null;
        } catch (GitHubApiException e) {
            return e;
        }
    }

    // ---------- tests ----------

    @Test
    public void sendsGraphQlQueryWithTokenAndPeriodAndNoAuthorFilter() {
        stubGraphQl(page(false, null));

        proxy.getContributionsReport(request);

        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withHeader("Authorization", equalTo("Bearer token"))
                .withRequestBody(matchingJsonPath("$.query", containing("history(first: 100, since: $since, until: $until, after: $cursor)")))
                .withRequestBody(matchingJsonPath("$.variables.owner", equalTo("octocat")))
                .withRequestBody(matchingJsonPath("$.variables.repo", equalTo("hello-world")))
                .withRequestBody(matchingJsonPath("$.variables.since", equalTo("2026-01-01T00:00:00Z")))
                .withRequestBody(matchingJsonPath("$.variables.until", equalTo("2026-06-30T00:00:00Z")))
                .withRequestBody(notMatching(".*\"author\".*:.*\"variables\".*"))
                .withRequestBody(notMatching(".*\"cursor\".*")));
    }

    @Test
    public void aggregatesPerAuthorIgnoringMergesWithPercentageAndOrdering() {
        stubGraphQl(page(false, null,
                commit("alice", "Alice", 10, 2, 1),
                commit("bob", "Bob", 2, 0, 1),
                commit("alice", "Alice", 5, 1, 1),
                commit("alice", "Alice", 500, 500, 2)));   // merge: ignored

        ContributionsReport report = proxy.getContributionsReport(request);

        assertEquals(3, report.getTotalCommits());
        assertFalse(report.isTruncated());
        assertEquals("2026-01-01T00:00:00Z", report.getSince());

        List<ContributorStatsResponseBody> stats = report.getConsolidatedStats();
        assertEquals(2, stats.size());

        ContributorStatsResponseBody alice = stats.get(0);
        assertEquals("alice", alice.getAuthor());
        assertEquals(2, alice.getCommits());
        assertEquals(15, alice.getTotalAdditions());
        assertEquals(3, alice.getTotalDeletions());
        assertEquals(90.0, alice.getPercentage(), 0.001);

        ContributorStatsResponseBody bob = stats.get(1);
        assertEquals("bob", bob.getAuthor());
        assertEquals(1, bob.getCommits());
        assertEquals(10.0, bob.getPercentage(), 0.001);
    }

    @Test
    public void fallsBackToGitNameThenUnknown() {
        stubGraphQl(page(false, null,
                commit(null, "Maria Silva", 3, 1, 1),
                commit(null, null, 1, 0, 1)));

        List<ContributorStatsResponseBody> stats = proxy.getContributionsReport(request).getConsolidatedStats();

        assertEquals("Maria Silva", stats.get(0).getAuthor());
        assertEquals("unknown", stats.get(1).getAuthor());
    }

    @Test
    public void zeroLinesGivesZeroPercentage() {
        stubGraphQl(page(false, null, commit("alice", "Alice", 0, 0, 1)));

        ContributorStatsResponseBody alice = proxy.getContributionsReport(request).getConsolidatedStats().get(0);

        assertEquals(1, alice.getCommits());
        assertEquals(0.0, alice.getPercentage(), 0.0);
    }

    @Test
    public void followsCursorUntilLastPage() {
        stubFor(post(urlEqualTo("/graphql")).atPriority(5)
                .willReturn(okJson(page(true, "C1", commit("alice", "Alice", 1, 0, 1)))));
        stubFor(post(urlEqualTo("/graphql")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.variables.cursor", equalTo("C1")))
                .willReturn(okJson(page(true, "C2", commit("bob", "Bob", 1, 0, 1)))));
        stubFor(post(urlEqualTo("/graphql")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.variables.cursor", equalTo("C2")))
                .willReturn(okJson(page(false, null, commit("carol", "Carol", 1, 0, 1)))));

        ContributionsReport report = proxy.getContributionsReport(request);

        assertEquals(3, report.getTotalCommits());
        assertEquals(3, report.getConsolidatedStats().size());
        assertFalse(report.isTruncated());
        verify(3, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    public void stopsAtMaxCommitsAndMarksTruncated() {
        System.setProperty(GitHubProxy.MAX_COMMITS_PROPERTY, "100");
        stubGraphQl(page(true, "C1", commit("alice", "Alice", 1, 0, 1)));

        ContributionsReport report = proxy.getContributionsReport(request);

        assertTrue(report.isTruncated());
        verify(1, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    public void invalidMaxCommitsFallsBackToDefault() {
        System.setProperty(GitHubProxy.MAX_COMMITS_PROPERTY, "abc");
        assertEquals(GitHubProxy.DEFAULT_MAX_COMMITS, GitHubProxy.maxCommits());
        System.setProperty(GitHubProxy.MAX_COMMITS_PROPERTY, "0");
        assertEquals(GitHubProxy.DEFAULT_MAX_COMMITS, GitHubProxy.maxCommits());
    }

    @Test
    public void emptyRepositoryReturnsEmptyReport() {
        stubGraphQl("{\"data\":{\"repository\":{\"defaultBranchRef\":null}}}");

        ContributionsReport report = proxy.getContributionsReport(request);

        assertEquals(0, report.getTotalCommits());
        assertTrue(report.getConsolidatedStats().isEmpty());
    }

    @Test
    public void unauthorized() {
        stubFor(post(urlEqualTo("/graphql")).willReturn(aResponse().withStatus(401)));

        GitHubApiException e = expectError();

        assertEquals(401, e.getHttpStatus());
        assertEquals(GitHubApiException.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    public void rateLimitedByHeaders() {
        stubFor(post(urlEqualTo("/graphql")).willReturn(aResponse().withStatus(403)
                .withHeader("X-RateLimit-Remaining", "0")
                .withHeader("X-RateLimit-Reset", "1793980800")));

        GitHubApiException e = expectError();

        assertEquals(429, e.getHttpStatus());
        assertEquals(GitHubApiException.RATE_LIMITED, e.getErrorCode());
        assertTrue(e.getMessage(), e.getMessage().contains("2026-11-06T"));
    }

    @Test
    public void rateLimitedByGraphQlError() {
        stubGraphQl("{\"data\":null,\"errors\":[{\"type\":\"RATE_LIMITED\",\"message\":\"API rate limit exceeded\"}]}");

        assertEquals(GitHubApiException.RATE_LIMITED, expectError().getErrorCode());
    }

    @Test
    public void forbiddenWithoutRateLimit() {
        stubFor(post(urlEqualTo("/graphql")).willReturn(aResponse().withStatus(403)));

        GitHubApiException e = expectError();

        assertEquals(403, e.getHttpStatus());
        assertEquals(GitHubApiException.FORBIDDEN, e.getErrorCode());
    }

    @Test
    public void notFoundComesAsGraphQlErrorWithHttp200() {
        stubGraphQl("{\"data\":{\"repository\":null},\"errors\":[{\"type\":\"NOT_FOUND\",\"message\":\"Could not resolve to a Repository\"}]}");

        GitHubApiException e = expectError();

        assertEquals(404, e.getHttpStatus());
        assertEquals(GitHubApiException.NOT_FOUND, e.getErrorCode());
    }

    @Test
    public void nullRepositoryWithoutErrorsIsNotFound() {
        stubGraphQl("{\"data\":{\"repository\":null}}");

        assertEquals(GitHubApiException.NOT_FOUND, expectError().getErrorCode());
    }

    @Test
    public void serverErrorIsBadGateway() {
        stubFor(post(urlEqualTo("/graphql")).willReturn(aResponse().withStatus(500)));

        GitHubApiException e = expectError();

        assertEquals(502, e.getHttpStatus());
        assertEquals(GitHubApiException.ERROR, e.getErrorCode());
    }

    @Test
    public void successIsCached() {
        stubGraphQl(page(false, null, commit("alice", "Alice", 1, 0, 1)));

        proxy.getContributionsReport(request);
        ContributionsReport second = proxy.getContributionsReport(request);

        assertEquals(1, second.getTotalCommits());
        verify(1, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    public void errorIsNotCached() {
        stubFor(post(urlEqualTo("/graphql")).willReturn(aResponse().withStatus(500)));
        expectError();

        wireMockRule.resetAll();
        stubGraphQl(page(false, null, commit("alice", "Alice", 1, 0, 1)));

        assertEquals(1, proxy.getContributionsReport(request).getTotalCommits());
        verify(1, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    public void errorOnSecondPageIsNotCachedAsPartialResult() {
        stubFor(post(urlEqualTo("/graphql")).atPriority(5)
                .willReturn(okJson(page(true, "C1", commit("alice", "Alice", 1, 0, 1)))));
        stubFor(post(urlEqualTo("/graphql")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.variables.cursor", equalTo("C1")))
                .willReturn(aResponse().withStatus(500)));

        expectError();
        expectError(); // nothing cached: GitHub is called again

        verify(4, postRequestedFor(urlEqualTo("/graphql")));
    }
}
