package br.com.gradechecker.proxy;

import br.com.gradechecker.model.AccessToken;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthenticationProxy {

    private final Logger logger = LogManager.getLogger(this.getClass());

    // baseURL can still come from env var
    private static final String baseURL = System.getProperty("GITHUB_BASE_URL") != null ? System.getProperty("GITHUB_BASE_URL") : System.getenv("GITHUB_BASE_URL");

    public AccessToken exchangeCodePerToken(String code) {

        String clientId = System.getProperty("GITHUB_CLIENT_ID");
        if (clientId == null) clientId = System.getenv("GITHUB_CLIENT_ID");

        String clientSecret = System.getProperty("GITHUB_CLIENT_SECRET");
        if (clientSecret == null) clientSecret = System.getenv("GITHUB_CLIENT_SECRET");

        if (clientId == null || clientSecret == null) {
            logger.error("GitHub client credentials not configured. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables or system properties.");
            throw new IllegalStateException("GitHub client credentials not configured");
        }

        String url = (baseURL != null ? baseURL : "https://github.com").concat("/login/oauth/access_token");

        try {

            HttpResponse<AccessToken> responseEntity = Unirest.post(url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("client_id", clientId)
                    .field("client_secret", clientSecret)
                    .field("code", code)
                    .asObject(AccessToken.class);

            responseEntity.getStatus();

            logger.info("success on exchange code per token");

            return responseEntity.getBody();

        } catch (Exception e) {

            logger.error("Error on exchange code per token. Cause: {}", e.getMessage());

            throw new RuntimeException("Error on exchange code per token");

        }

    }

}
