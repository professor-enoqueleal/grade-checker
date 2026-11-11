package br.com.gradechecker.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.junit.Assert.assertEquals;

/** Session lifetimes (openspec capability serverless-session-auth). */
public class SessionConfigTest {

    @Rule
    public EnvironmentVariables environment = new EnvironmentVariables();

    @Test
    public void defaultsAreThirtyMinutesAndEightHours() {
        environment.set(SessionConfig.IDLE_MINUTES, null);
        environment.set(SessionConfig.MAX_HOURS, null);
        assertEquals(30 * 60, SessionConfig.idleSeconds());
        assertEquals(8 * 60 * 60, SessionConfig.maxSeconds());
    }

    @Test
    public void configuredValues() {
        environment.set(SessionConfig.IDLE_MINUTES, "60");
        environment.set(SessionConfig.MAX_HOURS, "4");
        assertEquals(60 * 60, SessionConfig.idleSeconds());
        assertEquals(4 * 60 * 60, SessionConfig.maxSeconds());
    }

    @Test
    public void invalidValuesFallBackToTheDefaults() {
        environment.set(SessionConfig.IDLE_MINUTES, "abc");
        environment.set(SessionConfig.MAX_HOURS, "24");      // longer than the GitHub token
        assertEquals(30 * 60, SessionConfig.idleSeconds());
        assertEquals(8 * 60 * 60, SessionConfig.maxSeconds());
        environment.set(SessionConfig.IDLE_MINUTES, "1");    // below 5 minutes
        assertEquals(30 * 60, SessionConfig.idleSeconds());
    }

    @Test
    public void idleIsNeverLongerThanTheMaximum() {
        environment.set(SessionConfig.IDLE_MINUTES, "480");
        environment.set(SessionConfig.MAX_HOURS, "2");
        assertEquals(2 * 60 * 60, SessionConfig.idleSeconds());
    }
}
