package br.com.gradechecker.adapter.inbound.http;

import br.com.gradechecker.session.Session;

/** What a route receives: the request and the session (null on public routes without a session). */
public final class Call {

    private final Request request;
    private final Session session;

    public Call(Request request, Session session) {
        this.request = request;
        this.session = session;
    }

    public Request request() { return request; }
    public Session session() { return session; }

    /** Login of the session; routes behind USER or ADMIN always have one. */
    public String login() { return session == null ? null : session.getLogin(); }

    /** GitHub token of the session (never sent to the browser). */
    public String token() { return session == null ? null : session.getAccessToken(); }
}
