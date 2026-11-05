package br.com.gradechecker.proxy;

import br.com.gradechecker.cache.CacheManager;
import br.com.gradechecker.model.*;
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

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final String githubApiVersion;
    private final String baseUrl;

    public GitHubProxy() {
        this("https://api.github.com", "2022-11-28");
    }

    public GitHubProxy(String baseUrl, String apiVersion) {
        this.baseUrl = baseUrl;
        this.githubApiVersion = apiVersion;
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

    public List<Contributor> getContributors(String accessKey, String owner, String repo) {

        final String cacheKey = String.format("github.contributors::%s:%s:%s", owner, repo, tokenHash(accessKey));

        @SuppressWarnings("unchecked")
        List<Contributor> cached = CacheManager.get(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        String url = baseUrl + "/repos/{owner}/{repo}/contributors";

        try {

            String bearerToken = String.format("Bearer %s", accessKey);

            HttpResponse<List<Contributor>> responseEntity = Unirest.get(url)
                    .header("Accept", "application/json")
                    .header("Authorization", bearerToken)
                    .header("X-GitHub-Api-Version", githubApiVersion)
                    .routeParam("owner", owner)
                    .routeParam("repo", repo)
                    .asObject(new GenericType<List<Contributor>>() {
                    });

            logger.info("success on get contributors information on github");

            List<Contributor> body = responseEntity.getBody();
            CacheManager.put(cacheKey, body);

            return body;

        } catch (Exception e) {

            logger.error("Error on retrieve contributors information on github. Cause: {}", e.getMessage());

            throw new RuntimeException("Error on retrieve contributors information on github");

        }

    }

    public List<Commit> getCommits(CommitRequestBody commitRequestBody) {

        final String cacheKey = String.format("github.commits::%s:%s:%s:%s:%s:%s",
                commitRequestBody.getOwner(),
                commitRequestBody.getRepository(),
                commitRequestBody.getCommitAuthor() == null ? "" : commitRequestBody.getCommitAuthor(),
                commitRequestBody.getSince() == null ? "" : commitRequestBody.getSince(),
                commitRequestBody.getUntil() == null ? "" : commitRequestBody.getUntil(),
                tokenHash(commitRequestBody.getAccessToken())
        );

         @SuppressWarnings("unchecked")
         List<Commit> cached = CacheManager.get(cacheKey, List.class);
         if (cached != null) {
             return cached;
         }

         String url = baseUrl + "/repos/{owner}/{repo}/commits?since={since}&until={until}&author={author}&per_page={per_page}";

         try {

             String bearerToken = String.format("Bearer %s", commitRequestBody.getAccessToken());

             HttpResponse<List<Commit>> responseEntity = Unirest.get(url)
                     .header("Accept", "application/json")
                     .header("Authorization", bearerToken)
                     .header("X-GitHub-Api-Version", githubApiVersion)
                     .routeParam("owner", commitRequestBody.getOwner())
                     .routeParam("repo", commitRequestBody.getRepository())
                     .routeParam("author", commitRequestBody.getCommitAuthor())
                     .routeParam("since", commitRequestBody.getSince())
                     .routeParam("until", commitRequestBody.getUntil())
                     .routeParam("per_page", "100")
                     .asObject(new GenericType<List<Commit>>() {
                     });

             logger.info("success on get commits information");

             List<Commit> body = responseEntity.getBody();
             CacheManager.put(cacheKey, body);

             return body;

         } catch (Exception e) {

             logger.error("Error on retrieve commits information. Cause: {}", e.getMessage());

             throw new RuntimeException("Error on retrieve commits information");

         }

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

     public List<ContributorStats> getContributorStats(CommitRequestBody commitRequestBody) {

        final String cacheKey = String.format("github.stats.contributors::%s:%s:%s",
                commitRequestBody.getOwner(),
                commitRequestBody.getRepository(),
                tokenHash(commitRequestBody.getAccessToken())
        );

         final String commitAuthor = commitRequestBody.getCommitAuthor();

         @SuppressWarnings("unchecked")
         List<ContributorStats> cached = CacheManager.get(cacheKey, List.class);

         if (cached != null) {
             return cached;
         }

         // use the commits API to obtain commit list and aggregate per author to build ContributorStats
         String url = baseUrl + "/repos/{owner}/{repo}/commits?author={author}";

         String bearerToken = String.format("Bearer %s", commitRequestBody.getAccessToken());

         try {

            // 1) Try to directly deserialize into ContributorStats list (if API returned stats-shaped JSON)
            HttpResponse<List<ContributorStats>> statsResp = Unirest.get(url)
                    .header("Accept", "application/json")
                    .header("Authorization", bearerToken)
                    .header("X-GitHub-Api-Version", githubApiVersion)
                    .routeParam("owner", commitRequestBody.getOwner())
                    .routeParam("repo", commitRequestBody.getRepository())
                    .routeParam("author", commitRequestBody.getCommitAuthor())
                    .asObject(new GenericType<java.util.List<ContributorStats>>(){});

            if (statsResp.getStatus() == 200 && statsResp.getBody() != null && !statsResp.getBody().isEmpty() && statsResp.getBody().get(0) != null && statsResp.getBody().get(0).getAuthor() != null) {
                CacheManager.put(cacheKey, statsResp.getBody());
                return statsResp.getBody();
            }

            // 2) Fallback: deserialize into CommitWrapper list and aggregate
            HttpResponse<List<CommitWrapper>> commitsResp = Unirest.get(url)
                    .header("Accept", "application/json")
                    .header("Authorization", bearerToken)
                    .header("X-GitHub-Api-Version", githubApiVersion)
                    .routeParam("owner", commitRequestBody.getOwner())
                    .routeParam("repo", commitRequestBody.getRepository())
                    .routeParam("author", commitRequestBody.getCommitAuthor())
                    .asObject(new GenericType<List<CommitWrapper>>(){});

            if (commitsResp.getStatus() != 200 || commitsResp.getBody() == null) {
                CacheManager.put(cacheKey, java.util.Collections.emptyList());
                return java.util.Collections.emptyList();
            }

            Map<String, Integer> counts = new java.util.HashMap<>();

            for (CommitWrapper cw : commitsResp.getBody()) {
                if (cw == null) continue;
                String key = null;
                if (cw.author != null && cw.author.getLogin() != null) {
                    key = cw.author.getLogin();
                } else if (cw.commit != null && cw.commit.getCommit() != null && cw.commit.getCommit().getCommitter() != null && cw.commit.getCommit().getCommitter().getName() != null) {
                    key = cw.commit.getCommit().getCommitter().getName();
                }
                if (key != null) counts.put(key, counts.getOrDefault(key, 0) + 1);
            }

            List<ContributorStats> stats = new java.util.ArrayList<>();

            for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
                ContributorStats cs = new ContributorStats();
                // use constructor for clarity: (login, id, avatarUrl, htmlUrl, contributions)
                Contributor author = new Contributor(e.getKey(), 0, null, null, e.getValue());
                cs.setAuthor(author);
                cs.setTotal(e.getValue());
                stats.add(cs);
            }

            CacheManager.put(cacheKey, stats);
            return stats;

        } catch (Exception e) {
            logger.error("Error on retrieve contributor stats on github. Cause: {}", e.getMessage());
            throw new RuntimeException("Error on retrieve contributor stats on github", e);
        }

    }

    /**
     * Fetch a GitHub user by login. Returns a Contributor-like object (id, login, avatarUrl, htmlUrl)
     * or null if the user does not exist (404).
     */
    public Contributor getUser(String username) {

        if (username == null || username.trim().isEmpty()) return null;

        String url = baseUrl + "/users/{username}";

        try {

            HttpResponse<Contributor> response = Unirest.get(url)
                    .header("Accept", "application/json")
                    .routeParam("username", username)
                    .asObject(Contributor.class);

            int status = response.getStatus();

            if (status == 200) {
                return response.getBody();
            } else if (status == 404) {
                return null;
            } else {
                logger.warn("Unexpected status {} when fetching user {}", status, username);
                return null;
            }

        } catch (Exception e) {
            logger.error("Error fetching user {} from GitHub: {}", username, e.getMessage());
            return null;
        }

    }

}
