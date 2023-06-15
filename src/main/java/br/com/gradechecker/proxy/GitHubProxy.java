package br.com.gradechecker.proxy;

import br.com.gradechecker.model.Commit;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.Contributor;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class GitHubProxy {

    private final Logger logger = LogManager.getLogger(this.getClass());


    public GitHubProxy() {

    }

    public List<Contributor> getContributors(String accessKey, String owner, String repo) {

        String url = "https://api.github.com/repos/{owner}/{repo}/contributors";

        try {

            String bearerToken = String.format("Bearer %s", accessKey);

            HttpResponse<List<Contributor>> responseEntity = Unirest.get(url)
                    .header("Accept", "application/json")
                    .header("Authorization", bearerToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .routeParam("owner", owner)
                    .routeParam("repo", repo)
                    .asObject(new GenericType<List<Contributor>>() {
                    });

            responseEntity.getStatus();

            logger.info("success on get github repository information");

            return responseEntity.getBody();

        } catch (Exception e) {

            logger.error("Error on retrieve contributors info. Cause: {}", e.getMessage());

            throw new RuntimeException("Error on retrieve contributors info");

        }

    }

    public List<Commit> getCommits(CommitRequestBody commitRequestBody) {

        String url = "https://api.github.com/repos/{owner}/{repo}/commits?since={since}&until={until}&author={author}";

        try {

            String bearerToken = String.format("Bearer %s", commitRequestBody.getAccessToken());

            HttpResponse<List<Commit>> responseEntity = Unirest.get(url)
                    .header("Accept", "application/json")
                    .header("Authorization", bearerToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .routeParam("owner", commitRequestBody.getOwner())
                    .routeParam("repo", commitRequestBody.getRepository())
                    .routeParam("author", commitRequestBody.getCommitAuthor())
                    .routeParam("since", commitRequestBody.getSince())
                    .routeParam("until", commitRequestBody.getUntil())
                    .asObject(new GenericType<List<Commit>>() {
                    });

            responseEntity.getStatus();

            logger.info("success on get github repository information");

            return responseEntity.getBody();

        } catch (Exception e) {

            logger.error("Error on retrieve commits info. Cause: {}", e.getMessage());

            throw new RuntimeException("Error on retrieve commits info");

        }

    }

}
