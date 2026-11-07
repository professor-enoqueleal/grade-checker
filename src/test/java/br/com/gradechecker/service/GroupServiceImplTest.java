package br.com.gradechecker.service;

import br.com.gradechecker.model.Contributor;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.Member;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.MemberRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** openspec capability group-members: the group detail only reads (contributors from GitHub, members from the database). */
public class GroupServiceImplTest {

    private GroupRepository groups;
    private MemberRepository members;
    private GitHubProxy gitHub;
    private GroupServiceImpl service;
    private Group stored;

    @Before
    public void setUp() {
        groups = mock(GroupRepository.class);
        members = mock(MemberRepository.class);
        gitHub = mock(GitHubProxy.class);
        service = new GroupServiceImpl(groups, members, gitHub);
        stored = new Group(6, "cannamed", "https://github.com/o/cannamed", "o", "cannamed");
        when(groups.getGroupDetail(6L)).thenReturn(stored);
    }

    @Test
    public void contributorsComeFromGitHubAndNothingIsWritten() {
        when(members.findByGroupId(6L)).thenReturn(List.of(new Member("Bia", "bia", "6")));
        when(gitHub.getContributors("token", "o", "cannamed")).thenReturn(List.of(
                new Contributor("bia", 1, "", "", 3), new Contributor("outsider", 2, "", "", 1)));

        Group group = service.getGroup(6L, "token");

        assertEquals(2, group.getContributors().size());
        assertEquals(1, group.getTotalOfMembers());
        assertEquals("bia", group.getMembers().get(0).getGitHubUserName());
        assertNull(group.getContributorsError());
        verify(members, never()).addIfAbsent(anyLong(), any(), any(), anyBoolean());
        verify(members, never()).delete(anyLong(), any());
        assertNull("the cached group is not changed", stored.getContributors());
    }

    @Test
    public void memberWithoutCommitsCountsOnlyAsMember() {
        when(members.findByGroupId(6L)).thenReturn(List.of(new Member("Nova", "nova-aluna", "6"), new Member("bia", "bia", "6")));
        when(gitHub.getContributors(any(), any(), any())).thenReturn(List.of(new Contributor("bia", 1, "", "", 3)));

        Group group = service.getGroup(6L, "token");

        assertEquals(2, group.getTotalOfMembers());
        assertEquals(1, group.getContributors().size());
    }

    @Test
    public void gitHubErrorDoesNotBreakThePage() {
        when(members.findByGroupId(6L)).thenReturn(List.of(new Member("bia", "bia", "6")));
        when(gitHub.getContributors(any(), any(), any())).thenThrow(
                new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found or not accessible with this token."));

        Group group = service.getGroup(6L, "token");

        assertEquals("Repository not found or not accessible with this token.", group.getContributorsError());
        assertTrue(group.getContributors().isEmpty());
        assertEquals(1, group.getTotalOfMembers());
    }
}
