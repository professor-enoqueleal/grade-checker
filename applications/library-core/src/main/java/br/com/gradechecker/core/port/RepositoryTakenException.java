package br.com.gradechecker.core.port;

/** The repository URL is already used by another group (raised by a {@link GroupRepository} implementation). */
public class RepositoryTakenException extends RuntimeException {

    public RepositoryTakenException() {
        super("repository already registered");
    }
}
