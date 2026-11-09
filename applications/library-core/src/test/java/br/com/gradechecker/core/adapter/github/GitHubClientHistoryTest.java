package br.com.gradechecker.core.adapter.github;

import br.com.gradechecker.core.attribution.AttributedHistory;
import br.com.gradechecker.core.attribution.AttributionSource;
import br.com.gradechecker.core.attribution.ContributionAnalyzer;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.history.ContributionsReport;
import br.com.gradechecker.core.history.ContributorStats;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubClientHistoryTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    private GitHubClient client;
    private final ContributionAnalyzer analyzer = new ContributionAnalyzer(GradingConfig.defaults(), ZoneId.of("America/Sao_Paulo"));

    private final HistoryRequest request = new HistoryRequest("octocat", "hello-world", "2026-01-01T00:00:00Z", "2026-06-30T00:00:00Z", "token");

    @Before
    public void setUp() {
        client = new GitHubClient("http://localhost:" + wireMockRule.port(), "2022-11-28", new MemoryCache());
        System.clearProperty(GitHubClient.MAX_COMMITS_PROPERTY);
    }

    @After
    public void tearDown() {
        System.clearProperty(GitHubClient.MAX_COMMITS_PROPERTY);
    }

    // ---------- helpers ----------

    /** The report of the dashboard: the history read by the client, aggregated by the domain. */
    private ContributionsReport report(HistoryRequest request) {
        return report(request, Map.of());
    }

    private ContributionsReport report(HistoryRequest request, Map<String, String> aliases) {
        return analyzer.report(client.getHistory(request), aliases);
    }

    private AttributedHistory attribute(HistoryRequest request, Map<String, String> aliases) {
        return analyzer.attribute(client.getHistory(request), aliases);
    }

    private static String commit(String login, String name, int additions, int deletions, int parents) {
        String user = login == null ? "null" : "{\"login\":\"" + login + "\"}";
        String authorName = name == null ? "null" : "\"" + name + "\"";
        return "{\"additions\":" + additions + ",\"deletions\":" + deletions
                + ",\"parents\":{\"totalCount\":" + parents + "}"
                + ",\"author\":{\"name\":" + authorName + ",\"user\":" + user + "}}";
    }

    private static String commit(String login, String name, String email, int additions, int deletions) {
        String user = login == null ? "null" : "{\"login\":\"" + login + "\"}";
        String authorName = name == null ? "null" : "\"" + name + "\"";
        String authorEmail = email == null ? "null" : "\"" + email + "\"";
        return "{\"additions\":" + additions + ",\"deletions\":" + deletions
                + ",\"parents\":{\"totalCount\":1}"
                + ",\"author\":{\"name\":" + authorName + ",\"email\":" + authorEmail + ",\"user\":" + user + "}}";
    }

    private static String dated(String login, String name, String email, int additions, int deletions, String date, int files) {
        String user = login == null ? "null" : "{\"login\":\"" + login + "\"}";
        return "{\"committedDate\":\"" + date + "\",\"additions\":" + additions + ",\"deletions\":" + deletions
                + ",\"changedFilesIfAvailable\":" + files + ",\"parents\":{\"totalCount\":1}"
                + ",\"author\":{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"user\":" + user + "}}";
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
            report(request);
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

        report(request);

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
    public void allPeriodOmitsSinceAndQueryAcceptsOptionalDates() {
        stubGraphQl(page(false, null));

        report(new HistoryRequest("octocat", "hello-world", null, "2026-11-05T15:01:00Z", "token"));

        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("$since: GitTimestamp,")))
                .withRequestBody(matchingJsonPath("$.variables.until", equalTo("2026-11-05T15:01:00Z")))
                .withRequestBody(notMatching(".*\"since\"\\s*:.*")));
    }

    @Test
    public void aggregatesPerAuthorIgnoringMergesWithPercentageAndOrdering() {
        stubGraphQl(page(false, null,
                commit("alice", "Alice", 10, 2, 1),
                commit("bob", "Bob", 2, 0, 1),
                commit("alice", "Alice", 5, 1, 1),
                commit("alice", "Alice", 500, 500, 2)));   // merge: ignored

        ContributionsReport report = report(request);

        assertEquals(3, report.getTotalCommits());
        assertFalse(report.isTruncated());
        assertEquals("2026-01-01T00:00:00Z", report.getSince());

        List<ContributorStats> stats = report.getStats();
        assertEquals(2, stats.size());

        ContributorStats alice = stats.get(0);
        assertEquals("alice", alice.getAuthor());
        assertEquals(2, alice.getCommits());
        assertEquals(15, alice.getTotalAdditions());
        assertEquals(3, alice.getTotalDeletions());
        assertEquals(90.0, alice.getPercentage(), 0.001);

        ContributorStats bob = stats.get(1);
        assertEquals("bob", bob.getAuthor());
        assertEquals(1, bob.getCommits());
        assertEquals(10.0, bob.getPercentage(), 0.001);
    }

    @Test
    public void fallsBackToGitNameThenUnknown() {
        stubGraphQl(page(false, null,
                commit(null, "Maria Silva", 3, 1, 1),
                commit(null, null, 1, 0, 1)));

        List<ContributorStats> stats = report(request).getStats();

        assertEquals("Maria Silva", stats.get(0).getAuthor());
        assertEquals("unknown", stats.get(1).getAuthor());
    }

    // ---------- author identities (openspec change identidades-autores) ----------

    private static ContributorStats row(List<ContributorStats> stats, String author) {
        return stats.stream().filter(r -> r.getAuthor().equals(author)).findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + author + " in " + stats.stream().map(ContributorStats::getAuthor).collect(java.util.stream.Collectors.toList())));
    }

    @Test
    public void queryRequestsAuthorEmail() {
        stubGraphQl(page(false, null));
        report(request);
        verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(matchingJsonPath("$.query", containing("author { name email user { login } }"))));
    }

    @Test
    public void unlinkedCommitWithEmailOfLinkedCommitIsMergedEvenFromALaterPage() {
        stubFor(post(urlEqualTo("/graphql")).atPriority(5)
                .willReturn(okJson(page(true, "C1", commit(null, "JOAO VITOR", "Joao@Escola.edu", 8, 0)))));
        stubFor(post(urlEqualTo("/graphql")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.variables.cursor", equalTo("C1")))
                .willReturn(okJson(page(false, null, commit("joaolima16", "joao", "joao@escola.edu", 10, 2)))));

        List<ContributorStats> stats = report(request).getStats();

        assertEquals(1, stats.size());
        ContributorStats joao = row(stats, "joaolima16");
        assertEquals(2, joao.getCommits());
        assertEquals(18, joao.getTotalAdditions());
        assertTrue(joao.isLinked());
        assertEquals(List.of("JOAO VITOR <Joao@Escola.edu>"), joao.getGitIdentities()); // merged commit is auditable
    }

    @Test
    public void unlinkedCommitWhoseGitNameIsExactlyALoginIsMerged() {
        // group 6 (cannamed): e-mail without "@", git name equal to the login
        stubGraphQl(page(false, null,
                commit("thiagofp77", "thiagofp77", "thi@gmail.com", 1000, 22),
                commit(null, "thiagofp77", "thiiago.fp7gmail.com", 500, 58),
                commit(null, " ThiagoFP77 ", "other@escola.edu", 1, 0)));   // case/spaces ignored, valid but unlinked e-mail

        List<ContributorStats> stats = report(request).getStats();

        assertEquals(1, stats.size());
        ContributorStats thiago = stats.get(0);
        assertEquals("thiagofp77", thiago.getAuthor());
        assertEquals(3, thiago.getCommits());
        assertEquals(1581, thiago.getTotalAdditions() + thiago.getTotalDeletions());
        assertTrue(thiago.isLinked());
        assertEquals(List.of("thiagofp77 <thiiago.fp7gmail.com>", "ThiagoFP77 <other@escola.edu>"), thiago.getGitIdentities());
    }

    @Test
    public void linkedAuthorWithoutUnlinkedCommitsHasNoIdentities() {
        stubGraphQl(page(false, null, commit("alice", "Alice", "alice@x.com", 1, 0)));

        assertTrue(report(request).getStats().get(0).getGitIdentities().isEmpty());
    }

    @Test
    public void ambiguousEmailIsNotMerged() {
        stubGraphQl(page(false, null,
                commit("alice", "Alice", "lab@escola.edu", 5, 0),
                commit("bob", "Bob", "lab@escola.edu", 5, 0),
                commit(null, "Someone", "lab@escola.edu", 3, 0)));

        List<ContributorStats> stats = report(request).getStats();

        assertEquals(1, row(stats, "alice").getCommits());
        assertEquals(1, row(stats, "bob").getCommits());
        ContributorStats orphan = row(stats, "Someone");
        assertFalse(orphan.isLinked());
        assertEquals(List.of("Someone <lab@escola.edu>"), orphan.getGitIdentities());
    }

    @Test
    public void unlinkedCommitsAreGroupedByEmailNotByName() {
        stubGraphQl(page(false, null,
                commit(null, "joao", "joao@escola.edu", 4, 0),
                commit(null, "JOAO VITOR", "JOAO@escola.edu", 6, 1)));

        List<ContributorStats> stats = report(request).getStats();

        assertEquals(1, stats.size());
        ContributorStats joao = stats.get(0);
        assertEquals("joao", joao.getAuthor()); // first non-empty name
        assertEquals(2, joao.getCommits());
        assertFalse(joao.isLinked());
        assertEquals(List.of("joao <joao@escola.edu>", "JOAO VITOR <JOAO@escola.edu>"), joao.getGitIdentities());
    }

    @Test
    public void invalidEmailFallsBackToNameAndSimilarNamesAreNotMerged() {
        stubGraphQl(page(false, null,
                commit("joaolima16", "joao", "joao@gmail.com", 10, 0),
                commit(null, "joaolima", "=", 2, 0),
                commit(null, "joaolima", "=", 1, 0),
                commit(null, "=", "=", 1, 0)));

        List<ContributorStats> stats = report(request).getStats();

        assertEquals(1, row(stats, "joaolima16").getCommits());
        ContributorStats byName = row(stats, "joaolima");
        assertEquals(2, byName.getCommits());
        assertFalse(byName.isLinked());
        assertEquals(List.of("joaolima <=>"), byName.getGitIdentities());
        assertFalse(row(stats, "=").isLinked());
    }

    // ---------- grading indicators (openspec change nota-por-marco) ----------

    @Test
    public void queryRequestsDateAndChangedFiles() {
        stubGraphQl(page(false, null));
        report(request);
        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("committedDate")))
                .withRequestBody(matchingJsonPath("$.query", containing("changedFilesIfAvailable"))));
    }

    @Test
    public void gradingIndicatorsPerAuthor() {
        stubGraphQl(page(false, null,
                dated("matheus", "Matheus", "m@x.com", 1000, 53, "2026-10-26T15:00:00Z", 8),    // capped at 400
                dated("matheus", "Matheus", "m@x.com", 300, 298, "2026-10-29T15:00:00Z", 44),   // reorganization: 59.8
                dated("matheus", "Matheus", "m@x.com", 10, 2, "2026-10-29T18:00:00Z", 1),       // not relevant (12 lines)
                dated("ana", "Ana", "a@x.com", 50, 0, "2026-10-21T02:30:00Z", 2),               // 20/10 23:30 in Sao Paulo
                dated("ana", "Ana", "a@x.com", 30, 0, "2026-10-21T13:00:00Z", 2),               // 21/10 10:00 in Sao Paulo
                dated(null, "ana", "invalid-email", 25, 0, "2026-10-22T13:00:00Z", 1)));        // rule 2b: counted for ana

        List<ContributorStats> stats = report(request).getStats();

        ContributorStats matheus = row(stats, "matheus");
        assertEquals(400 + 59.8 + 12, matheus.getEffectiveLines(), 1e-9);
        assertEquals(2, matheus.getActiveDays());
        assertEquals(2, matheus.getRelevantCommits());

        ContributorStats ana = row(stats, "ana");
        assertEquals(105, ana.getEffectiveLines(), 1e-9);
        assertEquals("2 days in Sao Paulo (1 in UTC) + 1 from the unlinked commit", 3, ana.getActiveDays());
        assertEquals(3, ana.getRelevantCommits());
    }

    @Test
    public void zeroLinesGivesZeroPercentage() {
        stubGraphQl(page(false, null, commit("alice", "Alice", 0, 0, 1)));

        ContributorStats alice = report(request).getStats().get(0);

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

        ContributionsReport report = report(request);

        assertEquals(3, report.getTotalCommits());
        assertEquals(3, report.getStats().size());
        assertFalse(report.isTruncated());
        verify(3, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    public void stopsAtMaxCommitsAndMarksTruncated() {
        System.setProperty(GitHubClient.MAX_COMMITS_PROPERTY, "100");
        stubGraphQl(page(true, "C1", commit("alice", "Alice", 1, 0, 1)));

        ContributionsReport report = report(request);

        assertTrue(report.isTruncated());
        verify(1, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    public void invalidMaxCommitsFallsBackToDefault() {
        System.setProperty(GitHubClient.MAX_COMMITS_PROPERTY, "abc");
        assertEquals(GitHubClient.DEFAULT_MAX_COMMITS, GitHubClient.maxCommits());
        System.setProperty(GitHubClient.MAX_COMMITS_PROPERTY, "0");
        assertEquals(GitHubClient.DEFAULT_MAX_COMMITS, GitHubClient.maxCommits());
    }

    @Test
    public void emptyRepositoryReturnsEmptyReport() {
        stubGraphQl("{\"data\":{\"repository\":{\"defaultBranchRef\":null}}}");

        ContributionsReport report = report(request);

        assertEquals(0, report.getTotalCommits());
        assertTrue(report.getStats().isEmpty());
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

        report(request);
        ContributionsReport second = report(request);

        assertEquals(1, second.getTotalCommits());
        verify(1, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    public void errorIsNotCached() {
        stubFor(post(urlEqualTo("/graphql")).willReturn(aResponse().withStatus(500)));
        expectError();

        wireMockRule.resetAll();
        stubGraphQl(page(false, null, commit("alice", "Alice", 1, 0, 1)));

        assertEquals(1, report(request).getTotalCommits());
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

    // ---------- manual associations (openspec capability identity-aliases) ----------

    @Test
    public void aliasesAreAppliedWithoutReadingTheHistoryAgain() {
        stubGraphQl(page(false, null,
                commit("joaolima16", "Joao", "j@x.com", 10, 0),
                commit(null, "joaolima", "=", 5, 0)));

        ContributionsReport without = report(request, Map.of());
        ContributionsReport linked = report(request, Map.of("name:joaolima", "joaolima16"));
        ContributionsReport again = report(request, Map.of("name:joaolima", "joaolima16"));

        assertEquals(2, without.getStats().size());
        assertEquals(1, linked.getStats().size());
        assertEquals(15, linked.getStats().get(0).getTotalAdditions());
        assertEquals("same aliases, same report", 1, again.getStats().size());
        verify(1, postRequestedFor(urlEqualTo("/graphql"))); // new aliases reuse the cached history
    }

    // ---------- shared history and per-commit attribution (openspec capability commit-history-page) ----------

    private static String full(String oid, String login, String name, String email, int additions, int parents, String date) {
        String user = login == null ? "null" : "{\"login\":\"" + login + "\"}";
        return "{\"oid\":\"" + oid + "\",\"message\":\"msg " + oid + "\",\"url\":\"https://github.com/octocat/hello-world/commit/" + oid + "\""
                + ",\"committedDate\":\"" + date + "\",\"additions\":" + additions + ",\"deletions\":0"
                + ",\"changedFilesIfAvailable\":1,\"parents\":{\"totalCount\":" + parents + "}"
                + ",\"author\":{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"user\":" + user + "}}";
    }

    @Test
    public void queryAsksForOidMessageAndUrl() {
        stubGraphQl(page(false, null));

        client.getHistory(request);

        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("nodes { oid message url committedDate"))));
    }

    @Test
    public void historyKeepsMergesAndTheCommitFields() {
        stubGraphQl(page(false, null,
                full("a1", "alice", "Alice", "a@x.com", 10, 1, "2026-03-01T14:05:00Z"),
                full("m1", "alice", "Alice", "a@x.com", 0, 2, "2026-03-02T10:00:00Z")));

        RepositoryHistory history = client.getHistory(request);

        assertEquals(2, history.getCommits().size());
        HistoryCommit first = history.getCommits().get(0);
        assertEquals("a1", first.getOid());
        assertEquals("msg a1", first.getMessage());
        assertEquals("https://github.com/octocat/hello-world/commit/a1", first.getUrl());
        assertEquals("2026-03-01T14:05:00Z", first.getCommittedDate());
        assertEquals("alice", first.getAuthorLogin());
        assertTrue(history.getCommits().get(1).isMerge());
    }

    @Test
    public void dashboardAndCommitHistoryShareOneQueryAndAliasesNeedNoNewQuery() {
        stubGraphQl(page(false, null,
                full("a1", "joaolima16", "Joao", "j@x.com", 10, 1, "2026-03-01T14:05:00Z"),
                full("b2", null, "joaolima", "=", 5, 1, "2026-03-02T14:05:00Z")));

        report(request, Map.of());
        attribute(request, Map.of());
        AttributedHistory linked = attribute(request, Map.of("name:joaolima", "joaolima16"));

        verify(1, postRequestedFor(urlEqualTo("/graphql")));
        assertEquals("joaolima16", linked.getCommits().get(1).getAuthor());
        assertEquals(AttributionSource.MANUAL, linked.getCommits().get(1).getSource());
        assertEquals("joaolima <=>", linked.getCommits().get(1).getIdentity());
    }

    @Test
    public void commitHistoryExcludesMergesAndAttributesLikeTheDashboard() {
        stubGraphQl(page(false, null,
                full("a1", "thiagofp77", "Thiago", "t@x.com", 10, 1, "2026-03-01T14:05:00Z"),
                full("b2", null, "thiagofp77", "thiiago.fp7gmail.com", 558, 1, "2026-03-02T14:05:00Z"),
                full("c3", null, "Thiago", "t@x.com", 3, 1, "2026-03-03T14:05:00Z"),
                full("d4", null, "Someone", "s@x.com", 7, 1, "2026-03-04T14:05:00Z"),
                full("m5", "thiagofp77", "Thiago", "t@x.com", 0, 2, "2026-03-05T14:05:00Z")));

        AttributedHistory history = attribute(request, Map.of());
        ContributionsReport report = report(request, Map.of());

        assertEquals("merge excluded", 4, history.getCommits().size());
        assertEquals(AttributionSource.ACCOUNT, history.getCommits().get(0).getSource());
        assertNull(history.getCommits().get(0).getIdentity());
        assertEquals(AttributionSource.SAME_NAME, history.getCommits().get(1).getSource());
        assertEquals(AttributionSource.SAME_EMAIL, history.getCommits().get(2).getSource());
        assertEquals(AttributionSource.NO_ACCOUNT, history.getCommits().get(3).getSource());
        assertFalse(history.getCommits().get(3).isLinked());

        // the same totals as the dashboard, author by author
        java.util.Map<String, Long> perAuthor = new HashMap<>();
        history.getCommits().forEach(c -> perAuthor.merge(c.getAuthor(), 1L, Long::sum));
        for (ContributorStats row : report.getStats()) {
            assertEquals(row.getAuthor(), Long.valueOf(row.getCommits()), perAuthor.get(row.getAuthor()));
        }
    }

    @Test
    public void historyErrorIsNotCached() {
        stubFor(post(urlEqualTo("/graphql")).inScenario("h").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(502)).willSetStateTo("ok"));
        stubFor(post(urlEqualTo("/graphql")).inScenario("h").whenScenarioStateIs("ok")
                .willReturn(okJson(page(false, null, full("a1", "alice", "Alice", "a@x.com", 1, 1, "2026-03-01T14:05:00Z")))));

        try {
            attribute(request, Map.of());
            fail("expected GitHubApiException");
        } catch (GitHubApiException expected) {
            // not cached
        }
        assertEquals(1, attribute(request, Map.of()).getCommits().size());
        verify(2, postRequestedFor(urlEqualTo("/graphql")));
    }
}
