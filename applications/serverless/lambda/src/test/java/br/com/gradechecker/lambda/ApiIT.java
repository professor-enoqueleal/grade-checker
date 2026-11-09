package br.com.gradechecker.lambda;

import br.com.gradechecker.adapter.inbound.http.Events;
import br.com.gradechecker.adapter.inbound.http.routes.AuthRoutes;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient;
import br.com.gradechecker.adapter.outbound.dynamodb.LocalTables;
import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GitHubOAuthConfig;
import br.com.gradechecker.core.adapter.github.GitHubClient;
import br.com.gradechecker.core.adapter.github.GitHubOAuthClient;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;

/**
 * The API end to end over DynamoDB Local (Docker, started by scripts/local-dynamodb.sh) with GitHub simulated by
 * WireMock: capabilities session-auth, groups-api, members-api, identity-aliases-api, contributions-api, gradebook-api.
 * Each run uses its own tables. Run with: mvn test -Pintegration
 */
public class ApiIT {

    private static WireMockServer gitHub;
    private static LocalTables tables;
    private ApplicationHandler api;

    @BeforeClass
    public static void startAll() {
        gitHub = new WireMockServer(options().dynamicPort());
        gitHub.start();
        tables = LocalTables.create();
    }

    @AfterClass
    public static void stopAll() {
        if (tables != null) tables.drop();
        if (gitHub != null) gitHub.stop();
    }

    @Before
    public void setUp() {
        gitHub.resetAll();
        tables.clear();
        System.setProperty(AdminConfig.ADMINS, "professor");
        System.setProperty(GitHubOAuthConfig.BASE_URL, gitHub.baseUrl());
        System.setProperty(GitHubOAuthConfig.API_URL, gitHub.baseUrl());
        System.setProperty(GitHubOAuthConfig.CLIENT_ID, "client-id");
        System.setProperty(GitHubOAuthConfig.CLIENT_SECRET, "client-secret");

        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
        PeriodResolver periods = new PeriodResolver(Milestones.of(semester), clock);

        DynamoClient dynamo = tables.dynamo();
        AppContext ctx = new AppContext(dynamo, new GitHubClient(gitHub.baseUrl(), GitHubClient.DEFAULT_API_VERSION),
                new GitHubOAuthClient(GitHubOAuthConfig.settings()), periods, GradingConfig.defaults(), clock);
        api = new ApplicationHandler(ctx);

        stubUser("good-admin", "professor");
        stubUser("good-student", "ana");
        gitHub.stubFor(get(urlEqualTo("/user")).withHeader("Authorization", equalTo("Bearer bad"))
                .willReturn(aResponse().withStatus(401)));
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
        System.clearProperty(GitHubOAuthConfig.BASE_URL);
        System.clearProperty(GitHubOAuthConfig.API_URL);
        System.clearProperty(GitHubOAuthConfig.CLIENT_ID);
        System.clearProperty(GitHubOAuthConfig.CLIENT_SECRET);
    }

    // ------------------------------------------------------------------------------------------- helpers

    private void stubUser(String token, String login) {
        gitHub.stubFor(get(urlEqualTo("/user")).withHeader("Authorization", equalTo("Bearer " + token))
                .willReturn(okJson("{\"login\":\"" + login + "\"}")));
    }

    private void stubRepository(String owner, String repo, String spellingOwner, String spellingRepo) {
        gitHub.stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo)).willReturn(okJson(
                "{\"name\":\"" + spellingRepo + "\",\"html_url\":\"https://github.com/" + spellingOwner + "/" + spellingRepo
                        + "\",\"private\":false,\"owner\":{\"login\":\"" + spellingOwner + "\"}}")));
    }

    private void stubGitHubUser(String login, String spelling) {
        gitHub.stubFor(get(urlEqualTo("/users/" + login)).willReturn(okJson("{\"login\":\"" + spelling + "\",\"id\":1}")));
    }

    private APIGatewayV2HTTPResponse call(String method, String path, String body, String session) {
        return call(method, path, null, body, session);
    }

    private APIGatewayV2HTTPResponse call(String method, String path, Map<String, String> query, String body, String session) {
        return api.handleRequest(Events.event(method, path, query, body, session), null);
    }

    private static JsonObject json(APIGatewayV2HTTPResponse response) {
        return JsonParser.parseString(response.getBody()).getAsJsonObject();
    }

    private static String message(APIGatewayV2HTTPResponse response) {
        return json(response).get("message").getAsString();
    }

    /** Signs in with a personal access token and returns the session id of the cookie. */
    private String signIn(String token) {
        APIGatewayV2HTTPResponse response = call("POST", "/api/auth/token", "{\"token\":\"" + token + "\"}", null);
        assertEquals(response.getBody(), 200, response.getStatusCode());
        return cookieValue(response.getCookies(), AuthRoutes.SESSION_COOKIE);
    }

    private static String cookieValue(List<String> cookies, String name) {
        for (String c : cookies) if (c.startsWith(name + "=")) return c.substring(name.length() + 1, c.indexOf(';'));
        return null;
    }

    private long createGroup(String admin, String name, String owner, String repo) {
        stubRepository(owner, repo, owner, repo);
        APIGatewayV2HTTPResponse created = call("POST", "/api/groups",
                "{\"name\":\"" + name + "\",\"repositoryUrl\":\"https://github.com/" + owner + "/" + repo + "\"}", admin);
        assertEquals(created.getBody(), 200, created.getStatusCode());
        return json(created).get("id").getAsLong();
    }

    // ------------------------------------------------------------------------------------------- session

    @Test
    public void tokenSignInMeAndSignOut() {
        APIGatewayV2HTTPResponse rejected = call("POST", "/api/auth/token", "{\"token\":\"bad\"}", null);
        assertEquals(401, rejected.getStatusCode());
        assertEquals("GitHub rejected this personal access token.", message(rejected));
        assertEquals("Please enter a personal access token.", message(call("POST", "/api/auth/token", "{\"token\":\" \"}", null)));

        APIGatewayV2HTTPResponse ok = call("POST", "/api/auth/token", "{\"token\":\"good-admin\"}", null);
        String cookie = ok.getCookies().get(0);
        assertTrue(cookie, cookie.startsWith(AuthRoutes.SESSION_COOKIE + "="));
        assertTrue(cookie, cookie.contains("HttpOnly") && cookie.contains("Secure") && cookie.contains("SameSite=Lax")
                && cookie.contains("Max-Age=28800"));
        assertFalse("the token never goes to the browser", ok.getBody().contains("good-admin"));
        String session = cookieValue(ok.getCookies(), AuthRoutes.SESSION_COOKIE);

        assertEquals("{\"login\":\"professor\",\"isAdmin\":true}", call("GET", "/api/me", null, session).getBody());
        assertEquals("{\"login\":\"ana\",\"isAdmin\":false}", call("GET", "/api/me", null, signIn("good-student")).getBody());

        assertEquals(200, call("POST", "/api/auth/logout", null, session).getStatusCode());
        assertEquals(401, call("GET", "/api/me", null, session).getStatusCode());
        assertEquals(401, call("GET", "/api/groups", null, session).getStatusCode());
    }

    @Test
    public void gitHubSignInChecksTheStateOnce() {
        APIGatewayV2HTTPResponse start = call("GET", "/api/auth/github", null, null);
        assertEquals(302, start.getStatusCode());
        String location = start.getHeaders().get("Location");
        assertTrue(location, location.startsWith(gitHub.baseUrl() + "/login/oauth/authorize?client_id=client-id&state="));
        String state = location.substring(location.indexOf("state=") + 6);
        assertEquals(state, cookieValue(start.getCookies(), AuthRoutes.OAUTH_COOKIE));

        gitHub.stubFor(post(urlEqualTo("/login/oauth/access_token")).willReturn(okJson("{\"access_token\":\"good-admin\"}")));

        // different cookie: not verified (and the state is consumed)
        var forged = Events.event("GET", "/api/auth/callback", Map.of("code", "c", "state", state), null, null);
        forged.setCookies(List.of(AuthRoutes.OAUTH_COOKIE + "=other"));
        assertEquals("/login?error=github_not_verified", api.handleRequest(forged, null).getHeaders().get("Location"));

        // new state, right cookie: signed in; the same callback again is refused
        start = call("GET", "/api/auth/github", null, null);
        state = cookieValue(start.getCookies(), AuthRoutes.OAUTH_COOKIE);
        var callback = Events.event("GET", "/api/auth/callback", Map.of("code", "c", "state", state), null, null);
        callback.setCookies(List.of(AuthRoutes.OAUTH_COOKIE + "=" + state));
        APIGatewayV2HTTPResponse signedIn = api.handleRequest(callback, null);
        assertEquals("/home", signedIn.getHeaders().get("Location"));
        String session = cookieValue(signedIn.getCookies(), AuthRoutes.SESSION_COOKIE);
        assertEquals("{\"login\":\"professor\",\"isAdmin\":true}", call("GET", "/api/me", null, session).getBody());
        assertEquals("/login?error=github_not_verified", api.handleRequest(callback, null).getHeaders().get("Location"));

        // no code (access denied on GitHub)
        assertEquals("/login?error=github_failed",
                call("GET", "/api/auth/callback", Map.of("error", "access_denied"), null, null).getHeaders().get("Location"));
    }

    // ------------------------------------------------------------------------------------------- groups

    @Test
    public void groupsCreateEditArchiveRestoreWithTheRulesOfTheJettyVersion() {
        String admin = signIn("good-admin");
        String student = signIn("good-student");

        assertEquals(403, call("POST", "/api/groups", "{}", student).getStatusCode());
        assertEquals("Only administrators can manage groups.", message(call("POST", "/api/groups", "{}", student)));

        // spelling of GitHub
        stubRepository("octocat", "hello-world", "octocat", "Hello-World");
        APIGatewayV2HTTPResponse created = call("POST", "/api/groups",
                "{\"name\":\"e2e\",\"repositoryUrl\":\"https://github.com/octocat/hello-world.git\"}", admin);
        assertEquals(created.getBody(), 200, created.getStatusCode());
        JsonObject group = json(created);
        long id = group.get("id").getAsLong();
        assertEquals("https://github.com/octocat/Hello-World", group.get("repositoryUrl").getAsString());
        assertEquals("Hello-World", group.get("repository").getAsString());

        // errors
        assertEquals("The group name is required.", message(call("POST", "/api/groups", "{\"repositoryUrl\":\"x\"}", admin)));
        assertEquals("Enter the URL of a GitHub repository (https://github.com/owner/repository).",
                message(call("POST", "/api/groups", "{\"name\":\"x\",\"repositoryUrl\":\"github.com/x\"}", admin)));
        APIGatewayV2HTTPResponse duplicate = call("POST", "/api/groups",
                "{\"name\":\"x\",\"repositoryUrl\":\"https://github.com/octocat/Hello-World/\"}", admin);
        assertEquals(409, duplicate.getStatusCode());
        assertEquals("This repository is already registered.", message(duplicate));
        gitHub.stubFor(get(urlEqualTo("/repos/octocat/missing")).willReturn(aResponse().withStatus(404)));
        assertEquals("Repository not found or not accessible with this token.",
                message(call("POST", "/api/groups", "{\"name\":\"x\",\"repositoryUrl\":\"https://github.com/octocat/missing\"}", admin)));

        // edit: same id, new repository reserved, old one released
        stubRepository("octocat", "spoon-knife", "octocat", "Spoon-Knife");
        APIGatewayV2HTTPResponse edited = call("PUT", "/api/groups/" + id,
                "{\"name\":\"renamed\",\"repositoryUrl\":\"https://github.com/octocat/spoon-knife\"}", admin);
        assertEquals(edited.getBody(), 200, edited.getStatusCode());
        assertEquals("renamed", json(edited).get("name").getAsString());
        long other = createGroup(admin, "again", "octocat", "Hello-World");
        assertTrue(other > id);

        // archive: out of the list, 404 in the detail, repository still reserved
        assertEquals(200, call("POST", "/api/groups/" + id + "/archive", null, admin).getStatusCode());
        assertEquals(404, call("POST", "/api/groups/" + id + "/archive", null, admin).getStatusCode());
        JsonArray active = JsonParser.parseString(call("GET", "/api/groups", null, student).getBody()).getAsJsonArray();
        assertEquals(1, active.size());
        assertEquals(404, call("GET", "/api/groups/" + id, null, student).getStatusCode());
        JsonObject archived = json(call("GET", "/api/groups/archived", null, admin)).getAsJsonArray("groups").get(0).getAsJsonObject();
        assertEquals(id, archived.get("id").getAsLong());
        assertTrue(archived.get("archivedOn").getAsString().matches("[A-Z][a-z]{2} \\d{1,2}, \\d{4}"));
        APIGatewayV2HTTPResponse inArchived = call("POST", "/api/groups",
                "{\"name\":\"x\",\"repositoryUrl\":\"https://github.com/octocat/Spoon-Knife\"}", admin);
        assertEquals("This repository is already registered in an archived group. Restore it instead.", message(inArchived));

        assertEquals(200, call("POST", "/api/groups/" + id + "/restore", null, admin).getStatusCode());
        assertEquals(2, JsonParser.parseString(call("GET", "/api/groups", null, student).getBody()).getAsJsonArray().size());
    }

    // ------------------------------------------------------------------------------------------- members and aliases

    @Test
    public void membersAndAliasesKeepTheCountAndFollowTheLogin() {
        String admin = signIn("good-admin");
        long id = createGroup(admin, "g", "octocat", "Hello-World");
        String base = "/api/groups/" + id;

        stubGitHubUser("ana-souza", "Ana-Souza");
        APIGatewayV2HTTPResponse added = call("POST", base + "/members", "{\"login\":\"ana-souza\",\"manual\":false}", admin);
        assertEquals("{\"groupId\":" + id + ",\"login\":\"Ana-Souza\",\"name\":\"Ana-Souza\"}", added.getBody());
        assertEquals("This user is already a member of the group.",
                message(call("POST", base + "/members", "{\"login\":\"ana-souza\"}", admin)));
        gitHub.stubFor(get(urlEqualTo("/users/ghost")).willReturn(aResponse().withStatus(404)));
        assertEquals("This GitHub user does not exist.", message(call("POST", base + "/members", "{\"login\":\"ghost\"}", admin)));
        assertEquals("A valid GitHub login is required.", message(call("POST", base + "/members", "{\"login\":\"-x\"}", admin)));

        JsonObject members = json(call("GET", base + "/members", null, admin));
        assertEquals("g", members.getAsJsonObject("group").get("name").getAsString());
        assertEquals(1, members.getAsJsonArray("members").size());
        assertEquals(1, JsonParser.parseString(call("GET", "/api/groups", null, admin).getBody()).getAsJsonArray()
                .get(0).getAsJsonObject().get("members").getAsInt());

        // alias: only students, valid key
        assertEquals("Invalid identity key.", message(call("POST", base + "/aliases", "{\"identityKey\":\"x\",\"login\":\"Ana-Souza\"}", admin)));
        assertEquals("The login must be a student of this group.",
                message(call("POST", base + "/aliases", "{\"identityKey\":\"email:a@local\",\"login\":\"bob\"}", admin)));
        assertEquals(200, call("POST", base + "/aliases", "{\"identityKey\":\"email:a@local\",\"login\":\"ana-souza\"}", admin).getStatusCode());

        // rename: the alias follows the new login
        stubGitHubUser("anab", "AnaB");
        APIGatewayV2HTTPResponse renamed = call("PUT", base + "/members/Ana-Souza", "{\"login\":\"anab\",\"name\":\"Ana B\"}", admin);
        assertEquals(renamed.getBody(), "AnaB", json(renamed).get("login").getAsString());
        assertEquals("This user is not a member of the group.",
                message(call("PUT", base + "/members/Ana-Souza", "{\"name\":\"x\"}", admin)));

        // remove: refused while the alias exists
        APIGatewayV2HTTPResponse refused = call("DELETE", base + "/members/AnaB", null, admin);
        assertEquals(409, refused.getStatusCode());
        assertEquals("Unlink the identities linked to this student before removing them.", message(refused));
        assertEquals(200, call("DELETE", base + "/aliases", Map.of("identityKey", "email:a@local"), null, admin).getStatusCode());
        assertEquals("This identity is not linked.", message(call("DELETE", base + "/aliases", Map.of("identityKey", "email:a@local"), null, admin)));
        assertEquals(200, call("DELETE", base + "/members/anab", null, admin).getStatusCode());
        assertEquals(0, JsonParser.parseString(call("GET", "/api/groups", null, admin).getBody()).getAsJsonArray()
                .get(0).getAsJsonObject().get("members").getAsInt());
    }

    // ------------------------------------------------------------------------------------------- reports and grades

    @Test
    public void detailContributionsCommitsAndGrades() {
        String admin = signIn("good-admin");
        long id = createGroup(admin, "g", "octocat", "Hello-World");
        stubGitHubUser("ana", "ana");
        stubGitHubUser("bruno", "bruno");
        call("POST", "/api/groups/" + id + "/members", "{\"login\":\"ana\",\"manual\":true,\"name\":\"Ana\"}", admin);
        call("POST", "/api/groups/" + id + "/members", "{\"login\":\"bruno\",\"manual\":true,\"name\":\"Bruno\"}", admin);

        gitHub.stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/contributors")).willReturn(okJson(
                "[{\"login\":\"ana\",\"id\":1,\"avatar_url\":\"https://avatars/ana\",\"html_url\":\"https://github.com/ana\",\"contributions\":3}]")));
        gitHub.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson("{\"data\":{\"repository\":{\"defaultBranchRef\":{\"target\":"
                + "{\"history\":{\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null},\"nodes\":["
                // GitHub order: newest first
                + node("n1", "2026-10-06T12:00:00Z", 10, 0, "Nobody", "nobody@local", null) + ","
                + node("b1", "2026-10-05T12:00:00Z", 40, 0, "Bruno", "bruno@x.com", "bruno") + ","
                + node("a2", "2026-10-02T12:00:00Z", 30, 5, "Ana", "ana@x.com", "ana") + ","
                + node("a1", "2026-09-20T12:00:00Z", 120, 10, "Ana", "ana@x.com", "ana")
                + "]}}}}}}")));

        // detail: cards (contributor ana + member bruno without commits), students for aliases
        JsonObject detail = json(call("GET", "/api/groups/" + id, null, admin));
        assertEquals(1, detail.get("contributorsCount").getAsInt());
        assertEquals(2, detail.getAsJsonArray("cards").size());
        assertEquals("bruno", detail.getAsJsonArray("cards").get(1).getAsJsonObject().get("login").getAsString());
        assertEquals(0, detail.getAsJsonArray("cards").get(1).getAsJsonObject().get("contributions").getAsInt());
        assertEquals(2, detail.getAsJsonArray("aliasTargets").size());

        // milestone 2: grades from 0 to 3, the commit without account is not counted
        JsonObject m2 = json(call("GET", "/api/groups/" + id + "/contributions", Map.of("period", "m2"), null, admin));
        assertEquals("m2", m2.get("period").getAsString());
        assertEquals(4, m2.get("totalCommits").getAsInt());
        assertEquals(2, m2.getAsJsonObject("grading").get("students").getAsInt());
        assertEquals(1, m2.getAsJsonObject("grading").get("excludedCommits").getAsInt());
        assertFalse(m2.getAsJsonObject("grading").get("provisional").getAsBoolean());
        JsonObject anaRow = m2.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertEquals("ana", anaRow.get("author").getAsString());
        assertTrue(anaRow.has("gradeDetails"));

        // All: M1, M2, M3 and total; the same numbers in /api/grades/{id}
        JsonObject all = json(call("GET", "/api/groups/" + id + "/contributions", Map.of("period", "all"), null, admin));
        assertEquals(List.of("m3").toString(), all.getAsJsonObject("grading").getAsJsonArray("provisionalMilestones").toString().replace("\"", ""));
        JsonObject grades = json(call("GET", "/api/grades/" + id, null, admin));
        for (var row : all.getAsJsonArray("rows")) {
            JsonObject r = row.getAsJsonObject();
            if (r.get("total").isJsonNull()) continue;
            for (var s : grades.getAsJsonArray("students")) {
                JsonObject g = s.getAsJsonObject();
                if (g.get("login").getAsString().equals(r.get("author").getAsString())) {
                    assertEquals(r.get("total").getAsDouble(), g.get("total").getAsDouble(), 0);
                }
            }
        }
        assertEquals(1, json(call("GET", "/api/grades", null, admin)).getAsJsonArray("groups").size());

        // invalid period
        APIGatewayV2HTTPResponse invalid = call("GET", "/api/groups/" + id + "/contributions", Map.of("period", "m9"), null, admin);
        assertEquals(400, invalid.getStatusCode());
        assertEquals("invalid_period", json(invalid).get("error").getAsString());

        // commit history of ana: 2 commits, newest first, chart
        JsonObject history = json(call("GET", "/api/groups/" + id + "/commits", Map.of("author", "ana", "period", "m9"), null, admin));
        assertTrue(history.get("periodWarning").getAsString().endsWith("Showing all history instead."));
        assertEquals(2, history.getAsJsonArray("commits").size());
        assertEquals("a2", history.getAsJsonArray("commits").get(0).getAsJsonObject().get("sha").getAsString());
        assertFalse(history.get("activity").isJsonNull());
        assertTrue(history.getAsJsonObject("activity").has("ariaLabel"));

        // a student sees the report but not the class grades
        String student = signIn("good-student");
        assertEquals(200, call("GET", "/api/groups/" + id + "/contributions", Map.of("period", "m2"), null, student).getStatusCode());
        assertEquals("Only administrators can see class grades.", message(call("GET", "/api/grades", null, student)));
        assertEquals(0, json(call("GET", "/api/groups/" + id, null, student)).getAsJsonArray("aliasTargets").size());
    }

    private static String node(String oid, String date, int additions, int deletions, String name, String email, String login) {
        return "{\"oid\":\"" + oid + "\",\"message\":\"work " + oid + "\",\"url\":\"https://github.com/octocat/Hello-World/commit/" + oid
                + "\",\"committedDate\":\"" + date + "\",\"additions\":" + additions + ",\"deletions\":" + deletions
                + ",\"changedFilesIfAvailable\":2,\"parents\":{\"totalCount\":1},\"author\":{\"name\":\"" + name + "\",\"email\":\""
                + email + "\",\"user\":" + (login == null ? "null" : "{\"login\":\"" + login + "\"}") + "}}";
    }

    @Test
    public void contributorsErrorStillShowsTheMembers() {
        String admin = signIn("good-admin");
        long id = createGroup(admin, "g", "octocat", "Hello-World");
        stubGitHubUser("ana", "ana");
        call("POST", "/api/groups/" + id + "/members", "{\"login\":\"ana\"}", admin);
        gitHub.stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/contributors")).willReturn(aResponse().withStatus(500)));

        JsonObject detail = json(call("GET", "/api/groups/" + id, null, admin));
        assertFalse(detail.get("contributorsError").isJsonNull());
        assertTrue(detail.get("contributorsCount").isJsonNull());
        assertTrue(detail.getAsJsonArray("cards").get(0).getAsJsonObject().get("contributions").isJsonNull());
    }

    @Test
    public void unknownGroupIs404AndBadIdIs400() {
        String admin = signIn("good-admin");
        assertEquals("Group not found.", message(call("GET", "/api/groups/999", null, admin)));
        assertEquals("The 'id' parameter is required.", message(call("GET", "/api/groups/abc/members", null, admin)));
        Map<String, String> none = new HashMap<>();
        assertEquals(404, call("GET", "/api/grades/999", none, null, admin).getStatusCode());
    }
}
