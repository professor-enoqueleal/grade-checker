package br.com.gradechecker.core.port;

import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.github.GitHubRepository;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;

import java.util.List;

/**
 * What the use cases read from GitHub (capability core-ports), implemented by the
 * {@code core.adapter.github.GitHubClient} of the library. Failures other than "not found" raise
 * {@code core.github.GitHubApiException}.
 */
public interface GitHubGateway {

    /** The repository with the spelling of GitHub; null when it does not exist or the token cannot see it. */
    GitHubRepository getRepository(String owner, String repo, String accessToken);

    /** The user with the spelling of GitHub; null when it does not exist. */
    Contributor getUser(String login, String accessToken);

    /** Contributors of the repository, as GitHub lists them (accounts with commits on the default branch). */
    List<Contributor> getContributors(String owner, String repo, String accessToken);

    /** Commits of the default branch within the period of the request, merges included. */
    RepositoryHistory getHistory(HistoryRequest request);
}
