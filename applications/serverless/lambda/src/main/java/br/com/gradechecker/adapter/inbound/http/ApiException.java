package br.com.gradechecker.adapter.inbound.http;

/** An error answered as { "error": code, "message": message } with the HTTP status (capability api-routing). */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException badRequest(String message) { return new ApiException(400, "bad_request", message); }
    public static ApiException notFound(String message) { return new ApiException(404, "not_found", message); }
    public static ApiException conflict(String code, String message) { return new ApiException(409, code, message); }
}
