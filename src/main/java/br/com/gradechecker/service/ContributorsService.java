package br.com.gradechecker.service;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributorStats;
import br.com.gradechecker.proxy.GitHubProxy;

import java.util.List;

public class ContributorsService {

    private final GitHubProxy gitHubProxy;

    public ContributorsService() {
        this.gitHubProxy = new GitHubProxy();
    }

    public List<ContributorStats> getCommitsDetails(CommitRequestBody commitRequestBody) {

        return this.gitHubProxy.getContributorStats(commitRequestBody);

    }

}
