package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.adapter.outbound.jdbc.JdbcGroupRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcIdentityAliasRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcMemberRepository;
import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.group.Member;
import br.com.gradechecker.core.port.GitHubGateway;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** openspec capability group-members: "Manage members" page (/group/members). */
public class GroupMembersPageServletTest {

    private final List<Long> reads = new ArrayList<>();
    private final List<Member> members = List.of(new Member("Alice", "alice", 6), new Member("bob", "bob", 6));
    private GroupMembersPageServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        System.setProperty(AdminConfig.ADMINS, "professor-enoqueleal");
        JdbcGroupRepository groups = mock(JdbcGroupRepository.class);
        when(groups.findActive(6L)).thenReturn(new Group(6, "cannamed", "u", "o", "cannamed"));
        JdbcMemberRepository memberRepository = mock(JdbcMemberRepository.class);
        when(memberRepository.findByGroupId(anyLong())).thenAnswer(call -> { reads.add(call.getArgument(0)); return members; });
        servlet = new GroupMembersPageServlet(new Membership(groups, memberRepository, mock(JdbcIdentityAliasRepository.class),
                mock(GitHubGateway.class), GradingConfig.defaults()));

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("professor-enoqueleal");
        when(request.getParameter("id")).thenReturn("6");
        when(request.getRequestDispatcher("/group-members.jsp")).thenReturn(dispatcher);
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    @Test
    public void administratorSeesTheMembersOfTheGroup() throws Exception {
        servlet.doGet(request, response);

        verify(response, never()).setStatus(anyInt());
        verify(request).setAttribute("groupId", 6L);
        verify(request).setAttribute("members", members);
        verify(request).setAttribute(eq("group"), any(Group.class));
        verify(request, never()).setAttribute(eq("pageError"), any());
        verify(dispatcher).forward(request, response);
        assertEquals(List.of(6L), reads);
    }

    @Test
    public void nonAdministratorGets403WithoutReadingMembers() throws Exception {
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("alice");

        servlet.doGet(request, response);

        verify(response).setStatus(403);
        verify(request).setAttribute("pageError", "Only administrators can manage members.");
        verify(request, never()).setAttribute(eq("members"), any());
        verify(dispatcher).forward(request, response);
        assertTrue(reads.isEmpty());
    }

    @Test
    public void invalidOrMissingIdIs400() throws Exception {
        when(request.getParameter("id")).thenReturn("abc");
        servlet.doGet(request, response);
        verify(response).setStatus(400);

        reset(response);
        when(request.getParameter("id")).thenReturn(null);
        servlet.doGet(request, response);
        verify(response).setStatus(400);

        assertTrue(reads.isEmpty());
    }

    @Test
    public void unknownGroupIs404() throws Exception {
        when(request.getParameter("id")).thenReturn("999");

        servlet.doGet(request, response);

        verify(response).setStatus(404);
        verify(request).setAttribute("pageError", "Group not found.");
        assertTrue(reads.isEmpty());
    }
}
