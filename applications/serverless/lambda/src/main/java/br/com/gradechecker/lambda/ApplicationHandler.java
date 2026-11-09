package br.com.gradechecker.lambda;

import br.com.gradechecker.adapter.inbound.http.Request;
import br.com.gradechecker.adapter.inbound.http.Router;
import br.com.gradechecker.adapter.inbound.http.routes.AuthRoutes;
import br.com.gradechecker.adapter.inbound.http.routes.GradebookRoutes;
import br.com.gradechecker.adapter.inbound.http.routes.GroupRoutes;
import br.com.gradechecker.adapter.inbound.http.routes.ReportRoutes;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

/**
 * Entry point of the Lambda (template.yaml: Handler ApplicationHandler::handleRequest, capability api-routing):
 * every /api/* request of the HTTP API (payload 2.0) goes through the router to the inbound HTTP adapter.
 * The context and the router are created once per instance, in the static init (they enter the SnapStart snapshot).
 */
public class ApplicationHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static volatile Router router;

    private final Router instanceRouter;

    /** Lambda: the production context, from the environment, shared by every invocation of the instance. */
    public ApplicationHandler() {
        this.instanceRouter = null;
    }

    /** Tests: a router built over a given context. */
    public ApplicationHandler(AppContext ctx) {
        this.instanceRouter = routes(ctx);
    }

    /** Every route of the API. */
    public static Router routes(AppContext ctx) {
        Router r = new Router(request -> AuthRoutes.sessionOf(ctx.sessions, request));
        new AuthRoutes(ctx.sessions, ctx.gitHubOAuth).register(r);
        new GroupRoutes(ctx.groups, ctx.membership, ctx.identityLinking, ctx.periods.getZone()).register(r);
        new ReportRoutes(ctx.contributions, ctx.commitHistory, ctx.periods, ctx.grading).register(r);
        new GradebookRoutes(ctx.classGrades).register(r);
        return r;
    }

    private static Router productionRouter() {
        Router r = router;
        if (r == null) {
            synchronized (ApplicationHandler.class) {
                if (router == null) router = routes(AppContext.fromEnvironment());
                r = router;
            }
        }
        return r;
    }

    static {
        // SnapStart: build the context during the init phase, so it is part of the snapshot
        if (System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null) productionRouter();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        Router r = instanceRouter != null ? instanceRouter : productionRouter();
        return r.handle(Request.of(event)).toEvent();
    }
}
