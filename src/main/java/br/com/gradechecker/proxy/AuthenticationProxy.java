package br.com.gradechecker.proxy;

import br.com.gradechecker.model.AccessToken;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthenticationProxy {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private static final String clientId = System.getenv("GITHUB_CLIENT_ID");

    private static final String clientSecret = System.getenv("GITHUB_CLIENT_SECRET");

    private static final String baseURL = System.getenv("GITHUB_BASE_URL");

    public AccessToken exchangeCodePerToken(String code) {

        String url = baseURL.concat( "/login/oauth/access_token");

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
