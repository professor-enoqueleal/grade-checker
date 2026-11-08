package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.repository.GroupRepository;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** openspec capability group-management: "Manage groups" page (/groups). */
public class GroupsPageServletTest {

    private GroupRepository groups;
    private GroupsPageServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        System.setProperty(AdminConfig.ADMINS, "professor-enoqueleal");
        groups = mock(GroupRepository.class);
        servlet = new GroupsPageServlet(groups, ZoneId.of("America/Sao_Paulo"));
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("professor-enoqueleal");
        when(request.getRequestDispatcher("/groups.jsp")).thenReturn(dispatcher);
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    @SuppressWarnings("unchecked")
    private <T> T attribute(String name) {
        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq(name), value.capture());
        return (T) value.getValue();
    }

    @Test
    public void activeAndArchivedGroupsWithTheirMembers() throws Exception {
        Group archived = new Group(9, "old", "https://github.com/a/old", "a", "old");
        archived.setArchivedAt(Instant.parse("2026-11-07T15:00:00Z"));
        when(groups.findAll()).thenReturn(List.of(new Group(3, "clinica", "https://github.com/Hugodev0711/clinica-pi", "Hugodev0711", "clinica-pi")));
        when(groups.findArchived()).thenReturn(List.of(archived));
        when(groups.countMembers()).thenReturn(Map.of(3L, 4));
        when(request.getParameter("add")).thenReturn("1");

        servlet.doGet(request, response);

        List<GroupsPageServlet.Row> active = attribute("groups");
        assertEquals(1, active.size());
        assertEquals(4, active.get(0).getMembers());
        assertEquals("https://github.com/Hugodev0711/clinica-pi", active.get(0).getRepositoryLink());
        List<GroupsPageServlet.Row> old = attribute("archivedGroups");
        assertEquals("Nov 7, 2026", old.get(0).getArchivedOn());
        assertEquals(0, old.get(0).getMembers());
        assertEquals(Boolean.TRUE, attribute("openAddForm"));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void nonAdministratorGets403WithoutReadingGroups() throws Exception {
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("alice");

        servlet.doGet(request, response);

        verify(response).setStatus(403);
        verify(request).setAttribute("pageError", "Only administrators can manage groups.");
        verifyNoInteractions(groups);
    }
}
