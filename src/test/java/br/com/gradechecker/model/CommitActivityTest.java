package br.com.gradechecker.model;

import br.com.gradechecker.config.Milestones;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/** openspec capability commit-history-page: commit frequency chart (per day or per week, by milestone). */
public class CommitActivityTest {

    private static final Milestones MILESTONES = milestones();
    /** "today" = 2026-11-07 12:00 in São Paulo. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneOffset.UTC);

    private static Milestones milestones() {
        Properties p = new Properties();
        p.setProperty("milestone.timezone", "America/Sao_Paulo");
        p.setProperty("milestone.1.label", "Milestone 1");
        p.setProperty("milestone.1.start", "2026-08-13");
        p.setProperty("milestone.1.end", "2026-09-17");
        p.setProperty("milestone.2.label", "Milestone 2");
        p.setProperty("milestone.2.start", "2026-09-18");
        p.setProperty("milestone.2.end", "2026-10-29");
        p.setProperty("milestone.3.label", "Milestone 3");
        p.setProperty("milestone.3.start", "2026-10-30");
        p.setProperty("milestone.3.end", "2026-11-19");
        return Milestones.of(p);
    }

    /** Local São Paulo date-time (UTC-3) as an instant. */
    private static Instant sp(String localDateTime) {
        return java.time.OffsetDateTime.parse(localDateTime + ":00-03:00").toInstant();
    }

    private static List<Instant> commits(String... localDateTimes) {
        List<Instant> list = new ArrayList<>();
        for (String s : localDateTimes) list.add(sp(s));
        return list;
    }

    private static int sum(CommitActivity a) {
        int total = 0;
        for (CommitActivity.Series s : a.getSeries()) for (int v : s.getCounts()) total += v;
        return total;
    }

    @Test
    public void milestonePerDayWithEveryDayOfTheMilestone() {
        CommitActivity a = CommitActivity.of(commits("2026-10-21T10:00", "2026-10-21T15:00", "2026-10-25T09:00"),
                sp("2026-09-18T00:00"), sp("2026-10-29T23:59"), MILESTONES, CLOCK);

        assertEquals("day", a.getGranularity());
        assertEquals("Commits per day", a.getTitle());
        assertEquals(42, a.getLabels().size());                     // 18/09 .. 29/10
        assertEquals("Sep 18", a.getLabels().get(0));
        assertEquals("Oct 29", a.getLabels().get(41));
        assertEquals("Wed, Oct 21, 2026", a.getTooltips().get(33));
        assertEquals(1, a.getSeries().size());                       // only M2: no legend
        assertEquals("M2", a.getSeries().get(0).getLabel());
        assertEquals(Integer.valueOf(2), a.getSeries().get(0).getCounts().get(33));
        assertEquals(Integer.valueOf(1), a.getSeries().get(0).getCounts().get(37));
        assertEquals(Integer.valueOf(0), a.getSeries().get(0).getCounts().get(0));
        assertEquals(3, a.getTotal());
        assertEquals(3, sum(a));
        assertEquals(2, a.getActiveDays());
        assertEquals("3 commits · 2 active days", a.getSummary());
        assertEquals("3 commits on 2 active days, per day", a.getAriaLabel());
    }

    @Test
    public void milestoneInProgressStopsToday() {
        CommitActivity a = CommitActivity.of(commits("2026-11-02T10:00"), sp("2026-10-30T00:00"), sp("2026-11-19T23:59"), MILESTONES, CLOCK);
        assertEquals(9, a.getLabels().size());                      // 30/10 .. 07/11
        assertEquals("Nov 7", a.getLabels().get(8));
        assertEquals("1 commit · 1 active day", a.getSummary());
    }

    @Test
    public void limitBetweenDayAndWeek() {
        // 62 days: 2026-08-01 .. 2026-10-01
        CommitActivity daily = CommitActivity.of(commits("2026-08-10T10:00"), sp("2026-08-01T00:00"), sp("2026-10-01T23:59"), MILESTONES, CLOCK);
        assertEquals("day", daily.getGranularity());
        assertEquals(62, daily.getLabels().size());
        // 63 days: 2026-08-01 .. 2026-10-02
        CommitActivity weekly = CommitActivity.of(commits("2026-08-10T10:00"), sp("2026-08-01T00:00"), sp("2026-10-02T23:59"), MILESTONES, CLOCK);
        assertEquals("week", weekly.getGranularity());
        assertEquals("Commits per week", weekly.getTitle());
    }

    @Test
    public void allPerWeekFromTheFirstCommitWithAWeekSplitBetweenMilestones() {
        // first commit on Mon 2026-08-03 (outside), 17/09 (Thu, M1) and 18/09 (Fri, M2) in the same week
        CommitActivity a = CommitActivity.of(commits("2026-08-03T10:00", "2026-09-17T10:00", "2026-09-18T10:00", "2026-09-18T11:00"),
                null, Instant.parse("2026-11-07T15:01:00Z"), MILESTONES, CLOCK);

        assertEquals("week", a.getGranularity());
        assertEquals("Aug 3", a.getLabels().get(0));                // Monday of the first commit's week
        assertEquals("Week of Aug 3, 2026", a.getTooltips().get(0));
        assertEquals("Nov 2", a.getLabels().get(a.getLabels().size() - 1));   // week of today (Sat 07/11)
        assertEquals(14, a.getLabels().size());

        List<String> keys = new ArrayList<>();
        for (CommitActivity.Series s : a.getSeries()) keys.add(s.getKey());
        assertEquals(List.of("m1", "m2", "outside"), keys);          // milestone order, outside last; no M3
        int week = a.getStarts().indexOf("2026-09-14");
        assertEquals(Integer.valueOf(1), a.getSeries().get(0).getCounts().get(week));   // M1
        assertEquals(Integer.valueOf(2), a.getSeries().get(1).getCounts().get(week));   // M2
        assertEquals("Outside milestones", a.getSeries().get(2).getLabel());
        assertEquals(4, sum(a));
        assertEquals(3, a.getActiveDays());
    }

    @Test
    public void daysInTheTimeZoneOfTheMilestones() {
        // 20/10 23:30 in São Paulo = 21/10 02:30 UTC: counted on 20/10
        CommitActivity a = CommitActivity.of(Collections.singletonList(Instant.parse("2026-10-21T02:30:00Z")),
                sp("2026-09-18T00:00"), sp("2026-10-29T23:59"), MILESTONES, CLOCK);
        int column = a.getStarts().indexOf("2026-10-20");
        assertEquals(Integer.valueOf(1), a.getSeries().get(0).getCounts().get(column));
    }

    @Test
    public void noCommitsNoChart() {
        assertNull(CommitActivity.of(Collections.emptyList(), sp("2026-09-18T00:00"), sp("2026-10-29T23:59"), MILESTONES, CLOCK));
        assertNull(CommitActivity.of(null, null, null, MILESTONES, CLOCK));
    }
}
