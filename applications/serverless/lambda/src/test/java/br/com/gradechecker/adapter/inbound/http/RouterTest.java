package br.com.gradechecker.adapter.inbound.http;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.session.Session;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static br.com.gradechecker.adapter.inbound.http.Router.Access.PUBLIC;
import static br.com.gradechecker.adapter.inbound.http.Router.Access.USER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Capability api-routing: routes, path parameters, errors and protections, with HTTP API events (no AWS). */
public class RouterTest {

    private final AtomicReference<Session> session = new AtomicReference<>();
    private Router router;

    @Before
    public void setUp() {
        System.setProperty(AdminConfig.ADMINS, "professor");
        router = new Router(request -> session.get())
                .get("/api/public", PUBLIC, call -> Response.ok(Map.of("ok", true)))
                .get("/api/groups/archived", USER, call -> Response.ok(Map.of("archived", true)))
                .get("/api/groups/{id}", USER, call -> Response.ok(Map.of("id", call.request().longParam("id", "bad id"))))
                .put("/api/groups/{id}/members/{login}", USER, call -> Response.ok(Map.of("login", call.request().pathParam("login"),
                        "name", String.valueOf(call.request().string("name")))))
                .post("/api/admin", Router.Access.admin("FORBIDDEN", "Only administrators can manage groups."), call -> Response.ok(Map.of("done", true)))
                .get("/api/github", USER, call -> { throw new GitHubApiException(429, "rate_limited", "GitHub rate limit."); })
                .get("/api/boom", USER, call -> { throw new IllegalStateException("token=secret"); });
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    private Response call(String method, String path, Map<String, String> headers, String body) {
        return router.handle(Events.request(method, path, headers, body));
    }

    private Response call(String method, String path) {
        return call(method, path, Map.of("X-Requested-With", "XMLHttpRequest"), null);
    }

    private void signIn(String login) {
        session.set(new Session("id", login, "token"));
    }

    @Test
    public void unknownRouteIs404AndWrongMethodIs405() {
        Response missing = call("GET", "/api/anything");
        assertEquals(404, missing.status());
        assertEquals("{\"error\":\"not_found\",\"message\":\"Not found.\"}", missing.body());
        assertEquals(Response.JSON, missing.headers().get("Content-Type"));

        assertEquals(405, call("DELETE", "/api/public").status());
    }

    @Test
    public void staticPathWinsOverParameterAndParametersAreDecoded() {
        signIn("ana");
        assertEquals("{\"archived\":true}", call("GET", "/api/groups/archived").body());
        assertEquals("{\"id\":3}", call("GET", "/api/groups/3").body());
        assertEquals(400, call("GET", "/api/groups/abc").status());

        Response put = call("PUT", "/api/groups/3/members/Ana%2DSouza", Map.of("X-Requested-With", "XMLHttpRequest"), "{\"name\":\" Ana \"}");
        assertTrue(put.body(), put.body().contains("\"login\":\"Ana-Souza\""));
        assertTrue(put.body(), put.body().contains("\"name\":\"Ana\""));
    }

    @Test
    public void withoutSessionIs401() {
        Response response = call("GET", "/api/groups/3");
        assertEquals(401, response.status());
        assertEquals("{\"error\":\"unauthorized\",\"message\":\"Sign in to continue.\"}", response.body());
        assertEquals(200, call("GET", "/api/public").status());
    }

    @Test
    public void studentGets403OnAdministratorRoutes() {
        signIn("ana");
        Response response = call("POST", "/api/admin");
        assertEquals(403, response.status());
        assertEquals("{\"error\":\"FORBIDDEN\",\"message\":\"Only administrators can manage groups.\"}", response.body());

        signIn("Professor");
        assertEquals(200, call("POST", "/api/admin").status());
    }

    @Test
    public void changesWithoutXRequestedWithAre403EvenForAdministrators() {
        signIn("professor");
        Response response = call("POST", "/api/admin", Map.of(), null);
        assertEquals(403, response.status());
        assertEquals("{\"error\":\"forbidden\",\"message\":\"Missing X-Requested-With header.\"}", response.body());
    }

    @Test
    public void errorsBecomeJson() {
        signIn("ana");
        Response github = call("GET", "/api/github");
        assertEquals(429, github.status());
        assertEquals("{\"error\":\"rate_limited\",\"message\":\"GitHub rate limit.\"}", github.body());

        Response boom = call("GET", "/api/boom");
        assertEquals(500, boom.status());
        assertEquals("{\"error\":\"error\",\"message\":\"Unexpected error.\"}", boom.body());

        Response badJson = call("PUT", "/api/groups/3/members/ana", Map.of("X-Requested-With", "XMLHttpRequest"), "{not json");
        assertEquals(400, badJson.status());
        assertEquals("{\"error\":\"bad_request\",\"message\":\"Invalid JSON body.\"}", badJson.body());
    }

    @Test
    public void redirectsAndCookiesGoToTheEvent() {
        var event = Response.redirect("/home").cookie("a=1").toEvent();
        assertEquals(302, event.getStatusCode());
        assertEquals("/home", event.getHeaders().get("Location"));
        assertEquals(java.util.List.of("a=1"), event.getCookies());
        assertNull(Response.ok(Map.of()).toEvent().getCookies());
    }
}
