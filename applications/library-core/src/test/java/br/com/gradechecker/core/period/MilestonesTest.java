package br.com.gradechecker.core.period;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Properties;

import static org.junit.Assert.*;

public class MilestonesTest {

    private static Properties semester() {
        Properties p = new Properties();
        p.setProperty("milestone.timezone", "America/Sao_Paulo");
        p.setProperty("milestone.1.start", "2026-08-13");
        p.setProperty("milestone.1.end", "2026-09-17");
        p.setProperty("milestone.2.label", "Marco 2");
        p.setProperty("milestone.2.start", "2026-09-18");
        p.setProperty("milestone.2.end", "2026-10-29");
        p.setProperty("milestone.3.start", "2026-10-30");
        p.setProperty("milestone.3.end", "2026-11-19");
        return p;
    }

    @Test
    public void convertsInclusiveDaysInConfiguredZoneToUtc() {
        Milestones.Milestone m1 = Milestones.of(semester()).get(1).orElseThrow();

        assertTrue(m1.isConfigured());
        assertEquals(Instant.parse("2026-08-13T03:00:00Z"), m1.getStart());
        assertEquals(Instant.parse("2026-09-18T02:59:59Z"), m1.getEnd());
        assertEquals(LocalDate.parse("2026-09-17"), m1.getEndDate());
        assertEquals("m1", m1.getKey());
    }

    @Test
    public void labelDefaultsAndCanBeConfigured() {
        Milestones milestones = Milestones.of(semester());

        assertEquals("Milestone 1", milestones.get(1).orElseThrow().getLabel());
        assertEquals("Marco 2", milestones.get(2).orElseThrow().getLabel());
    }

    @Test
    public void startAfterEndIsNotConfiguredButOthersStillWork() {
        Properties p = semester();
        p.setProperty("milestone.2.start", "2026-10-29");
        p.setProperty("milestone.2.end", "2026-09-18");

        Milestones milestones = Milestones.of(p);

        assertFalse(milestones.get(2).orElseThrow().isConfigured());
        assertTrue(milestones.get(1).orElseThrow().isConfigured());
        assertTrue(milestones.get(3).orElseThrow().isConfigured());
    }

    @Test
    public void invalidOrMissingDateIsNotConfigured() {
        Properties p = semester();
        p.setProperty("milestone.1.start", "13/08/2026");
        p.remove("milestone.3.end");

        Milestones milestones = Milestones.of(p);

        assertFalse(milestones.get(1).orElseThrow().isConfigured());
        assertFalse(milestones.get(3).orElseThrow().isConfigured());
    }

    @Test
    public void missingFileLeavesAllMilestonesNotConfigured() {
        Milestones milestones = Milestones.load("/does-not-exist.properties");

        assertEquals(Milestones.COUNT, milestones.getAll().size());
        assertTrue(milestones.getAll().stream().noneMatch(Milestones.Milestone::isConfigured));
        assertEquals(Milestones.DEFAULT_ZONE, milestones.getZone());
    }

    @Test
    public void invalidZoneFallsBackToDefault() {
        Properties p = semester();
        p.setProperty("milestone.timezone", "Mars/Olympus");

        assertEquals(ZoneId.of("America/Sao_Paulo"), Milestones.of(p).getZone());
    }

    // the bundled .properties files belong to each application: tested there (BundledConfigurationTest)
}
