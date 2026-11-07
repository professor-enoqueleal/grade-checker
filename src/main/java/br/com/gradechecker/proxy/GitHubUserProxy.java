package br.com.gradechecker.proxy;

import br.com.gradechecker.config.GitHubOAuthConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Who owns a token: GET {api}/user (openspec capability platform-admin). */
public class GitHubUserProxy {

    private static final String API_VERSION = "2022-11-28";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final String apiUrl; // null: read GITHUB_API_URL on each call

    public GitHubUserProxy() {
        this(null);
    }

    public GitHubUserProxy(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /**
     * @return the GitHub login of the token owner
     * @throws GitHubApiException 401 {@code github_unauthorized} when GitHub rejects the token;
     *                            502 {@code github_error} for any other failure (network, 5xx, unexpected body)
     */
    public String fetchLogin(String accessToken) {

        String url = (apiUrl != null ? apiUrl : GitHubOAuthConfig.apiUrl()) + "/user";

        HttpResponse<String> response;

        try {
            response = Unirest.get(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", API_VERSION)
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
