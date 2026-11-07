package br.com.gradechecker.config;

import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** openspec capability platform-admin. */
public class AdminConfigTest {

    @Rule
    public EnvironmentVariables environment = new EnvironmentVariables();

    @After
    public void clear() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    private static HttpSession session(Object login) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn(login);
        return session;
    }

    @Test
    public void listedLoginIsAdminIgnoringCaseAndSpaces() {
        environment.set(AdminConfig.ADMINS, " professor-enoqueleal , outro ");

        assertTrue(AdminConfig.isAdmin("Professor-EnoqueLeal"));
        assertTrue(AdminConfig.isAdmin(session("outro")));
        assertFalse(AdminConfig.isAdmin("alice"));
    }

    @Test
    public void withoutTheVariableNobodyIsAdmin() {
        environment.set(AdminConfig.ADMINS, null);

        assertTrue(AdminConfig.admins().isEmpty());
        assertFalse(AdminConfig.isAdmin("professor-enoqueleal"));
    }

    @Test
    public void blankListMeansNobody() {
        environment.set(AdminConfig.ADMINS, " , ");

        assertTrue(AdminConfig.admins().isEmpty());
        assertFalse(AdminConfig.isAdmin(""));
    }

    @Test
    public void systemPropertyWinsOverEnvironment() {
        environment.set(AdminConfig.ADMINS, "alice");
        System.setProperty(AdminConfig.ADMINS, "bob");

        assertTrue(AdminConfig.isAdmin("bob"));
        assertFalse(AdminConfig.isAdmin("alice"));
    }

    @Test
    public void sessionWithoutUserLoginIsNotAdmin() {
        environment.set(AdminConfig.ADMINS, "alice");

        assertFalse(AdminConfig.isAdmin(session(null)));
        assertFalse(AdminConfig.isAdmin((HttpSession) null));
    }
}
