package br.com.gradechecker.adapter.inbound.http;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One HTTP API request (payload 2.0), with the path parameters of the matched route. */
public final class Request {

    private final String method;
    private final String path;
    private final Map<String, String> headers;     // lower-case names
    private final Map<String, String> query;
    private final Map<String, String> cookies;
    private final String body;
    private Map<String, String> pathParams = Collections.emptyMap();
    private JsonObject json;

    public Request(String method, String path, Map<String, String> headers, Map<String, String> query,
                   Map<String, String> cookies, String body) {
        this.method = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        this.path = path == null || path.isEmpty() ? "/" : path;
        this.headers = headers;
        this.query = query;
        this.cookies = cookies;
        this.body = body;
    }

    public static Request of(APIGatewayV2HTTPEvent event) {
        String method = event.getRequestContext() != null && event.getRequestContext().getHttp() != null
                ? event.getRequestContext().getHttp().getMethod() : null;
        Map<String, String> headers = new HashMap<>();
        if (event.getHeaders() != null) event.getHeaders().forEach((k, v) -> headers.put(k.toLowerCase(Locale.ROOT), v));
        Map<String, String> query = new HashMap<>();
        if (event.getQueryStringParameters() != null) query.putAll(event.getQueryStringParameters());
        Map<String, String> cookies = new HashMap<>();
        List<String> list = event.getCookies();
        if (list != null) list.forEach(c -> addCookie(cookies, c));
        String cookieHeader = headers.get("cookie");
        if (cookieHeader != null) for (String c : cookieHeader.split(";")) addCookie(cookies, c);
        String body = event.getBody();
        if (body != null && event.getIsBase64Encoded()) body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        return new Request(method, event.getRawPath(), headers, query, cookies, body);
    }

    private static void addCookie(Map<String, String> cookies, String pair) {
        int eq = pair.indexOf('=');
        if (eq <= 0) return;
        cookies.putIfAbsent(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
    }

    public String method() { return method; }
    public String path() { return path; }
    public String header(String name) { return headers.get(name.toLowerCase(Locale.ROOT)); }
    public String cookie(String name) { return cookies.get(name); }

    /** Query parameter, trimmed; null when missing or blank. */
    public String query(String name) {
        return blankToNull(query.get(name));
    }

    /** Path parameter of the route ({id}, {login}), URL-decoded. */
    public String pathParam(String name) {
        String value = pathParams.get(name);
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    void setPathParams(Map<String, String> pathParams) {
        this.pathParams = pathParams;
    }

    /** {id} as a positive number, or 400 with the given message. */
    public long longParam(String name, String message) {
        try {
            long value = Long.parseLong(pathParam(name).trim());
            if (value > 0) return value;
        } catch (RuntimeException e) {
            // falls through
        }
        throw ApiException.badRequest(message);
    }

    /** JSON body as an object ({} when empty); 400 when it is not a JSON object. */
    public JsonObject json() {
        if (json != null) return json;
        if (body == null || body.trim().isEmpty()) return json = new JsonObject();
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) throw ApiException.badRequest("Invalid JSON body.");
            return json = element.getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw ApiException.badRequest("Invalid JSON body.");
        }
    }

    /** String field of the JSON body, trimmed; null when missing, blank or not a string. */
    public String string(String field) {
        JsonElement value = json().get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        return blankToNull(value.getAsString());
    }

    public boolean bool(String field) {
        JsonElement value = json().get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return false;
        return value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : "true".equalsIgnoreCase(value.getAsString());
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
