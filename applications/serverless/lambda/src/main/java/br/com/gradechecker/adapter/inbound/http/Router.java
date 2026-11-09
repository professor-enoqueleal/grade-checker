package br.com.gradechecker.adapter.inbound.http;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.session.Session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Router of the Lambda (capability api-routing): matches method and path ("/api/groups/{id}/members/{login}"),
 * then applies, in this order: X-Requested-With on changes, session (401), administrator (403), the route.
 * Errors become { "error", "message" }.
 */
public final class Router {

    /** A route: receives the call and answers. */
    @FunctionalInterface
    public interface Handler {
        Response handle(Call call);
    }

    /** Who may call a route. */
    public static final class Access {
        final boolean session;
        final String adminCode;
        final String adminMessage;

        private Access(boolean session, String adminCode, String adminMessage) {
            this.session = session;
            this.adminCode = adminCode;
            this.adminMessage = adminMessage;
        }

        /** No session needed (sign-in routes). */
        public static final Access PUBLIC = new Access(false, null, null);
        /** Any signed-in user. */
        public static final Access USER = new Access(true, null, null);

        /** Administrators only: other users get 403 with this code and message. */
        public static Access admin(String code, String message) {
            return new Access(true, code, message);
        }
    }

    public static final String UNAUTHORIZED = "Sign in to continue.";
    static final String MISSING_HEADER = "Missing X-Requested-With header.";

    private static final Logger logger = LogManager.getLogger(Router.class);

    private final List<Route> routes = new ArrayList<>();
    private final Function<Request, Session> sessions;

    /** @param sessions session of a request (cookie), or null */
    public Router(Function<Request, Session> sessions) {
        this.sessions = sessions;
    }

    public Router get(String path, Access access, Handler handler) { return add("GET", path, access, handler); }
    public Router post(String path, Access access, Handler handler) { return add("POST", path, access, handler); }
    public Router put(String path, Access access, Handler handler) { return add("PUT", path, access, handler); }
    public Router delete(String path, Access access, Handler handler) { return add("DELETE", path, access, handler); }

    private Router add(String method, String path, Access access, Handler handler) {
        routes.add(new Route(method, path, access, handler));
        return this;
    }

    public Response handle(Request request) {
        try {
            return dispatch(request);
        } catch (ApiException e) {
            return Response.error(e.getStatus(), e.getCode(), e.getMessage());
        } catch (DomainException e) {
            return Response.error(status(e.getKind()), e.getCode(), e.getMessage());
        } catch (GitHubApiException e) {
            logger.warn("GitHub error on {} {}: {} ({})", request.method(), request.path(), e.getErrorCode(), e.getMessage());
            return Response.error(e.getHttpStatus(), e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Unexpected error on {} {}", request.method(), request.path(), e);
            return Response.error(500, "error", "Unexpected error.");
        }
    }

    /** HTTP status of a refusal of the domain. */
    static int status(DomainException.Kind kind) {
        switch (kind) {
            case NOT_FOUND: return 404;
            case CONFLICT: return 409;
            default: return 400;
        }
    }

    private Response dispatch(Request request) {
        boolean pathMatched = false;
        for (Route route : routes) {
            Map<String, String> params = route.match(request.path());
            if (params == null) continue;
            pathMatched = true;
            if (!route.method.equals(request.method())) continue;
            request.setPathParams(params);
            return run(route, request);
        }
        if (pathMatched) return Response.error(405, "method_not_allowed", "Method not allowed.");
        return Response.error(404, "not_found", "Not found.");
    }

    private Response run(Route route, Request request) {
        // a cross-site form cannot set this header (CSRF protection of every change, public ones included)
        if (!"GET".equals(request.method()) && !"XMLHttpRequest".equalsIgnoreCase(request.header("X-Requested-With"))) {
            return Response.error(403, "forbidden", MISSING_HEADER);
        }
        Session session = sessions.apply(request);
        if (route.access.session && session == null) {
            return Response.error(401, "unauthorized", UNAUTHORIZED);
        }
        if (route.access.adminCode != null && !AdminConfig.isAdmin(session.getLogin())) {
            logger.warn("{} {} refused for {}", request.method(), request.path(), session.getLogin());
            return Response.error(403, route.access.adminCode, route.access.adminMessage);
        }
        return route.handler.handle(new Call(request, session));
    }

    private static final class Route {
        final String method;
        final Pattern pattern;
        final List<String> names = new ArrayList<>();
        final Access access;
        final Handler handler;

        Route(String method, String path, Access access, Handler handler) {
            this.method = method;
            this.access = access;
            this.handler = handler;
            Matcher m = Pattern.compile("\\{(\\w+)}").matcher(path);
            StringBuilder regex = new StringBuilder("^");
            int last = 0;
            while (m.find()) {
                regex.append(Pattern.quote(path.substring(last, m.start()))).append("([^/]+)");
                names.add(m.group(1));
                last = m.end();
            }
            regex.append(Pattern.quote(path.substring(last))).append("/?$");
            this.pattern = Pattern.compile(regex.toString());
        }

        Map<String, String> match(String path) {
            Matcher m = pattern.matcher(path);
            if (!m.matches()) return null;
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < names.size(); i++) params.put(names.get(i), m.group(i + 1));
            return params;
        }
    }
}
