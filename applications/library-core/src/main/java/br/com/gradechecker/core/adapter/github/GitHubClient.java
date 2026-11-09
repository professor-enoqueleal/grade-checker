package br.com.gradechecker.core.adapter.github;

import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.github.GitHubRepository;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.internal.Log;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.ResultCache;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client of the GitHub API (REST and GraphQL), the implementation of {@link GitHubGateway} shared by the two
 * applications. Only reads and caches: reports and attributions are computed by the domain
 * ({@code core.attribution.ContributionAnalyzer}). Only complete results are cached, with keys that include a hash of
 * the token.
 */
public class GitHubClient implements GitHubGateway {

    public static final String DEFAULT_API_VERSION = "2022-11-28";

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

    private static final Gson GSON = new Gson();
    private static final Type CONTRIBUTOR_LIST = new TypeToken<List<Contributor>>() { }.getType();

    private final Log logger = Log.of(GitHubClient.class);

    private final String baseUrl;
    private final String apiVersion;
    /** Cache of the application (port): Caffeine in the Jetty version, Caffeine + DynamoDB in the serverless version. */
    private final ResultCache cache;

    /** No cache. */
    public GitHubClient(String baseUrl, String apiVersion) {
        this(baseUrl, apiVersion, ResultCache.NONE);
    }

    public GitHubClient(String baseUrl, String apiVersion, ResultCache cache) {
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.cache = cache == null ? ResultCache.NONE : cache;
    }

    /**
     * Contributors of the repository (accounts with commits on the default branch), up to 100
     * (openspec capability group-members). Never stored in the database; cached only on success.
     *
     * @throws GitHubApiException when GitHub cannot be read (401, 403, 404, 429, 502)
     */
    @Override
    public List<Contributor> getContributors(String owner, String repo, String accessToken) {

        // v2: the JSON of the domain Contributor (camelCase), not the one of GitHub
        final String cacheKey = String.format("github.contributors.v2::%s:%s:%s", owner, repo, tokenHash(accessToken));

        // typed: the second cache level (DynamoDB) rebuilds the list from JSON
        List<Contributor> cached = cache.get(cacheKey, CONTRIBUTOR_LIST);
        if (cached != null) {
            return cached;
        }

        HttpResponse<String> response;
        try {
            response = Unirest.get(baseUrl + "/repos/{owner}/{repo}/contributors")
                    .header("Accept", "application/json")
                    .header("Authorization", String.format("Bearer %s", accessToken))
                    .header("X-GitHub-Api-Version", apiVersion)
                    .routeParam("owner", owner)
                    .routeParam("repo", repo)
                    .queryString("per_page", 100)
                    .asString();
        } catch (Exception e) {
            logger.error("Error on retrieve contributors of {}/{}: {}", owner, repo, e.getMessage());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Could not connect to GitHub. Please try again.", e);
        }

        if (response.getStatus() == 204) {          // repository without commits
            List<Contributor> empty = Collections.emptyList();
            cache.put(cacheKey, empty);
            return empty;
        }
        if (response.getStatus() != 200) {
            throw httpError(response);
        }

        List<Contributor> contributors = new ArrayList<>();
        try {
            JsonElement parsed = JsonParser.parseString(response.getBody());
            if (parsed != null && parsed.isJsonArray()) {
                for (JsonElement element : (JsonArray) parsed) {
                    if (element != null && element.isJsonObject()) contributors.add(contributor(element.getAsJsonObject()));
                }
            }
        } catch (RuntimeException e) {
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Invalid response from GitHub.", e);
        }

        List<Contributor> result = Collections.unmodifiableList(contributors);
        logger.info("success on get contributors information on github");
        cache.put(cacheKey, result);
        return result;

    }

    /**
     * Commits of the default branch within [since, until] (merges included), read with GraphQL and shared by the
     * report, the grades and the commit history page. Only complete results are cached.
     *
     * @throws GitHubApiException when GitHub answers with an error (never returns an empty history on error)
     */
    @Override
    public RepositoryHistory getHistory(HistoryRequest request) {

        final int maxCommits = maxCommits();

        final String cacheKey = String.format("github.history::%s:%s:%s:%s:%d:%s",
                request.getOwner(),
                request.getRepository(),
                orDash(request.getSince()),
                orDash(request.getUntil()),
                maxCommits,
                tokenHash(request.getAccessToken())
        );

        RepositoryHistory cached = cache.get(cacheKey, RepositoryHistory.class);

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

        cache.put(cacheKey, result);

        return result;

    }

    private static boolean hasNextPageAfterLimit(int pages, int maxPages, String cursor) {
        return pages >= maxPages && cursor != null;
    }

    private JsonObject executeContributionsQuery(HistoryRequest request, String cursor) {

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
                return base + " It resets at " + Instant.ofEpochSecond(Long.parseLong(resetEpoch)) + ".";
            }
            if (retryAfter != null && !retryAfter.isEmpty()) {
                return base + " Try again in " + Long.parseLong(retryAfter) + " seconds.";
            }
        } catch (NumberFormatException ignored) {
            // fall through to the generic message
        }

        return base + " Please try again later.";

    }

    private static String notFoundMessage(HistoryRequest request) {
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
     * GitHub user by login, read with the session token (no anonymous rate limit), or null when it does not exist.
     *
     * @throws GitHubApiException when GitHub cannot be read for another reason
     */
    @Override
    public Contributor getUser(String login, String accessToken) {

        if (login == null || login.trim().isEmpty()) return null;

        HttpResponse<String> response;
        try {
            GetRequest request = Unirest.get(baseUrl + "/users/{username}")
                    .header("Accept", "application/json")
                    .header("X-GitHub-Api-Version", apiVersion)
                    .routeParam("username", login.trim());
            if (accessToken != null && !accessToken.isEmpty()) request = request.header("Authorization", "Bearer " + accessToken);
            response = request.asString();
        } catch (Exception e) {
            logger.error("Error fetching user {} from GitHub: {}", login, e.getMessage());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Could not connect to GitHub. Please try again.", e);
        }

        if (response.getStatus() == 404) return null;
        if (response.getStatus() != 200) throw httpError(response);

        try {
            JsonObject json = JsonParser.parseString(response.getBody()).getAsJsonObject();
            return text(json, "login") == null ? null : contributor(json);
        } catch (RuntimeException e) {
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Invalid response from GitHub.", e);
        }

    }

    /**
     * Repository of a group, read with the session token (openspec capability group-management), or null when it does
     * not exist or the token cannot see it (404).
     *
     * @throws GitHubApiException when GitHub cannot be read for another reason
     */
    @Override
    public GitHubRepository getRepository(String owner, String repo, String accessToken) {

        HttpResponse<String> response;
        try {
            GetRequest request = Unirest.get(baseUrl + "/repos/{owner}/{repo}")
                    .header("Accept", "application/json")
                    .header("X-GitHub-Api-Version", apiVersion)
                    .routeParam("owner", owner)
                    .routeParam("repo", repo);
            if (accessToken != null && !accessToken.isEmpty()) request = request.header("Authorization", "Bearer " + accessToken);
            response = request.asString();
        } catch (Exception e) {
            logger.error("Error fetching repository {}/{} from GitHub: {}", owner, repo, e.getMessage());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Could not connect to GitHub. Please try again.", e);
        }

        if (response.getStatus() == 404) return null;
        if (response.getStatus() != 200) throw httpError(response);

        try {
            JsonObject json = JsonParser.parseString(response.getBody()).getAsJsonObject();
            String ownerLogin = text(child(json, "owner"), "login");
            String htmlUrl = text(json, "html_url");
            if (ownerLogin == null || htmlUrl == null) return null;
            boolean isPrivate = json.has("private") && !json.get("private").isJsonNull() && json.get("private").getAsBoolean();
            return new GitHubRepository(ownerLogin, text(json, "name"), htmlUrl, isPrivate);
        } catch (RuntimeException e) {
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Invalid response from GitHub.", e);
        }
    }

    private static Contributor contributor(JsonObject json) {
        long id = json.has("id") && !json.get("id").isJsonNull() ? json.get("id").getAsLong() : 0;
        return new Contributor(text(json, "login"), id, text(json, "avatar_url"), text(json, "html_url"),
                integer(json, "contributions"));
    }

    private static String tokenHash(String token) {
        if (token == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            return Integer.toString(token.hashCode());
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String orDash(String value) {
        return isPresent(value) ? value : "-";
    }
}
