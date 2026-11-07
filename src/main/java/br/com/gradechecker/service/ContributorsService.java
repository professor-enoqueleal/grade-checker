package br.com.gradechecker.service;

import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.IdentityAliasRepository;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class ContributorsService {

    private final GitHubProxy gitHubProxy;
    private final Function<Long, Map<String, String>> aliasesByGroup; // openspec capability identity-aliases

    public ContributorsService() {
        this(new GitHubProxy(), new IdentityAliasRepository()::findByGroupId);
    }

    public ContributorsService(GitHubProxy gitHubProxy) {
        this(gitHubProxy, groupId -> Collections.emptyMap());
    }

    public ContributorsService(GitHubProxy gitHubProxy, Function<Long, Map<String, String>> aliasesByGroup) {
        this.gitHubProxy = gitHubProxy;
        this.aliasesByGroup = aliasesByGroup;
    }

    /** Report of the repository; the manual associations of the group (when groupId is given) are applied. */
    public ContributionsReport getContributionsReport(CommitRequestBody commitRequestBody) {

        return this.gitHubProxy.getContributionsReport(commitRequestBody, aliasesOf(commitRequestBody.getGroupId()));

    }

    private Map<String, String> aliasesOf(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) return Collections.emptyMap();
        try {
            return aliasesByGroup.apply(Long.parseLong(groupId.trim()));
        } catch (NumberFormatException e) {
            return Collections.emptyMap();
        }
    }

}
