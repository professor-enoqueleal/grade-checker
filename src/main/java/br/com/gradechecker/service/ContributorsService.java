package br.com.gradechecker.service;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.proxy.GitHubProxy;

public class ContributorsService {

    private final GitHubProxy gitHubProxy;

    public ContributorsService() {
        this(new GitHubProxy());
    }

    public ContributorsService(GitHubProxy gitHubProxy) {
        this.gitHubProxy = gitHubProxy;
    }

    public ContributionsReport getContributionsReport(CommitRequestBody commitRequestBody) {

        return this.gitHubProxy.getContributionsReport(commitRequestBody);

    }

}
