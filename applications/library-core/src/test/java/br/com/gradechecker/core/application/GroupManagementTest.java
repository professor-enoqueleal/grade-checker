package br.com.gradechecker.core.application;

import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.group.Group;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class GroupManagementTest {

    private InMemoryPorts.Groups groups;
    private InMemoryPorts.Members members;
    private InMemoryPorts.GitHub gitHub;
    private GroupManagement management;

    @Before
    public void setUp() {
        groups = new InMemoryPorts.Groups();
        members = new InMemoryPorts.Members(new InMemoryPorts.Aliases());
        gitHub = new InMemoryPorts.GitHub();
        management = new GroupManagement(groups, members, gitHub);
    }

    private static DomainException refused(Runnable action) {
        try {
            action.run();
        } catch (DomainException e) {
            return e;
        }
        throw new AssertionError("expected a DomainException");
    }

    @Test
    public void createStoresTheSpellingOfGitHub() {
        gitHub.repository("acme/demo", "Acme", "Demo", true);
        Group g = management.create("  Team A ", "https://github.com/acme/demo", "token");

        assertEquals("Team A", g.getName());
        assertEquals("https://github.com/Acme/Demo", g.getRepositoryUrl());
        assertEquals("Acme", g.getOwner());
        assertEquals("Demo", g.getRepository());
        assertTrue(g.isRepositoryPrivate());
        assertSame(g, groups.find(g.getId()));
    }

    @Test
    public void nameAndUrlAreChecked() {
        DomainException noName = refused(() -> management.create(" ", "https://github.com/acme/demo", "t"));
        assertEquals(DomainException.Kind.INVALID, noName.getKind());
        assertEquals(GroupManagement.NAME_REQUIRED, noName.getMessage());

        DomainException badUrl = refused(() -> management.create("A", "acme/demo", "t"));
        assertEquals("bad_request", badUrl.getCode());
        assertEquals(0, gitHub.calls);
    }

    @Test
    public void repositoryOfAnArchivedGroupSuggestsRestoring() {
        groups.add("Old", "https://github.com/acme/demo", true);
        DomainException e = refused(() -> management.create("A", "https://github.com/ACME/demo/", "t"));
        assertEquals(DomainException.Kind.CONFLICT, e.getKind());
        assertEquals("already_registered", e.getCode());
        assertEquals(GroupManagement.ALREADY_ARCHIVED, e.getMessage());
        assertEquals("checked before calling GitHub", 0, gitHub.calls);
    }

    @Test
    public void duplicateIsCheckedAgainWithTheSpellingOfGitHub() {
        groups.add("Renamed", "https://github.com/acme/new-name", false);
        gitHub.repository("acme/old-name", "acme", "new-name", false);   // GitHub redirects the old name
        DomainException e = refused(() -> management.create("A", "https://github.com/acme/old-name", "t"));
        assertEquals(GroupManagement.ALREADY_REGISTERED, e.getMessage());
    }

    @Test
    public void repositoryNotAccessible() {
        DomainException e = refused(() -> management.create("A", "https://github.com/acme/secret", "t"));
        assertEquals("invalid_repository", e.getCode());
        assertEquals(GroupManagement.INVALID_REPOSITORY, e.getMessage());
    }

    @Test
    public void archivedGroupsAreNotFoundForEditing() {
        Group old = groups.add("Old", "https://github.com/acme/demo", true);
        DomainException e = refused(() -> management.update(old.getId(), "B", "https://github.com/acme/demo", "t"));
        assertEquals(DomainException.Kind.NOT_FOUND, e.getKind());
        assertEquals(GroupManagement.GROUP_NOT_FOUND, e.getMessage());
    }

    @Test
    public void updateKeepsTheIdAndMayKeepItsOwnRepository() {
        Group g = groups.add("A", "https://github.com/acme/demo", false);
        gitHub.repository("acme/demo", "acme", "demo", false);
        Group stored = management.update(g.getId(), "B", "https://github.com/acme/demo", "t");
        assertEquals(g.getId(), stored.getId());
        assertEquals("B", stored.getName());
    }

    @Test
    public void archiveAndRestore() {
        Group g = groups.add("A", "https://github.com/acme/demo", false);
        assertTrue(management.archive(g.getId()).isArchived());
        assertEquals(DomainException.Kind.NOT_FOUND, refused(() -> management.archive(g.getId())).getKind());
        assertFalse(management.restore(g.getId()).isArchived());
        assertEquals(1, management.activeGroups().size());
    }

    @Test
    public void detailKeepsTheMembersWhenGitHubFails() {
        Group g = groups.add("A", "https://github.com/acme/demo", false);
        members.addIfAbsent(g.getId(), "ana", "Ana", false);
        gitHub.contributorsError = new GitHubApiException(403, GitHubApiException.FORBIDDEN, "No access.");

        GroupManagement.GroupDetail detail = management.detail(g.getId(), "t");
        assertEquals(1, detail.getMembers().size());
        assertTrue(detail.getContributors().isEmpty());
        assertEquals("No access.", detail.getContributorsError());

        gitHub.contributorsError = null;
        gitHub.contributors = List.of(new Contributor("ana", 1, null, null, 3));
        assertNull(management.detail(g.getId(), "t").getContributorsError());
    }
}
