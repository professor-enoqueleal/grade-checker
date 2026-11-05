package br.com.gradechecker.proxy;

import br.com.gradechecker.model.Contributor;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GitHubProxyTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    @Test
    public void getContributors_returnsList() {
        String owner = "octocat";
        String repo = "hello-world";

        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/contributors"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"login\":\"octocat\", \"contributions\": 42}]")
                        .withStatus(200)));

        String baseUrl = "http://localhost:" + wireMockRule.port();

        GitHubProxy proxy = new GitHubProxy(baseUrl, "2022-11-28");

        List<Contributor> contributors = proxy.getContributors("dummy-token", owner, repo);

        assertNotNull(contributors);
        assertEquals(1, contributors.size());
        assertEquals("octocat", contributors.get(0).getLogin());
        assertEquals(42, contributors.get(0).getContributions());
    }

}

