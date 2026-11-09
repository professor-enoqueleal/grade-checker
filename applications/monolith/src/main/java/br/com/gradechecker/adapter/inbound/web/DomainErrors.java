package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.core.error.DomainException;

import jakarta.servlet.http.HttpServletResponse;

/** HTTP status of a refusal of the domain (use cases of grade-checker-core), the same as in the serverless version. */
final class DomainErrors {

    private DomainErrors() {
    }

    static int status(DomainException e) {
        switch (e.getKind()) {
            case NOT_FOUND: return HttpServletResponse.SC_NOT_FOUND;
            case CONFLICT: return HttpServletResponse.SC_CONFLICT;
            default: return HttpServletResponse.SC_BAD_REQUEST;
        }
    }
}
