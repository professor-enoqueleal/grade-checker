package br.com.gradechecker.core.adapter.github;

import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.internal.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

/**
 * Sign-in with the GitHub App: exchanges the OAuth code for a user token and identifies the owner of a token
 * (openspec capabilities github-oauth and platform-admin). The settings are read on every call.
 */
public class GitHubOAuthClient {

    private final Log logger = Log.of(GitHubOAuthClient.class);

    private final GitHubOAuthSettings settings;

    /** @param settings client id, client secret, OAuth base URL and API URL of the application */
    public GitHubOAuthClient(GitHubOAuthSettings settings) {
        this.settings = settings;
    }

    /**
     * Exchanges the OAuth code for a user access token.
     *
     * @return the token, or null when GitHub does not return one (expired/invalid code)
     * @throws IllegalStateException when the client credentials are not configured
     * @throws RuntimeException when the call to GitHub fails
     */
    public AccessToken exchangeCodeForToken(String code) {

        String clientId = settings.clientId();
        String clientSecret = settings.clientSecret();

        if (clientId == null || clientSecret == null) {
            logger.error("GitHub client credentials not configured. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables or system properties.");
            throw new IllegalStateException("GitHub client credentials not configured");
        }

        try {

            HttpResponse<AccessToken> response = Unirest.post(settings.baseUrl() + "/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("client_id", clientId)
                    .field("client_secret", clientSecret)
                    .field("code", code)
                    .asObject(AccessToken.class);

            AccessToken token = response.getBody();

            if (response.getStatus() != 200 || token == null || token.getAccessToken() == null || token.getAccessToken().isEmpty()) {
                logger.warn("GitHub did not return an access token (HTTP {})", response.getStatus());
                return null;
            }

            logger.info("success on exchange code for token");
            return token;

        } catch (Exception e) {
            logger.error("Error on exchange code for token. Cause: {}", e.getMessage());
            throw new RuntimeException("Error on exchange code for token", e);
        }

    }

    /**
     * Who owns a token: GET {api}/user.
     *
     * @return the GitHub login of the token owner
     * @throws GitHubApiException 401 {@code github_unauthorized} when GitHub rejects the token;
     *                            502 {@code github_error} for any other failure (network, 5xx, unexpected body)
     */
    public String fetchLogin(String accessToken) {

        HttpResponse<String> response;

        try {
            response = Unirest.get(settings.apiUrl() + "/user")
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", GitHubClient.DEFAULT_API_VERSION)
                    .asString();
        } catch (Exception e) {
            logger.error("Could not reach GitHub to identify the user: {}", e.getMessage());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Could not reach GitHub.", e);
        }

        if (response.getStatus() == 401) {
            throw new GitHubApiException(401, GitHubApiException.UNAUTHORIZED, "GitHub rejected the token.");
        }

        if (response.getStatus() != 200) {
            logger.warn("GET /user answered HTTP {}", response.getStatus());
            throw new GitHubApiException(502, GitHubApiException.ERROR, "Unexpected answer from GitHub (HTTP " + response.getStatus() + ").");
        }

        try {
            JsonElement login = JsonParser.parseString(response.getBody()).getAsJsonObject().get("login");
            if (login != null && login.isJsonPrimitive() && !login.getAsString().trim().isEmpty()) {
                return login.getAsString().trim();
            }
        } catch (RuntimeException e) {
            logger.warn("GET /user returned an unexpected body: {}", e.getMessage());
        }

        throw new GitHubApiException(502, GitHubApiException.ERROR, "GitHub did not return the user login.");
    }
}
