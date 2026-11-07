package br.com.gradechecker.proxy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** openspec capability platform-admin: GET /user at sign-in. */
public class GitHubUserProxyTest {

    @Rule
    public WireMockRule wireMock = new WireMockRule(0);

    private GitHubUserProxy proxy() {
        return new GitHubUserProxy("http://localhost:" + wireMock.port());
    }

    private void expectFailure(int status, String code) {
        try {
            proxy().fetchLogin("ghp_x");
            fail("expected GitHubApiException");
        } catch (GitHubApiException e) {
            assertEquals(status, e.getHttpStatus());
            assertEquals(code, e.getErrorCode());
        }
    }

    @Test
    public void returnsTheLoginOfTheTokenOwner() {
        stubFor(get("/user").withHeader("Authorization", equalTo("Bearer ghp_x"))
                .willReturn(okJson("{\"login\":\"professor-enoqueleal\",\"id\":1}")));

        assertEquals("professor-enoqueleal", proxy().fetchLogin("ghp_x"));
    }

    @Test
    public void rejectedTokenIs401() {
        stubFor(get("/user").willReturn(unauthorized().withBody("{\"message\":\"Bad credentials\"}")));
        expectFailure(401, GitHubApiException.UNAUTHORIZED);
    }

    @Test
    public void serverErrorIs502() {
        stubFor(get("/user").willReturn(serverError()));
        expectFailure(502, GitHubApiException.ERROR);
    }

    @Test
    public void bodyWithoutLoginIs502() {
        stubFor(get("/user").willReturn(okJson("{\"id\":1}")));
        expectFailure(502, GitHubApiException.ERROR);
    }

    @Test
    public void unreachableGitHubIs502() {
        GitHubUserProxy unreachable = new GitHubUserProxy("http://localhost:1");
        try {
            unreachable.fetchLogin("ghp_x");
            fail("expected GitHubApiException");
        } catch (GitHubApiException e) {
            assertEquals(502, e.getHttpStatus());
        }
    }
}
