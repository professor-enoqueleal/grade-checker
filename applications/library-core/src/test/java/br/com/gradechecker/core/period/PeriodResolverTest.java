package br.com.gradechecker.core.period;

import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver.PeriodOption;
import br.com.gradechecker.core.period.PeriodResolver.ResolvedPeriod;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class PeriodResolverTest {

    /** 2026-11-05 15:00:10 UTC: M1 and M2 are over, M3 is in progress. */
    private static final Instant NOW = Instant.parse("2026-11-05T15:00:10Z");

    private static Properties semester() {
        Properties p = new Properties();
        p.setProperty("milestone.timezone", "America/Sao_Paulo");
        p.setProperty("milestone.1.start", "2026-08-13");
        p.setProperty("milestone.1.end", "2026-09-17");
        p.setProperty("milestone.2.start", "2026-09-18");
        p.setProperty("milestone.2.end", "2026-10-29");
        p.setProperty("milestone.3.start", "2026-10-30");
        p.setProperty("milestone.3.end", "2026-11-19");
        return p;
    }

    private static PeriodResolver resolver(Properties p, Instant now) {
        return new PeriodResolver(Milestones.of(p), Clock.fixed(now, ZoneOffset.UTC));
    }

    private final PeriodResolver resolver = resolver(semester(), NOW);

    @Test
    public void missingOrUnknownPeriodIsAllWithoutSince() {
        for (String period : new String[]{null, "", "whatever"}) {
            ResolvedPeriod p = resolver.resolve(period, null, null);
            assertEquals("all", p.getKey());
            assertNull(p.getSince());
            assertEquals(Instant.parse("2026-11-05T15:01:00Z"), p.getUntil());
            assertNull(p.getError());
        }
    }

    @Test
    public void milestoneUsesConfiguredDays() {
        ResolvedPeriod p = resolver.resolve("m2", null, null);

        assertEquals("m2", p.getKey());
        assertEquals("2026-09-18T03:00:00Z", p.getSinceIso());
        assertEquals("2026-10-30T02:59:59Z", p.getUntilIso());
        assertEquals("Sep 18, 2026 – Oct 29, 2026", resolver.describe(p));
    }

    @Test
    public void milestoneInProgressIsAllowed() {
        assertNull(resolver.resolve("m3", null, null).getError());
    }

    @Test
    public void futureMilestoneIsAnError() {
        PeriodResolver beforeSemester = resolver(semester(), Instant.parse("2026-08-01T12:00:00Z"));

        ResolvedPeriod p = beforeSemester.resolve("m1", null, null);

        assertEquals("Milestone 1 starts on Aug 13, 2026.", p.getError());
    }

    @Test
    public void notConfiguredMilestoneIsAnError() {
        Properties p = semester();
        p.remove("milestone.2.start");

        assertEquals("Milestone 2 is not configured.", resolver(p, NOW).resolve("m2", null, null).getError());
    }

    @Test
    public void customLocalValuesAreInterpretedInConfiguredZone() {
        ResolvedPeriod p = resolver.resolve("custom", "2026-03-01T08:00", "2026-03-01T18:00");

        assertEquals("2026-03-01T11:00:00Z", p.getSinceIso());
        assertEquals("2026-03-01T21:00:00Z", p.getUntilIso());
    }

    @Test
    public void customValuesWithOffsetAreRespected() {
        ResolvedPeriod p = resolver.resolve("custom", "2026-03-01T00:00:00Z", "2026-06-30T23:59:59Z");

        assertEquals("2026-03-01T00:00:00Z", p.getSinceIso());
        assertEquals("2026-06-30T23:59:59Z", p.getUntilIso());
    }

    @Test
    public void customWithoutSinceIsWholeHistoryAndWithoutUntilIsNow() {
        ResolvedPeriod p = resolver.resolve("custom", "", "");

        assertNull(p.getSince());
        assertEquals(Instant.parse("2026-11-05T15:01:00Z"), p.getUntil());
    }

    @Test
    public void invalidCustomIsAnError() {
        assertNotNull(resolver.resolve("custom", "01/03/2026", null).getError());
        assertNotNull(resolver.resolve("custom", "2026-06-01T00:00", "2026-03-01T00:00").getError());
    }

    @Test
    public void optionsDisableFutureAndNotConfiguredMilestones() {
        Properties p = semester();
        p.remove("milestone.3.end");
        PeriodResolver early = resolver(p, Instant.parse("2026-09-01T12:00:00Z"));

        List<PeriodOption> options = early.options("all");

        assertEquals(4, options.size());
        assertTrue(options.get(0).isEnabled());
        assertFalse(options.get(1).isEnabled());
        assertEquals("Starts on Sep 18, 2026", options.get(1).getHint());
        assertFalse(options.get(2).isEnabled());
        assertEquals("Not configured", options.get(2).getHint());
        assertEquals("all", options.get(3).getKey());
        assertTrue(options.get(3).isSelected());
    }

    @Test
    public void inputValuesAndDatesAreInConfiguredZone() {
        ResolvedPeriod m1 = resolver.resolve("m1", null, null);

        assertEquals("2026-08-13T00:00", resolver.toInputValue(m1.getSince()));
        assertEquals("2026-09-17T23:59", resolver.toInputValue(m1.getUntil()));
        assertEquals("", resolver.toInputValue(null));
        assertEquals("Mar 1, 2026 11:05", resolver.formatDateTime("2026-03-01T14:05:00Z"));
        assertEquals("All history", resolver.describe(resolver.all()));
    }

    @Test
    public void ceilToMinute() {
        assertEquals(Instant.parse("2026-11-05T14:33:00Z"), PeriodResolver.ceilToMinute(Instant.parse("2026-11-05T14:32:10Z")));
        assertEquals(Instant.parse("2026-11-05T14:32:00Z"), PeriodResolver.ceilToMinute(Instant.parse("2026-11-05T14:32:00Z")));
    }
}
