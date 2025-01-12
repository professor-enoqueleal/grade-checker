package br.com.gradechecker.service;

import br.com.gradechecker.proxy.GitHubProxy;

public class CommitDetailService {

    private final GitHubProxy gitHubProxy;

    public CommitDetailService() {
        this.gitHubProxy = new GitHubProxy();
    }

}
