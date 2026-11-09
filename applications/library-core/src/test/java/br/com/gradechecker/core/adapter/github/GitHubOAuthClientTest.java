package br.com.gradechecker.core.adapter.github;

import br.com.gradechecker.core.github.GitHubApiException;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/** Sign-in with the GitHub App: code exchange (capability github-oauth) and GET /user (capability platform-admin). */
public class GitHubOAuthClientTest {

    @Rule
    public WireMockRule wireMock = new WireMockRule(0);

    private GitHubOAuthClient client(String clientId, String clientSecret) {
        String url = "http://localhost:" + wireMock.port();
        return new GitHubOAuthClient(GitHubOAuthSettings.of(() -> url, () -> url, () -> clientId, () -> clientSecret));
    }

    private GitHubOAuthClient client() {
        return client("id", "secret");
    }

    private void expectFailure(int status, String code) {
        try {
            client().fetchLogin("ghp_x");
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

        assertEquals("professor-enoqueleal", client().fetchLogin("ghp_x"));
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
        GitHubOAuthClient unreachable = new GitHubOAuthClient(
                GitHubOAuthSettings.of(null, () -> "http://localhost:1", () -> "id", () -> "secret"));
        try {
            unreachable.fetchLogin("ghp_x");
            fail("expected GitHubApiException");
        } catch (GitHubApiException e) {
            assertEquals(502, e.getHttpStatus());
        }
    }

    @Test
    public void exchangesTheCodeForAUserToken() {
        stubFor(post("/login/oauth/access_token")
                .withRequestBody(containing("client_id=id")).withRequestBody(containing("code=abc"))
                .willReturn(okJson("{\"access_token\":\"ghu_1\",\"token_type\":\"bearer\",\"refresh_token\":\"ghr_1\"}")));

        AccessToken token = client().exchangeCodeForToken("abc");

        assertEquals("ghu_1", token.getAccessToken());
        assertEquals("ghr_1", token.getRefreshToken());
    }

    @Test
    public void expiredCodeGivesNoToken() {
        stubFor(post("/login/oauth/access_token").willReturn(okJson("{\"error\":\"bad_verification_code\"}")));
        assertNull(client().exchangeCodeForToken("old"));
    }

    @Test(expected = IllegalStateException.class)
    public void exchangeWithoutClientCredentialsIsRefused() {
        client("id", null).exchangeCodeForToken("abc");
    }
}
