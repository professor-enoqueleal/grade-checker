package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.repository.IdentityAliasRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** openspec capability identity-aliases: POST/DELETE /identity-aliases. */
public class IdentityAliasServletTest {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private IdentityAliasRepository repository;
    private StringWriter body;
    private IdentityAliasServlet servlet;

    @Before
    public void setUp() throws Exception {
        System.setProperty(AdminConfig.ADMINS, "professor-enoqueleal");
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        repository = mock(IdentityAliasRepository.class);
        body = new StringWriter();

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("professor-enoqueleal");
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        when(request.getParameter("groupId")).thenReturn("6");
        when(request.getParameter("identityKey")).thenReturn("name:joaolima");
        when(request.getParameter("login")).thenReturn("JoaoLima16");

        servlet = new IdentityAliasServlet(repository,
                groupId -> List.of("joaolima16", "professor-enoqueleal", "dependabot[bot]"),
                GradingConfig.of(new java.util.Properties() {{ setProperty("grading.excludedLogins", "professor-enoqueleal"); }}));
    }

    @After
    public void clear() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    @Test
    public void adminLinksAnIdentityToAStudentUsingTheRegisteredSpelling() throws Exception {
        servlet.doPost(request, response);

        verify(response).setStatus(200);
        verify(repository).save(6L, "name:joaolima", "joaolima16", "professor-enoqueleal");
        assertTrue(body.toString().contains("\"login\":\"joaolima16\""));
    }

    @Test
    public void archivedGroupIsAMissingGroup() throws Exception {
        IdentityAliasServlet archived = new IdentityAliasServlet(repository, groupId -> List.of("joaolima16"),
                GradingConfig.defaults(), id -> false);   // group-management: archived = not found

        archived.doPost(request, response);

        verify(response).setStatus(404);
        verifyNoInteractions(repository);
    }

    @Test
    public void nonAdminReceives403AndNothingIsSaved() throws Exception {
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("alice");

        servlet.doPost(request, response);
        servlet.doDelete(request, response);

        verify(response, times(2)).setStatus(403);
        verifyNoInteractions(repository);
        assertTrue(body.toString().contains(IdentityAliasServlet.FORBIDDEN));
    }

    @Test
    public void withoutAdminsVariableEveryoneReceives403() throws Exception {
        System.clearProperty(AdminConfig.ADMINS);

        servlet.doPost(request, response);

        verify(response).setStatus(403);
        verifyNoInteractions(repository);
    }

    @Test
    public void requestWithoutXRequestedWithIsRefused() throws Exception {
        when(request.getHeader("X-Requested-With")).thenReturn(null); // e.g. a cross-site form

        servlet.doPost(request, response);

        verify(response).setStatus(403);
        verifyNoInteractions(repository);
    }

    @Test
    public void loginOutsideTheGroupOrNotAStudentIsRejected() throws Exception {
        for (String login : new String[]{"stranger", "professor-enoqueleal", "dependabot[bot]", ""}) {
            reset(response);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
            when(request.getParameter("login")).thenReturn(login);

            servlet.doPost(request, response);

            verify(response).setStatus(400);
        }
        verify(repository, never()).save(anyLong(), anyString(), anyString(), any());
    }

    @Test
    public void invalidIdentityKeyOrGroupIsRejected() throws Exception {
        for (String key : new String[]{null, "joaolima", "login:x", "name: "}) {
            reset(response);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
            when(request.getParameter("identityKey")).thenReturn(key);

            servlet.doPost(request, response);

            verify(response).setStatus(400);
        }
        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
        when(request.getParameter("identityKey")).thenReturn("name:joaolima");
        when(request.getParameter("groupId")).thenReturn("abc");
        servlet.doPost(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(repository);
    }

    @Test
    public void adminUnlinksAnIdentity() throws Exception {
        when(repository.delete(6L, "name:joaolima")).thenReturn(true);

        servlet.doDelete(request, response);

        verify(response).setStatus(200);
        assertTrue(body.toString().contains("\"removed\":true"));
    }

    @Test
    public void unlinkingAnIdentityThatIsNotLinkedIs404() throws Exception {
        when(repository.delete(6L, "name:joaolima")).thenReturn(false);

        servlet.doDelete(request, response);

        verify(response).setStatus(404);
    }
}
