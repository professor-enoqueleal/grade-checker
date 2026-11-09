package br.com.gradechecker.core.application;

import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.github.GitHubRepository;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.group.Member;
import br.com.gradechecker.core.group.RepositoryUrl;
import br.com.gradechecker.core.internal.Log;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.MemberRepository;
import br.com.gradechecker.core.port.RepositoryTakenException;

import java.util.Collections;
import java.util.List;

/**
 * Groups (capability core-use-cases): list, detail, create, edit, archive and restore. A repository URL belongs to one
 * group (active or archived), checked with the typed URL and with the spelling returned by GitHub, which is the one
 * stored.
 */
public class GroupManagement {

    public static final String GROUP_NOT_FOUND = "Group not found.";
    public static final String NAME_REQUIRED = "The group name is required.";
    public static final String INVALID_REPOSITORY = "Repository not found or not accessible with this token.";
    public static final String ALREADY_REGISTERED = "This repository is already registered.";
    public static final String ALREADY_ARCHIVED = "This repository is already registered in an archived group. Restore it instead.";

    static final int MAX_NAME = 255;

    private static final Log logger = Log.of(GroupManagement.class);

    /** A group with its members and the contributors of the repository (empty, with the error, when GitHub fails). */
    public static final class GroupDetail {
        private final Group group;
        private final List<Member> members;
        private final List<Contributor> contributors;
        private final String contributorsError;

        GroupDetail(Group group, List<Member> members, List<Contributor> contributors, String contributorsError) {
            this.group = group;
            this.members = members;
            this.contributors = contributors;
            this.contributorsError = contributorsError;
        }

        public Group getGroup() { return group; }
        public List<Member> getMembers() { return members; }
        public List<Contributor> getContributors() { return contributors; }
        /** Message of GitHub when the contributors could not be read; null otherwise. */
        public String getContributorsError() { return contributorsError; }
    }

    private final GroupRepository groups;
    private final MemberRepository members;
    private final GitHubGateway gitHub;

    public GroupManagement(GroupRepository groups, MemberRepository members, GitHubGateway gitHub) {
        this.groups = groups;
        this.members = members;
        this.gitHub = gitHub;
    }

    public List<Group> activeGroups() {
        return groups.findActive();
    }

    public List<Group> archivedGroups() {
        return groups.findArchived();
    }

    /** The active group; NOT_FOUND when missing or archived. */
    public Group activeGroup(long id) {
        Group group = groups.findActive(id);
        if (group == null) throw notFound();
        return group;
    }

    /** The group, its members and the contributors of its repository read with the token. */
    public GroupDetail detail(long id, String accessToken) {
        Group group = activeGroup(id);
        List<Member> groupMembers = members.findByGroupId(id);
        try {
            return new GroupDetail(group, groupMembers, gitHub.getContributors(group.getOwner(), group.getRepository(), accessToken), null);
        } catch (GitHubApiException e) {
            logger.warn("Contributors of group {} not available: {} ({})", id, e.getErrorCode(), e.getMessage());
            return new GroupDetail(group, groupMembers, Collections.emptyList(), e.getMessage());
        }
    }

    public Group create(String name, String repositoryUrl, String accessToken) {
        Group group = validated(name, repositoryUrl, null, accessToken);
        try {
            groups.create(group);
        } catch (RepositoryTakenException e) {
            throw DomainException.conflict("already_registered", ALREADY_REGISTERED);
        }
        return group;
    }

    /** Edits name and repository of an active group; returns the stored group. */
    public Group update(long id, String name, String repositoryUrl, String accessToken) {
        activeGroup(id);
        Group group = validated(name, repositoryUrl, id, accessToken);
        group.setId(id);
        try {
            if (!groups.update(group)) throw notFound();
        } catch (RepositoryTakenException e) {
            throw DomainException.conflict("already_registered", ALREADY_REGISTERED);
        }
        Group stored = groups.find(id);
        return stored == null ? group : stored;
    }

    public Group archive(long id) {
        if (!groups.archive(id)) throw notFound();
        return groups.find(id);
    }

    public Group restore(long id) {
        if (!groups.restore(id)) throw notFound();
        return groups.find(id);
    }

    /** Name and URL checks, duplicate check and GitHub validation; the group has the spelling returned by GitHub. */
    private Group validated(String name, String typed, Long editedId, String accessToken) {
        name = blankToNull(name);
        if (name == null) throw DomainException.invalid("bad_request", NAME_REQUIRED);
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);

        typed = blankToNull(typed);
        RepositoryUrl url = RepositoryUrl.parse(typed);
        if (url == null) throw DomainException.invalid("bad_request", RepositoryUrl.INVALID);

        duplicate(typed, editedId);

        GitHubRepository repository = gitHub.getRepository(url.getOwner(), url.getRepository(), accessToken);
        if (repository == null) throw DomainException.invalid("invalid_repository", INVALID_REPOSITORY);

        // GitHub may answer with another spelling (or a renamed repository): check again with its URL
        if (!RepositoryUrl.normalize(repository.getHtmlUrl()).equals(RepositoryUrl.normalize(typed))) {
            duplicate(repository.getHtmlUrl(), editedId);
        }

        Group group = new Group(name, repository.getHtmlUrl(), repository.getOwnerLogin(), repository.getName());
        group.setRepositoryPrivate(repository.isPrivateRepository());
        return group;
    }

    private void duplicate(String repositoryUrl, Long editedId) {
        Group other = groups.findByRepository(repositoryUrl, editedId);
        if (other != null) {
            throw DomainException.conflict("already_registered", other.isArchived() ? ALREADY_ARCHIVED : ALREADY_REGISTERED);
        }
    }

    static DomainException notFound() {
        return DomainException.notFound("not_found", GROUP_NOT_FOUND);
    }

    static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
