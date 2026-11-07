package br.com.gradechecker.service;

import br.com.gradechecker.model.Contributor;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.Member;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.MemberRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Group detail (openspec capability group-members): read only.
 * Contributors come from GitHub (cached in memory); members come from the database and are only changed by an
 * administrator. Nothing is written here.
 */
public class GroupServiceImpl implements GroupService {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final GitHubProxy gitHubProxy;

    private final GroupRepository groupRepository;

    private final MemberRepository memberRepository;

    public GroupServiceImpl() {
        this(new GroupRepository(), new MemberRepository(), new GitHubProxy());
    }

    // injectable constructor for tests
    public GroupServiceImpl(GroupRepository groupRepository, MemberRepository memberRepository, GitHubProxy gitHubProxy) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.gitHubProxy = gitHubProxy;
    }

    @Override
    public Group getGroup(Long groupId, String accessToken) {

        Group stored = groupRepository.getGroupDetail(groupId);

        // a copy: the stored group is a cached object shared by every user
        Group group = new Group(stored.getId(), stored.getName(), stored.getRepositoryUrl(), stored.getOwner(), stored.getRepository());

        List<Member> members = memberRepository.findByGroupId(groupId);
        group.setMembers(members);
        group.setTotalOfMembers(members.size());

        List<Contributor> contributors;
        try {
            contributors = gitHubProxy.getContributors(accessToken, group.getOwner(), group.getRepository());
        } catch (GitHubApiException e) {
            logger.warn("Contributors of group {} not available: {} ({})", groupId, e.getErrorCode(), e.getMessage());
            contributors = null;
            group.setContributorsError(e.getMessage());
        }

        group.setContributors(contributors == null ? Collections.emptyList() : contributors);
        return group;

    }

}
