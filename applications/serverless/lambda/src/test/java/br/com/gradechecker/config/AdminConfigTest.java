package br.com.gradechecker.config;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.junit.Assert.*;

/** Copied from the Jetty version (capability platform-admin) without the HttpSession tests: capability serverless-session-auth. */
public class AdminConfigTest {

    @Rule
    public EnvironmentVariables environment = new EnvironmentVariables();

    @After
    public void clear() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    @Test
    public void listedLoginIsAdminIgnoringCaseAndSpaces() {
        environment.set(AdminConfig.ADMINS, " professor-enoqueleal , outro ");

        assertTrue(AdminConfig.isAdmin("Professor-EnoqueLeal"));
        assertTrue(AdminConfig.isAdmin("outro"));
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
    public void missingLoginIsNotAdmin() {
        environment.set(AdminConfig.ADMINS, "professor-enoqueleal");

        assertFalse(AdminConfig.isAdmin((String) null));
        assertFalse(AdminConfig.isAdmin("  "));
    }

}
