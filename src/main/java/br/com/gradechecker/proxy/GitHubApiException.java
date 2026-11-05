package br.com.gradechecker.proxy;

/**
 * Failure while talking to the GitHub API, already classified into the HTTP status
 * and error code that should be returned to our own clients.
 */
public class GitHubApiException extends RuntimeException {

    public static final String UNAUTHORIZED = "github_unauthorized";
    public static final String RATE_LIMITED = "github_rate_limited";
    public static final String FORBIDDEN = "github_forbidden";
    public static final String NOT_FOUND = "github_not_found";
    public static final String ERROR = "github_error";

    private final int httpStatus;
    private final String errorCode;

    public GitHubApiException(int httpStatus, String errorCode, String message) {
        this(httpStatus, errorCode, message, null);
    }

    public GitHubApiException(int httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
