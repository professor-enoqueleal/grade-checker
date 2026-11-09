package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.adapter.outbound.jdbc.JdbcGroupRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcIdentityAliasRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcMemberRepository;
import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.MemberRepository;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** openspec capability group-members: API /group-members. */
public class GroupMembersServletTest {

    private JdbcMemberRepository members;
    private JdbcIdentityAliasRepository aliases;
    private GitHubGateway gitHub;
    private GroupMembersServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private StringWriter body;

    @Before
    public void setUp() throws Exception {
        System.setProperty(AdminConfig.ADMINS, "professor-enoqueleal");
        members = mock(JdbcMemberRepository.class);
        aliases = mock(JdbcIdentityAliasRepository.class);
        gitHub = mock(GitHubGateway.class);
        JdbcGroupRepository groups = mock(JdbcGroupRepository.class);
        when(groups.findActive(6L)).thenReturn(new Group(6, "cannamed", "u", "o", "cannamed"));
        servlet = new GroupMembersServlet(new Membership(groups, members, aliases, gitHub, GradingConfig.defaults()));

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        body = new StringWriter();
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("accessToken")).thenReturn("token");
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("professor-enoqueleal");
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("login")).thenReturn("THIAGOFP77");
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    private JsonObject json() {
        return JsonParser.parseString(body.toString()).getAsJsonObject();
    }

    @Test
    public void addsWithTheSpellingOfGitHubAndLoginAsDefaultName() throws Exception {
        when(gitHub.getUser("THIAGOFP77", "token")).thenReturn(new Contributor("thiagofp77", 7, "", "", 0));
        when(members.addIfAbsent(6L, "thiagofp77", "thiagofp77", false)).thenReturn(true);

        servlet.doPost(request, response);

        verify(response).setStatus(200);
        assertEquals("thiagofp77", json().get("login").getAsString());
        assertEquals("thiagofp77", json().get("name").getAsString());
        verify(members).addIfAbsent(6L, "thiagofp77", "thiagofp77", false);
    }

    @Test
    public void manualFormKeepsTheName() throws Exception {
        when(request.getParameter("name")).thenReturn(" Nova Aluna ");
        when(request.getParameter("manual")).thenReturn("true");
        when(gitHub.getUser(any(), any())).thenReturn(new Contributor("nova-aluna", 8, "", "", 0));
        when(members.addIfAbsent(anyLong(), any(), any(), anyBoolean())).thenReturn(true);

        servlet.doPost(request, response);

        verify(members).addIfAbsent(6L, "nova-aluna", "Nova Aluna", true);
    }

    @Test
    public void unknownGitHubUserIs400AndNothingIsSaved() throws Exception {
        when(gitHub.getUser(any(), any())).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).setStatus(400);
        assertEquals("invalid_login", json().get("error").getAsString());
        assertEquals("This GitHub user does not exist.", json().get("message").getAsString());
        verify(members, never()).addIfAbsent(anyLong(), any(), any(), anyBoolean());
    }

    @Test
    public void alreadyMemberIs409() throws Exception {
        when(gitHub.getUser(any(), any())).thenReturn(new Contributor("thiagofp77", 7, "", "", 0));
        when(members.addIfAbsent(anyLong(), any(), any(), anyBoolean())).thenReturn(false);

        servlet.doPost(request, response);

        verify(response).setStatus(409);
        assertEquals("This user is already a member of the group.", json().get("message").getAsString());
    }

    @Test
    public void gitHubErrorKeepsItsStatus() throws Exception {
        when(gitHub.getUser(any(), any())).thenThrow(new GitHubApiException(429, GitHubApiException.RATE_LIMITED, "GitHub API rate limit reached."));

        servlet.doPost(request, response);

        verify(response).setStatus(429);
    }

    @Test
    public void removesWhenThereIsNoManualAssociation() throws Exception {
        when(members.isMember(6L, "THIAGOFP77")).thenReturn(true);
        when(members.delete(6L, "THIAGOFP77")).thenReturn(true);

        servlet.doDelete(request, response);

        verify(response).setStatus(200);
        verify(members).delete(6L, "THIAGOFP77");
    }

    @Test
    public void removalIsBlockedByManualAssociations() throws Exception {
        when(members.isMember(6L, "THIAGOFP77")).thenReturn(true);
        when(aliases.existsForLogin(6L, "THIAGOFP77")).thenReturn(true);

        servlet.doDelete(request, response);

        verify(response).setStatus(409);
        assertEquals("has_aliases", json().get("error").getAsString());
        assertEquals("Unlink the identities linked to this student before removing them.", json().get("message").getAsString());
        verify(members, never()).delete(anyLong(), any());
    }

    @Test
    public void removingSomeoneWhoIsNotAMemberIs404() throws Exception {
        servlet.doDelete(request, response);

        verify(response).setStatus(404);
        verify(members, never()).delete(anyLong(), any());
    }

    @Test
    public void withoutHeaderOrNotAdminIs403AndNothingChanges() throws Exception {
        when(request.getHeader("X-Requested-With")).thenReturn(null);
        servlet.doPost(request, response);
        verify(response).setStatus(403);

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        body.getBuffer().setLength(0);
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("alice");
        servlet.doDelete(request, response);
        verify(response).setStatus(403);
        assertEquals("FORBIDDEN", json().get("error").getAsString());
        assertEquals("Only administrators can manage members.", json().get("message").getAsString());

        verifyNoInteractions(members, aliases, gitHub);
    }

    @Test
    public void invalidParametersAndUnknownGroup() throws Exception {
        when(request.getParameter("groupId")).thenReturn("abc");
        servlet.doPost(request, response);
        verify(response).setStatus(400);

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("login")).thenReturn("bad login/with spaces");
        servlet.doPost(request, response);
        verify(response).setStatus(400);

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
        when(request.getParameter("login")).thenReturn("ok");
        when(request.getParameter("groupId")).thenReturn("999");
        servlet.doPost(request, response);
        verify(response).setStatus(404);

        verifyNoInteractions(members, gitHub);
    }

    @Test
    public void oldBotMemberCanBeRemoved() throws Exception {
        when(request.getParameter("login")).thenReturn("dependabot[bot]");
        when(members.isMember(6L, "dependabot[bot]")).thenReturn(true);
        when(members.delete(6L, "dependabot[bot]")).thenReturn(true);

        servlet.doDelete(request, response);

        verify(response).setStatus(200);
    }

    // ------------------------------------------------------------------ PUT (edit)

    private void editing(String login, String newLogin, String name) {
        when(request.getParameter("login")).thenReturn(login);
        when(request.getParameter("newLogin")).thenReturn(newLogin);
        when(request.getParameter("name")).thenReturn(name);
    }

    @Test
    public void editWithoutHeaderOrAsNonAdministratorIsRefused() throws Exception {
        editing("alice", "bob", "Bob");
        when(request.getHeader("X-Requested-With")).thenReturn(null);
        servlet.doPut(request, response);
        verify(response).setStatus(403);

        reset(response);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("alice");
        servlet.doPut(request, response);
        verify(response).setStatus(403);
        assertEquals("FORBIDDEN", json().get("error").getAsString());

        verifyNoInteractions(members, gitHub);
    }

    @Test
    public void editOfANonMemberIs404() throws Exception {
        editing("carol", "dave", null);
        servlet.doPut(request, response);
        verify(response).setStatus(404);
        verify(members, never()).rename(anyLong(), any(), any(), any());
        verifyNoInteractions(gitHub);
    }

    @Test
    public void invalidNewLoginIs400() throws Exception {
        editing("alice", "bad login", null);
        servlet.doPut(request, response);
        verify(response).setStatus(400);
        assertEquals("bad_request", json().get("error").getAsString());
        verifyNoInteractions(members, gitHub);
    }

    @Test
    public void newLoginThatDoesNotExistOnGitHubIsRefused() throws Exception {
        editing("alice", "ghost-4242", null);
        when(members.isMember(6L, "alice")).thenReturn(true);
        when(gitHub.getUser("ghost-4242", "token")).thenReturn(null);

        servlet.doPut(request, response);

        verify(response).setStatus(400);
        assertEquals("This GitHub user does not exist.", json().get("message").getAsString());
        verify(members, never()).rename(anyLong(), any(), any(), any());
    }

    @Test
    public void newLoginOfAnotherMemberIs409() throws Exception {
        editing("alice", "Bob", null);
        when(members.isMember(6L, "alice")).thenReturn(true);
        when(gitHub.getUser("Bob", "token")).thenReturn(new Contributor("bob", 2, "", "", 0));
        when(members.rename(6L, "alice", "bob", "bob")).thenReturn(MemberRepository.RenameResult.ALREADY_MEMBER);

        servlet.doPut(request, response);

        verify(response).setStatus(409);
        assertEquals("already_member", json().get("error").getAsString());
    }

    @Test
    public void nameOnlyDoesNotCallGitHub() throws Exception {
        editing("alice", null, "Alice A.");
        when(members.isMember(6L, "alice")).thenReturn(true);
        when(members.rename(6L, "alice", "alice", "Alice A.")).thenReturn(MemberRepository.RenameResult.OK);

        servlet.doPut(request, response);

        verify(response).setStatus(200);
        assertEquals("alice", json().get("login").getAsString());
        assertEquals("alice", json().get("previousLogin").getAsString());
        assertEquals("Alice A.", json().get("name").getAsString());
        verifyNoInteractions(gitHub);
    }

    @Test
    public void caseOnlyFixUsesTheSpellingOfGitHub() throws Exception {
        editing("ThiagoFP77", "thiagofp77", "Thiago");
        when(members.isMember(6L, "ThiagoFP77")).thenReturn(true);
        when(gitHub.getUser("thiagofp77", "token")).thenReturn(new Contributor("thiagofp77", 7, "", "", 0));
        when(members.rename(6L, "ThiagoFP77", "thiagofp77", "Thiago")).thenReturn(MemberRepository.RenameResult.OK);

        servlet.doPut(request, response);

        verify(response).setStatus(200);
        assertEquals("thiagofp77", json().get("login").getAsString());
        assertEquals("ThiagoFP77", json().get("previousLogin").getAsString());
    }

    @Test
    public void loginChangeIsStoredWithTheSpellingOfGitHubAndDefaultName() throws Exception {
        editing("joao-silv", "Joao-Silva", " ");
        when(members.isMember(6L, "joao-silv")).thenReturn(true);
        when(gitHub.getUser("Joao-Silva", "token")).thenReturn(new Contributor("joao-silva", 9, "", "", 0));
        when(members.rename(6L, "joao-silv", "joao-silva", "joao-silva")).thenReturn(MemberRepository.RenameResult.OK);

        servlet.doPut(request, response);

        verify(response).setStatus(200);
        assertEquals(6, json().get("groupId").getAsInt());
        assertEquals("joao-silva", json().get("login").getAsString());
        assertEquals("joao-silva", json().get("name").getAsString());
        verify(members).rename(6L, "joao-silv", "joao-silva", "joao-silva");
    }
}
