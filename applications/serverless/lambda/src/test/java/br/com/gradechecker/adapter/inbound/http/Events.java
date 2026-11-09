package br.com.gradechecker.adapter.inbound.http;

import br.com.gradechecker.adapter.inbound.http.routes.AuthRoutes;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HTTP API (payload 2.0) events for the tests. */
public final class Events {

    private Events() {
    }

    public static APIGatewayV2HTTPEvent event(String method, String path, Map<String, String> query, String body, String sessionCookie) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Content-Type", "application/json");
        return APIGatewayV2HTTPEvent.builder()
                .withRawPath(path)
                .withHeaders(headers)
                .withQueryStringParameters(query)
                .withCookies(sessionCookie == null ? null : List.of(AuthRoutes.SESSION_COOKIE + "=" + sessionCookie))
                .withBody(body)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(method).withPath(path).build())
                        .build())
                .build();
    }

    public static Request request(String method, String path, Map<String, String> headers, String body) {
        Map<String, String> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        return new Request(method, path, lower, new HashMap<>(), new HashMap<>(), body);
    }
}
