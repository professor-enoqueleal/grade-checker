package br.com.gradechecker.proxy;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.model.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.*;

public class GitHubProxy {

    private static final Gson GSON = new Gson();

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final String githubApiVersion;
    private final String baseUrl;
    private final br.com.gradechecker.config.GradingConfig grading;
    private final java.time.ZoneId zone;

    public GitHubProxy() {
        this("https://api.github.com", "2022-11-28");
    }

    public GitHubProxy(String baseUrl, String apiVersion) {
        this(baseUrl, apiVersion, br.com.gradechecker.config.GradingConfig.fromClasspath(),
                br.com.gradechecker.config.Milestones.fromClasspath().getZone());
    }

    public GitHubProxy(String baseUrl, String apiVersion, br.com.gradechecker.config.GradingConfig grading, java.time.ZoneId zone) {
        this.baseUrl = baseUrl;
        this.githubApiVersion = apiVersion;
        this.grading = grading;
        this.zone = zone;
    }

    private String tokenHash(String token) {
        if (token == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            return Integer.toString(token.hashCode());
        }
    }

    /**
     * Contributors of the repository (accounts with commits on the default branch), up to 100
     * (openspec capability group-members). Never stored in the database; cached only on success.
     *
     * @throws GitHubApiException when GitHub cannot be read (401, 403, 404, 429, 502)
     */
    public List<Contributor> getContributors(String accessKey, String owner, String repo) {

        final String cacheKey = String.format("github.contributors::%s:%s:%s", owner, repo, tokenHash(accessKey));

        @SuppressWarnings("unchecked")
        List<Contributor> cached = CacheManager.get(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        HttpResponse<String> response;
        try {
            response = Unirest.get(baseUrl + "/repos/{owner}/{repo}/contributors")
                    .header("Accept", "application/json")
                    .header("Authorization", String.format("Bearer %s", accessKey))
                    .header("X-GitHub-Api-Version", githubApiVersion)
                    .routeParam("owner", owner)
                    .routeParam("repo", repo)
                    .queryString("per_page", 100)
                    .asString();
        } catch (Exception e) {
            logger.error("Error on retrieve contributors of {}/{}: {}", owner, repo, e.getMessage());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Could not connect to GitHub. Please try again.", e);
        }

        if (response.getStatus() == 204) {          // repository without commits
            List<Contributor> empty = java.util.Collections.emptyList();
            CacheManager.put(cacheKey, empty);
            return empty;
        }
        if (response.getStatus() != 200) {
            throw httpError(response);
        }

        List<Contributor> body;
        try {
            Contributor[] parsed = GSON.fromJson(response.getBody(), Contributor[].class);
            body = parsed == null ? java.util.Collections.emptyList() : java.util.Collections.unmodifiableList(java.util.Arrays.asList(parsed));
        } catch (RuntimeException e) {
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Invalid response from GitHub.", e);
        }

        logger.info("success on get contributors information on github");
        CacheManager.put(cacheKey, body);
        return body;

    }


    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String orDash(String value) {
        return isPresent(value) ? value : "-";
    }

     public Commit getCommitDetail(CommitRequestBody commitRequestBody, String sha) {

        final String cacheKey = String.format("github.commit::%s:%s:%s:%s",
                commitRequestBody.getOwner(),
                commitRequestBody.getRepository(),
                sha,
                tokenHash(commitRequestBody.getAccessToken())
        );

         Commit cached = CacheManager.get(cacheKey, Commit.class);

         if (cached != null) {
             return cached;
         }

         String url = baseUrl + "/repos/{owner}/{repo}/commits/{sha}";

         try {

             String bearerToken = String.format("Bearer %s", commitRequestBody.getAccessToken());

             HttpResponse<Commit> httpResponse = Unirest.get(url)
                     .header("Accept", "application/json")
                     .header("Authorization", bearerToken)
                     .header("X-GitHub-Api-Version", githubApiVersion)
                     .routeParam("owner", commitRequestBody.getOwner())
                     .routeParam("repo", commitRequestBody.getRepository())
                     .routeParam("sha", sha)
                     .asObject(Commit.class);

             logger.info("success on get commits detail on github");

             Commit body = httpResponse.getBody();
             CacheManager.put(cacheKey, body);

             return body;

         } catch (Exception e) {

             logger.error("Error on retrieve commits detail on github. Cause: {}", e.getMessage());

             throw new RuntimeException("Error on retrieve commits detail on github");

         }

     }

    static final String MAX_COMMITS_PROPERTY = "GH_DASHBOARD_MAX_COMMITS";
    static final int DEFAULT_MAX_COMMITS = 1000;
    private static final int PAGE_SIZE = 100;

    static final String CONTRIBUTIONS_QUERY =
            "query($owner: String!, $repo: String!, $since: GitTimestamp, $until: GitTimestamp, $cursor: String) {"
            + " repository(owner: $owner, name: $repo) {"
            + "  defaultBranchRef { target { ... on Commit {"
            + "   history(first: " + PAGE_SIZE + ", since: $since, until: $until, after: $cursor) {"
            + "    pageInfo { hasNextPage endCursor }"
            + "    nodes { oid message url committedDate additions deletions changedFilesIfAvailable parents { totalCount } author { name email user { login } } }"
            + "   } } } }"
            + " }"
            + "}";

    /**
     * Contributions (commits, additions, deletions, percentage) per author of the repository
     * default branch within [since, until], using the GitHub GraphQL API.
     * Merge commits are ignored. Only complete results are cached.
     *
     * @throws GitHubApiException when GitHub answers with an error (never returns an empty list on error)
     */
    public ContributionsReport getContributionsReport(CommitRequestBody request) {
        return getContributionsReport(request, Collections.emptyMap());
    }

    /**
     * @param aliases manual associations of the group (identity key -> login, openspec capability identity-aliases);
     *                part of the cache key, so linking or unlinking an identity never serves a stale report
     */
    public ContributionsReport getContributionsReport(CommitRequestBody request, Map<String, String> aliases) {

        final Map<String, String> groupAliases = aliases == null ? Collections.emptyMap() : aliases;

        final String cacheKey = String.format("github.contributions::%s:%s:%s:%s:%d:%s:%s",
                request.getOwner(),
                request.getRepository(),
                orDash(request.getSince()),
                orDash(request.getUntil()),
                maxCommits(),
                tokenHash(request.getAccessToken()),
                aliasesFingerprint(groupAliases)
        );

        ContributionsReport cached = CacheManager.get(cacheKey, ContributionsReport.class);

        if (cached != null) {
            return cached;
        }

        RepositoryHistory history = getHistory(request);
        AuthorAggregator aggregator = aggregate(history, groupAliases);
        int totalCommits = 0;
        for (HistoryCommit commit : history.getCommits()) if (!commit.isMerge()) totalCommits++;

        ContributionsReport report = new ContributionsReport(
                request.getSince(), request.getUntil(), totalCommits, history.isTruncated(), aggregator.result());

        CacheManager.put(cacheKey, report);

        return report;

    }

    /**
     * Non-merge commits of the period, each with the author it is counted for (same rules as the dashboard,
     * including the manual associations of the group) - openspec capability commit-history-page.
     *
     * @throws GitHubApiException when GitHub answers with an error
     */
    public AttributedHistory getCommitHistory(CommitRequestBody request, Map<String, String> aliases) {

        RepositoryHistory history = getHistory(request);
        AuthorAggregator aggregator = aggregate(history, aliases == null ? Collections.emptyMap() : aliases);

        List<AttributedCommit> commits = new ArrayList<>();
        for (HistoryCommit commit : history.getCommits()) {
            if (!commit.isMerge()) commits.add(aggregator.attributionOf(commit));
        }

        return new AttributedHistory(history.isTruncated(), commits);
    }

    /** Phase 1 of the aggregation with every non-merge commit of the history. */
    private AuthorAggregator aggregate(RepositoryHistory history, Map<String, String> aliases) {
        AuthorAggregator aggregator = new AuthorAggregator(grading, zone, aliases);
        for (HistoryCommit c : history.getCommits()) {
            if (c.isMerge()) continue;
            aggregator.add(c.getAuthorLogin(), c.getAuthorName(), c.getAuthorEmail(), c.getAdditions(), c.getDeletions(),
                    c.getCommittedDate(), c.getChangedFiles());
        }
        return aggregator;
    }

    /**
     * Commits of the default branch within [since, until] (merges included), read once with GraphQL and shared by
     * the dashboard and the commit history page. Cached without the aliases; only complete results are cached.
     *
     * @throws GitHubApiException when GitHub answers with an error
     */
    public RepositoryHistory getHistory(CommitRequestBody request) {

        final int maxCommits = maxCommits();

        final String cacheKey = String.format("github.history::%s:%s:%s:%s:%d:%s",
                request.getOwner(),
                request.getRepository(),
                orDash(request.getSince()),
                orDash(request.getUntil()),
                maxCommits,
                tokenHash(request.getAccessToken())
        );

        RepositoryHistory cached = CacheManager.get(cacheKey, RepositoryHistory.class);

        if (cached != null) {
            return cached;
        }

        List<HistoryCommit> commits = new ArrayList<>();
        int maxPages = (maxCommits + PAGE_SIZE - 1) / PAGE_SIZE;
        int pages = 0;
        String cursor = null;
        boolean hasNextPage;

        do {

            JsonObject repository = executeContributionsQuery(request, cursor).getAsJsonObject("repository");

            JsonObject history = child(child(child(repository, "defaultBranchRef"), "target"), "history");

            if (history == null) {
                // empty repository (no default branch) -> no commits
                break;
            }

            for (JsonElement element : history.getAsJsonArray("nodes")) {

                if (element == null || !element.isJsonObject()) continue;

                JsonObject node = element.getAsJsonObject();
                JsonObject author = child(node, "author");

                commits.add(new HistoryCommit(text(node, "oid"), text(node, "message"), text(node, "url"),
                        text(node, "committedDate"), integer(node, "additions"), integer(node, "deletions"),
                        integer(node, "changedFilesIfAvailable"), integer(child(node, "parents"), "totalCount"),
                        text(author, "name"), text(author, "email"), text(child(author, "user"), "login")));

            }

            JsonObject pageInfo = child(history, "pageInfo");
            hasNextPage = pageInfo != null && pageInfo.has("hasNextPage") && pageInfo.get("hasNextPage").getAsBoolean();
            cursor = hasNextPage ? text(pageInfo, "endCursor") : null;
            pages++;

        } while (hasNextPage && cursor != null && pages < maxPages);

        boolean truncated = hasNextPageAfterLimit(pages, maxPages, cursor);

        RepositoryHistory result = new RepositoryHistory(request.getSince(), request.getUntil(), truncated, commits);

        logger.info("success on get history of {}/{} ({} commits, {} pages, truncated={})",
                request.getOwner(), request.getRepository(), commits.size(), pages, truncated);

        CacheManager.put(cacheKey, result);

        return result;

    }

    /** "-" without aliases; otherwise a hash of the sorted entries. */
    private String aliasesFingerprint(Map<String, String> aliases) {
        if (aliases.isEmpty()) return "-";
        StringBuilder entries = new StringBuilder();
        new TreeMap<>(aliases).forEach((key, login) -> entries.append(key).append('=').append(login).append('\n'));
        return tokenHash(entries.toString());
    }

    private static boolean hasNextPageAfterLimit(int pages, int maxPages, String cursor) {
        return pages >= maxPages && cursor != null;
    }

    private JsonObject executeContributionsQuery(CommitRequestBody request, String cursor) {

        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", request.getOwner());
        variables.put("repo", request.getRepository());
        // null values are omitted by Gson: no since = whole history, no cursor = first page
        variables.put("since", isPresent(request.getSince()) ? request.getSince() : null);
        variables.put("until", isPresent(request.getUntil()) ? request.getUntil() : null);
        variables.put("cursor", cursor);

        Map<String, Object> body = new HashMap<>();
        body.put("query", CONTRIBUTIONS_QUERY);
        body.put("variables", variables);

        HttpResponse<String> response;

        try {

            response = Unirest.post(baseUrl + "/graphql")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", String.format("Bearer %s", request.getAccessToken()))
                    .body(GSON.toJson(body))
                    .asString();

        } catch (Exception e) {

            logger.error("Error calling GitHub GraphQL. Cause: {}", e.getMessage());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Could not connect to GitHub. Please try again.", e);

        }

        int status = response.getStatus();

        if (status != 200) {
            throw httpError(response);
        }

        JsonObject json;

        try {
            json = JsonParser.parseString(response.getBody()).getAsJsonObject();
        } catch (Exception e) {
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Invalid response from GitHub.", e);
        }

        if (json.has("errors") && json.get("errors").isJsonArray() && json.getAsJsonArray("errors").size() > 0) {
            throw graphQlError(json.getAsJsonArray("errors").get(0).getAsJsonObject());
        }

        JsonObject data = child(json, "data");

        if (data == null || child(data, "repository") == null) {
            throw new GitHubApiException(404, GitHubApiException.NOT_FOUND, notFoundMessage(request));
        }

        return data;

    }

    private GitHubApiException httpError(HttpResponse<?> response) {

        int status = response.getStatus();
        String remaining = header(response, "X-RateLimit-Remaining");
        String retryAfter = header(response, "Retry-After");

        logger.warn("GitHub answered HTTP {}", status);

        if (status == 401) {
            return new GitHubApiException(401, GitHubApiException.UNAUTHORIZED,
                    "Invalid or expired GitHub token. Please sign in again.");
        }

        if ((status == 403 || status == 429) && ("0".equals(remaining) || !retryAfter.isEmpty())) {
            return new GitHubApiException(429, GitHubApiException.RATE_LIMITED,
                    rateLimitMessage(header(response, "X-RateLimit-Reset"), retryAfter));
        }

        if (status == 403) {
            return new GitHubApiException(403, GitHubApiException.FORBIDDEN,
                    "The token is not allowed to read this repository.");
        }

        if (status == 404) {
            return new GitHubApiException(404, GitHubApiException.NOT_FOUND,
                    "Repository not found or not accessible with this token.");
        }

        return new GitHubApiException(502, GitHubApiException.ERROR,
                "GitHub returned an error (HTTP " + status + "). Please try again.");

    }

    private GitHubApiException graphQlError(JsonObject error) {

        String type = text(error, "type");
        String message = text(error, "message");

        logger.warn("GitHub GraphQL error: type={} message={}", type, message);

        if ("NOT_FOUND".equals(type)) {
            return new GitHubApiException(404, GitHubApiException.NOT_FOUND,
                    "Repository not found or not accessible with this token.");
        }

        if ("RATE_LIMITED".equals(type)) {
            return new GitHubApiException(429, GitHubApiException.RATE_LIMITED, rateLimitMessage("", ""));
        }

        if ("FORBIDDEN".equals(type)) {
            return new GitHubApiException(403, GitHubApiException.FORBIDDEN,
                    "The token is not allowed to read this repository.");
        }

        return new GitHubApiException(502, GitHubApiException.ERROR,
                "GitHub returned an error: " + (message == null ? type : message));

    }

    private static String header(HttpResponse<?> response, String name) {
        String value = response.getHeaders().getFirst(name);
        return value == null ? "" : value.trim();
    }

    private static String rateLimitMessage(String resetEpoch, String retryAfter) {

        String base = "GitHub API rate limit reached.";

        try {
            if (resetEpoch != null && !resetEpoch.isEmpty()) {
                return base + " It resets at " + java.time.Instant.ofEpochSecond(Long.parseLong(resetEpoch)) + ".";
            }
            if (retryAfter != null && !retryAfter.isEmpty()) {
                return base + " Try again in " + Long.parseLong(retryAfter) + " seconds.";
            }
        } catch (NumberFormatException ignored) {
            // fall through to the generic message
        }

        return base + " Please try again later.";

    }

    private static String notFoundMessage(CommitRequestBody request) {
        return String.format("Repository %s/%s not found or not accessible with this token.",
                request.getOwner(), request.getRepository());
    }

    static int maxCommits() {

        String value = System.getProperty(MAX_COMMITS_PROPERTY);
        if (value == null) value = System.getenv(MAX_COMMITS_PROPERTY);

        try {
            int parsed = value == null ? DEFAULT_MAX_COMMITS : Integer.parseInt(value.trim());
            return parsed < 1 ? DEFAULT_MAX_COMMITS : parsed;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_COMMITS;
        }

    }

    private static JsonObject child(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || !parent.get(name).isJsonObject()) return null;
        return parent.getAsJsonObject(name);
    }

    private static String text(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || parent.get(name).isJsonNull()) return null;
        return parent.get(name).getAsString();
    }

    private static int integer(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || parent.get(name).isJsonNull()) return 0;
        return parent.get(name).getAsInt();
    }

    /**
     * Fetch a GitHub user by login. Returns a Contributor-like object (id, login, avatarUrl, htmlUrl)
     * or null if the user does not exist (404).
     */
    /**
     * GitHub user by login, read with the session token (no anonymous rate limit), or null when it does not exist.
     *
     * @throws GitHubApiException when GitHub cannot be read for another reason
     */
    public Contributor getUser(String username, String accessToken) {

        if (username == null || username.trim().isEmpty()) return null;

        HttpResponse<String> response;
        try {
            kong.unirest.GetRequest request = Unirest.get(baseUrl + "/users/{username}")
                    .header("Accept", "application/json")
                    .header("X-GitHub-Api-Version", githubApiVersion)
                    .routeParam("username", username.trim());
            if (accessToken != null && !accessToken.isEmpty()) request = request.header("Authorization", "Bearer " + accessToken);
            response = request.asString();
        } catch (Exception e) {
            logger.error("Error fetching user {} from GitHub: {}", username, e.getMessage());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Could not connect to GitHub. Please try again.", e);
        }

        if (response.getStatus() == 404) return null;
        if (response.getStatus() != 200) throw httpError(response);

        try {
            Contributor user = GSON.fromJson(response.getBody(), Contributor.class);
            return user == null || user.getLogin() == null ? null : user;
        } catch (RuntimeException e) {
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Invalid response from GitHub.", e);
        }

    }

}
