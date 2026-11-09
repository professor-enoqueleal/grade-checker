package br.com.gradechecker.adapter.inbound.http;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP response of a route: JSON (default) or a redirect, plus Set-Cookie values. */
public final class Response {

    public static final String JSON = "application/json; charset=UTF-8";

    private final int status;
    private final String body;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final List<String> cookies = new ArrayList<>();

    private Response(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public static Response json(int status, Object body) {
        Response r = new Response(status, Json.GSON.toJson(body));
        r.headers.put("Content-Type", JSON);
        r.headers.put("Cache-Control", "no-store");
        return r;
    }

    public static Response ok(Object body) {
        return json(200, body);
    }

    public static Response error(int status, String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return json(status, body);
    }

    public static Response redirect(String location) {
        Response r = new Response(302, "");
        r.headers.put("Location", location);
        r.headers.put("Cache-Control", "no-store");
        return r;
    }

    public Response cookie(String setCookie) {
        cookies.add(setCookie);
        return this;
    }

    public int status() { return status; }
    public String body() { return body; }
    public Map<String, String> headers() { return headers; }
    public List<String> cookies() { return cookies; }

    public APIGatewayV2HTTPResponse toEvent() {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(new LinkedHashMap<>(headers))
                .withCookies(cookies.isEmpty() ? null : new ArrayList<>(cookies))
                .withBody(body)
                .build();
    }
}
