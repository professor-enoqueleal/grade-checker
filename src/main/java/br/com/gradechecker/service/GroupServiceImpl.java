package br.com.gradechecker.service;

import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.Member;
import br.com.gradechecker.model.Contributor;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.ContributorsRepository;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.MemberRepository;

import java.util.List;
import java.util.stream.Collectors;

public class GroupServiceImpl implements GroupService {

    private final ContributorsRepository contributorsRepository;

    private final GitHubProxy gitHubProxy;

    private final GroupRepository groupRepository;

    private final MemberRepository memberRepository;

    public GroupServiceImpl() {
        this(new ContributorsRepository(), new GroupRepository(), new MemberRepository(), new GitHubProxy());
    }

    // injectable constructor for tests
    public GroupServiceImpl(ContributorsRepository contributorsRepository, GroupRepository groupRepository, MemberRepository memberRepository, GitHubProxy gitHubProxy) {
        this.contributorsRepository = contributorsRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.gitHubProxy = gitHubProxy;
    }

    @Override
    public Group getGroup(Long groupId, String accessToken) {

        var contributors = contributorsRepository.getByGroupId(groupId);

        Group group = groupRepository.getGroupDetail(groupId);
        int totalOfMembers = memberRepository.findById(groupId);

        if (contributors.isEmpty()) {

            contributors = gitHubProxy.getContributors(accessToken, group.getOwner(), group.getRepository());
            contributorsRepository.create(contributors, groupId);

            // convert contributors to members and persist in MEMBERS table (idempotent)
            if (contributors != null && !contributors.isEmpty()) {
                // sync existing manual members with contributors (fill GITHUB_ID and CONTRIBUTIONS)
                memberRepository.syncMembersWithContributors(contributors, groupId);

                List<Member> members = contributors.stream()
                        .map(c -> new Member(c.getLogin(), c.getLogin(), String.valueOf(groupId), c.getId()))
                        .collect(Collectors.toList());

                memberRepository.createBulkIfNotExists(members, groupId);

                // refresh totalOfMembers after insertion
                totalOfMembers = memberRepository.findById(groupId);
            }

        }

        group.setContributors(contributors);
        group.setTotalOfMembers(totalOfMembers);

        return group;

    }

}
