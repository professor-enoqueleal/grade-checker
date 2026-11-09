package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.adapter.outbound.jdbc.JdbcGroupRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcMemberRepository;
import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.application.GroupManagement;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.github.GitHubRepository;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.port.GitHubGateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** openspec capability group-management: API /groups/api. */
public class GroupsApiServletTest {

    private JdbcGroupRepository groups;
    private GitHubGateway gitHub;
    private GroupsApiServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private StringWriter body;

    @Before
    public void setUp() throws Exception {
        System.setProperty(AdminConfig.ADMINS, "professor-enoqueleal");
        groups = mock(JdbcGroupRepository.class);
        gitHub = mock(GitHubGateway.class);
        servlet = new GroupsApiServlet(new GroupManagement(groups, mock(JdbcMemberRepository.class), gitHub));

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        body = new StringWriter();
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("accessToken")).thenReturn("token");
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("professor-enoqueleal");
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(request.getServletPath()).thenReturn("/groups/api");
        when(request.getParameter("name")).thenReturn(" clinica ");
        when(request.getParameter("repositoryUrl")).thenReturn("https://github.com/hugodev0711/clinica-pi.git");
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    private JsonObject json() {
        return JsonParser.parseString(body.toString()).getAsJsonObject();
    }

    private void gitHubAnswersClinica() {
        when(gitHub.getRepository("hugodev0711", "clinica-pi", "token")).thenReturn(
                new GitHubRepository("Hugodev0711", "clinica-pi", "https://github.com/Hugodev0711/clinica-pi", false));
    }

    @Test
    public void createsWithTheSpellingOfGitHub() throws Exception {
        gitHubAnswersClinica();
        when(groups.create(any())).thenAnswer(call -> {   // the repository sets the id
            call.<Group>getArgument(0).setId(42);
            return 42L;
        });

        servlet.doPost(request, response);

        verify(response).setStatus(200);
        ArgumentCaptor<Group> saved = ArgumentCaptor.forClass(Group.class);
        verify(groups).create(saved.capture());
        assertEquals("clinica", saved.getValue().getName());
        assertEquals("https://github.com/Hugodev0711/clinica-pi", saved.getValue().getRepositoryUrl());
        assertEquals("Hugodev0711", saved.getValue().getOwner());
        assertEquals("clinica-pi", saved.getValue().getRepository());
        assertEquals(42, json().get("id").getAsLong());
        assertFalse(json().get("archived").getAsBoolean());
    }

    @Test
    public void withoutHeaderOrAsNonAdministratorNothingIsCreated() throws Exception {
        when(request.getHeader("X-Requested-With")).thenReturn(null);
        servlet.doPost(request, response);
        verify(response).setStatus(403);

        reset(response);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("alice");
        servlet.doPost(request, response);
        verify(response).setStatus(403);
        assertEquals("FORBIDDEN", json().get("error").getAsString());
        assertEquals("Only administrators can manage groups.", json().get("message").getAsString());

        verifyNoInteractions(groups, gitHub);
    }

    @Test
    public void emptyNameAndInvalidUrlAreRefusedWithoutCallingGitHub() throws Exception {
        when(request.getParameter("name")).thenReturn("  ");
        servlet.doPost(request, response);
        verify(response).setStatus(400);
        assertEquals("The group name is required.", json().get("message").getAsString());

        reset(response);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        when(request.getParameter("name")).thenReturn("x");
        when(request.getParameter("repositoryUrl")).thenReturn("https://gitlab.com/a/b");
        servlet.doPost(request, response);
        verify(response).setStatus(400);
        assertEquals("Enter the URL of a GitHub repository (https://github.com/owner/repository).", json().get("message").getAsString());

        verifyNoInteractions(gitHub);
        verify(groups, never()).create(any());
    }

    @Test
    public void missingRepositoryIs400AndGitHubErrorsKeepTheirStatus() throws Exception {
        servlet.doPost(request, response);   // gitHub mock answers null
        verify(response).setStatus(400);
        assertEquals("invalid_repository", json().get("error").getAsString());
        assertEquals("Repository not found or not accessible with this token.", json().get("message").getAsString());

        reset(response);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        when(gitHub.getRepository(any(), any(), any())).thenThrow(new GitHubApiException(429, GitHubApiException.RATE_LIMITED, "limit"));
        servlet.doPost(request, response);
        verify(response).setStatus(429);
        verify(groups, never()).create(any());
    }

    @Test
    public void repositoryOfAnActiveOrArchivedGroupIs409() throws Exception {
        Group active = new Group(3, "clinica", "https://github.com/Hugodev0711/clinica-pi", "Hugodev0711", "clinica-pi");
        when(groups.findByRepository(eq("https://github.com/hugodev0711/clinica-pi.git"), isNull())).thenReturn(active);
        servlet.doPost(request, response);
        verify(response).setStatus(409);
        assertEquals("This repository is already registered.", json().get("message").getAsString());

        reset(response);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        active.setArchivedAt(Instant.now());
        servlet.doPost(request, response);
        verify(response).setStatus(409);
        assertEquals("This repository is already registered in an archived group. Restore it instead.", json().get("message").getAsString());

        verifyNoInteractions(gitHub);
    }

    @Test
    public void editKeepsTheIdAndRefusesArchivedOrMissingGroups() throws Exception {
        when(request.getParameter("id")).thenReturn("6");
        when(groups.findActive(6L)).thenReturn(new Group(6, "old", "https://github.com/a/b", "a", "b"));
        gitHubAnswersClinica();
        when(groups.update(any())).thenReturn(true);

        servlet.doPut(request, response);

        verify(response).setStatus(200);
        ArgumentCaptor<Group> saved = ArgumentCaptor.forClass(Group.class);
        verify(groups).update(saved.capture());
        assertEquals(6, saved.getValue().getId());
        verify(groups).findByRepository("https://github.com/hugodev0711/clinica-pi.git", 6L);   // itself does not count

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
        when(groups.findActive(6L)).thenReturn(null);   // archived or missing
        servlet.doPut(request, response);
        verify(response).setStatus(404);
    }

    @Test
    public void archiveAndRestore() throws Exception {
        when(request.getParameter("id")).thenReturn("6");
        Group group = new Group(6, "cannamed", "https://github.com/a/b", "a", "b");
        group.setArchivedAt(Instant.now());
        when(groups.find(6L)).thenReturn(group);

        when(request.getServletPath()).thenReturn("/groups/api/archive");
        when(groups.archive(6L)).thenReturn(true);
        servlet.doPost(request, response);
        verify(response).setStatus(200);
        assertTrue(json().get("archived").getAsBoolean());

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
        when(request.getServletPath()).thenReturn("/groups/api/restore");
        when(groups.restore(6L)).thenReturn(false);   // not archived
        servlet.doPost(request, response);
        verify(response).setStatus(404);

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
        when(request.getParameter("id")).thenReturn("abc");
        servlet.doPost(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(gitHub);
    }
}
