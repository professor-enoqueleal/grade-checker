package br.com.gradechecker.core.error;

/**
 * A rule of the domain refused the operation (capability core-errors): a kind that the adapters translate (400, 404,
 * 409 in HTTP), a stable code and the message shown to the user.
 */
public class DomainException extends RuntimeException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT }

    private final Kind kind;
    private final String code;

    public DomainException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind getKind() { return kind; }
    public String getCode() { return code; }

    public static DomainException invalid(String code, String message) { return new DomainException(Kind.INVALID, code, message); }
    public static DomainException notFound(String code, String message) { return new DomainException(Kind.NOT_FOUND, code, message); }
    public static DomainException conflict(String code, String message) { return new DomainException(Kind.CONFLICT, code, message); }
}
