package br.com.gradechecker.adapter.inbound.http.routes;

import br.com.gradechecker.adapter.inbound.http.Call;
import br.com.gradechecker.adapter.inbound.http.Maps;
import br.com.gradechecker.adapter.inbound.http.Request;
import br.com.gradechecker.adapter.inbound.http.Response;
import br.com.gradechecker.adapter.inbound.http.Router;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoSessionStore;
import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GitHubOAuthConfig;
import br.com.gradechecker.core.adapter.github.AccessToken;
import br.com.gradechecker.core.adapter.github.GitHubOAuthClient;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.session.Session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static br.com.gradechecker.adapter.inbound.http.Router.Access.PUBLIC;

/**
 * Session routes (capability serverless-session-auth): who is signed in, sign in with GitHub (OAuth with state) or with a
 * personal access token, sign out. The session lives in DynamoDB; the browser only gets an HttpOnly cookie.
 */
public final class AuthRoutes {

    public static final String SESSION_COOKIE = "__Host-gc_session";
    public static final String OAUTH_COOKIE = "__Host-gc_oauth";

    static final String MISSING_TOKEN = "Please enter a personal access token.";
    static final String REJECTED_TOKEN = "GitHub rejected this personal access token.";
    static final String UNVERIFIED_TOKEN = "Could not verify the token with GitHub. Please try again.";

    private static final Logger logger = LogManager.getLogger(AuthRoutes.class);

    private final DynamoSessionStore sessions;
    private final GitHubOAuthClient gitHubOAuth;

    public AuthRoutes(DynamoSessionStore sessions, GitHubOAuthClient gitHubOAuth) {
        this.sessions = sessions;
        this.gitHubOAuth = gitHubOAuth;
    }

    public void register(Router router) {
        router.get("/api/me", PUBLIC, this::me)
                .post("/api/auth/token", PUBLIC, this::token)
                .post("/api/auth/logout", PUBLIC, this::logout)
                .get("/api/auth/github", PUBLIC, this::gitHub)
                .get("/api/auth/callback", PUBLIC, this::callback);
    }

    /** Session of the request (cookie), or null when missing or expired. */
    public static Session sessionOf(DynamoSessionStore sessions, Request request) {
        String id = request.cookie(SESSION_COOKIE);
        return id == null ? null : sessions.find(id);
    }

    static Object me(String login) {
        return Maps.of("login", login, "isAdmin", AdminConfig.isAdmin(login), "avatarUrl", "https://github.com/" + login + ".png?size=64");
    }

    private Response me(Call call) {
        if (call.session() == null) return Response.error(401, "unauthorized", Router.UNAUTHORIZED);
        return Response.ok(me(call.login()));
    }

    private Response token(Call call) {
        String token = call.request().string("token");
        if (token == null) return Response.error(400, "bad_request", MISSING_TOKEN);
        String login;
        try {
            login = gitHubOAuth.fetchLogin(token);
        } catch (GitHubApiException e) {
            if (e.getHttpStatus() == 401) return Response.error(401, "unauthorized", REJECTED_TOKEN);
            return Response.error(502, "github_error", UNVERIFIED_TOKEN);
        }
        Session session = sessions.create(login, token);
        logger.info("{} signed in with a personal access token", login);
        return Response.ok(me(login)).cookie(sessionCookie(session.getId(), sessions.maxSeconds()));
    }

    private Response logout(Call call) {
        String id = call.request().cookie(SESSION_COOKIE);
        if (id != null) sessions.delete(id);
        return Response.ok(Maps.of("signedOut", true)).cookie(clear(SESSION_COOKIE));
    }

    /** Starts the OAuth of the GitHub App: random state in DynamoDB and in a short cookie, then GitHub. */
    private Response gitHub(Call call) {
        String clientId = GitHubOAuthConfig.clientId();
        if (clientId == null) {
            logger.error("GitHub sign-in requested but GITHUB_CLIENT_ID is not configured");
            return Response.redirect("/login?error=github_failed");
        }
        String state = sessions.createState();
        return Response.redirect(GitHubOAuthConfig.baseUrl() + "/login/oauth/authorize"
                        + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8))
                .cookie(OAUTH_COOKIE + "=" + state + "; Path=/; Max-Age=" + DynamoSessionStore.STATE_SECONDS + "; HttpOnly; Secure; SameSite=Lax");
    }

    /** Back from GitHub: state checked (same as the cookie, stored, single use), code exchanged, session created. */
    private Response callback(Call call) {
        Request request = call.request();
        String code = request.query("code");
        if (code == null) {                                   // e.g. the user denied access on GitHub
            return Response.redirect("/login?error=github_failed").cookie(clear(OAUTH_COOKIE));
        }
        if (!stateIsValid(request.query("state"), request.cookie(OAUTH_COOKIE))) {
            logger.warn("GitHub callback rejected: missing or invalid state");
            return Response.redirect("/login?error=github_not_verified").cookie(clear(OAUTH_COOKIE));
        }

        AccessToken accessToken;
        String login;
        try {
            accessToken = gitHubOAuth.exchangeCodeForToken(code);
            if (accessToken == null) return Response.redirect("/login?error=github_failed").cookie(clear(OAUTH_COOKIE));
            login = gitHubOAuth.fetchLogin(accessToken.getAccessToken());
        } catch (RuntimeException e) {
            logger.error("GitHub sign-in failed: {}", e.getMessage());
            return Response.redirect("/login?error=github_failed").cookie(clear(OAUTH_COOKIE));
        }

        Session session = sessions.create(login, accessToken.getAccessToken());
        logger.info("{} signed in with GitHub", login);
        return Response.redirect("/home").cookie(sessionCookie(session.getId(), sessions.maxSeconds())).cookie(clear(OAUTH_COOKIE));
    }

    /** The state of the URL must equal the cookie (constant time) and exist in DynamoDB; it is consumed in any case. */
    private boolean stateIsValid(String received, String fromCookie) {
        if (received == null) return false;
        boolean stored = sessions.consumeState(received);
        return stored && fromCookie != null && MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), fromCookie.getBytes(StandardCharsets.UTF_8));
    }

    /** Max-Age is the absolute lifetime; the idle deadline is checked on the server (DynamoSessionStore). */
    static String sessionCookie(String id, long maxAgeSeconds) {
        return SESSION_COOKIE + "=" + id + "; Path=/; Max-Age=" + maxAgeSeconds + "; HttpOnly; Secure; SameSite=Lax";
    }

    static String clear(String name) {
        return name + "=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax";
    }
}
