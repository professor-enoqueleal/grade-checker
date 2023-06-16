package br.com.gradechecker.proxy;

import br.com.gradechecker.model.AccessToken;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthenticationProxy {

    private final Logger logger = LogManager.getLogger(this.getClass());

    public AccessToken exchangeCodePerToken(String code) {

        String url = "https://github.com/login/oauth/access_token";

        try {

            HttpResponse<AccessToken> responseEntity = Unirest.post(url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("client_id", "")
                    .field("client_secret", "")
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
