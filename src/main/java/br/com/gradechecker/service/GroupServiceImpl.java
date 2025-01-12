package br.com.gradechecker.service;

import br.com.gradechecker.model.Group;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.ContributorsRepository;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.MemberRepository;

public class GroupServiceImpl implements GroupService {

    private final ContributorsRepository contributorsRepository;

    private final GitHubProxy gitHubProxy;

    private final GroupRepository groupRepository;

    private final MemberRepository memberRepository;

    public GroupServiceImpl() {
        this.contributorsRepository = new ContributorsRepository();
        this.groupRepository = new GroupRepository();
        this.memberRepository = new MemberRepository();
        this.gitHubProxy = new GitHubProxy();
    }

    @Override
    public Group getGroup(Long groupId, String accessToken) {

        var contributors = contributorsRepository.getByGroupId(groupId);

        Group group = groupRepository.getGroupDetail(groupId);
        int totalOfMembers = memberRepository.findById(groupId);

        if (contributors.isEmpty()) {

            contributors = gitHubProxy.getContributors(accessToken, group.getOwner(), group.getRepository());
            contributorsRepository.create(contributors, groupId);

        }

        group.setContributors(contributors);
        group.setTotalOfMembers(totalOfMembers);

        return group;

    }

}
