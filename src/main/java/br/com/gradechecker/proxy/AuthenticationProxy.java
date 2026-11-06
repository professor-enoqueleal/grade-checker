package br.com.gradechecker.proxy;

import br.com.gradechecker.config.GitHubOAuthConfig;
import br.com.gradechecker.model.AccessToken;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthenticationProxy {

    private final Logger logger = LogManager.getLogger(this.getClass());

    /**
     * Exchanges the OAuth code for a user access token.
     *
     * @return the token, or null when GitHub does not return one (expired/invalid code)
     * @throws IllegalStateException when the client credentials are not configured
     * @throws RuntimeException when the call to GitHub fails
     */
    public AccessToken exchangeCodePerToken(String code) {

        String clientId = GitHubOAuthConfig.clientId();
        String clientSecret = GitHubOAuthConfig.clientSecret();

        if (clientId == null || clientSecret == null) {
            logger.error("GitHub client credentials not configured. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables or system properties.");
            throw new IllegalStateException("GitHub client credentials not configured");
        }

        String url = GitHubOAuthConfig.baseUrl() + "/login/oauth/access_token";

        try {

            HttpResponse<AccessToken> responseEntity = Unirest.post(url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("client_id", clientId)
                    .field("client_secret", clientSecret)
                    .field("code", code)
                    .asObject(AccessToken.class);

            AccessToken token = responseEntity.getBody();

            if (responseEntity.getStatus() != 200 || token == null || token.getAccessToken() == null || token.getAccessToken().isEmpty()) {
                logger.warn("GitHub did not return an access token (HTTP {})", responseEntity.getStatus());
                return null;
            }

            logger.info("success on exchange code per token");

            return token;

        } catch (Exception e) {

            logger.error("Error on exchange code per token. Cause: {}", e.getMessage());

            throw new RuntimeException("Error on exchange code per token", e);

        }

    }

}
