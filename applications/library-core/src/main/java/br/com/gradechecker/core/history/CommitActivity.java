package br.com.gradechecker.core.history;

import br.com.gradechecker.core.period.Milestones;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Commit frequency of one author over the period of the history page (openspec capability commit-history-page):
 * one column per day (up to {@link #MAX_DAILY_DAYS} days) or per ISO week, stacked by the milestone each commit fell
 * in, in the time zone of the milestones. Built on the server from the same commits as the table; the page only draws.
 */
public final class CommitActivity {

    /** Longest interval drawn per day; longer intervals are drawn per week. */
    public static final int MAX_DAILY_DAYS = 62;

    static final String OUTSIDE = "outside";

    private static final DateTimeFormatter AXIS = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    /** Commits of one milestone (or outside the milestones) per column. */
    public static final class Series {
        private final String key;
        private final String label;
        private final List<Integer> counts;

        Series(String key, String label, List<Integer> counts) {
            this.key = key;
            this.label = label;
            this.counts = counts;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public List<Integer> getCounts() { return counts; }
    }

    private final String granularity;     // "day" or "week"
    private final String title;
    private final List<String> labels;    // axis: "Oct 21"
    private final List<String> tooltips;  // "Tue, Oct 21, 2026" or "Week of Oct 20, 2026"
    private final List<String> starts;    // ISO date of each column (day, or Monday of the week)
    private final List<Series> series;    // only the series with commits, in milestone order, "outside" last
    private final int total;
    private final int activeDays;
    private final String summary;

    private CommitActivity(String granularity, List<String> labels, List<String> tooltips, List<String> starts,
                           List<Series> series, int total, int activeDays) {
        this.granularity = granularity;
        this.title = "day".equals(granularity) ? "Commits per day" : "Commits per week";
        this.labels = labels;
        this.tooltips = tooltips;
        this.starts = starts;
        this.series = series;
        this.total = total;
        this.activeDays = activeDays;
        this.summary = total + (total == 1 ? " commit" : " commits") + " · " + activeDays
                + (activeDays == 1 ? " active day" : " active days");
    }

    /**
     * @param commits commit instants of the table (already filtered: author, period, no merges)
     * @param since   start of the period, or null (All: the chart starts at the first commit)
     * @param until   end of the period (limited to today)
     * @return null when there is no commit (no chart)
     */
    public static CommitActivity of(Collection<Instant> commits, Instant since, Instant until, Milestones milestones, Clock clock) {

        if (commits == null || commits.isEmpty()) return null;

        ZoneId zone = milestones.getZone();
        List<LocalDate> dates = new ArrayList<>();
        for (Instant commit : commits) dates.add(commit.atZone(zone).toLocalDate());

        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = dates.stream().min(LocalDate::compareTo).get();
        LocalDate last = dates.stream().max(LocalDate::compareTo).get();
        LocalDate start = since == null ? first : since.atZone(zone).toLocalDate();
        LocalDate end = until == null ? today : until.atZone(zone).toLocalDate();
        if (end.isAfter(today)) end = today;
        // the listed commits are inside the period; widen only if the clock disagrees, so the sum always equals the total
        if (first.isBefore(start)) start = first;
        if (last.isAfter(end)) end = last;

        boolean daily = ChronoUnit.DAYS.between(start, end) + 1 <= MAX_DAILY_DAYS;
        LocalDate firstColumn = daily ? start : start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastColumn = daily ? end : end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int columns = (int) ChronoUnit.DAYS.between(firstColumn, lastColumn) / (daily ? 1 : 7) + 1;

        List<String> labels = new ArrayList<>();
        List<String> tooltips = new ArrayList<>();
        List<String> starts = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            LocalDate column = daily ? firstColumn.plusDays(i) : firstColumn.plusWeeks(i);
            labels.add(AXIS.format(column));
            tooltips.add(daily ? DAY.format(column) : "Week of " + WEEK.format(column));
            starts.add(column.toString());
        }

        // counts per series (milestone of each commit), in milestone order, "outside" last
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> seriesLabels = new LinkedHashMap<>();
        for (Milestones.Milestone m : milestones.getAll()) {
            counts.put(m.getKey(), new int[columns]);
            seriesLabels.put(m.getKey(), "M" + m.getNumber());
        }
        counts.put(OUTSIDE, new int[columns]);
        seriesLabels.put(OUTSIDE, "Outside milestones");

        Set<LocalDate> active = new HashSet<>();
        for (LocalDate date : dates) {
            active.add(date);
            int column = daily ? (int) ChronoUnit.DAYS.between(firstColumn, date)
                    : (int) ChronoUnit.DAYS.between(firstColumn, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) / 7;
            counts.get(milestoneOf(date, milestones))[column]++;
        }

        List<Series> series = new ArrayList<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            List<Integer> values = new ArrayList<>();
            int sum = 0;
            for (int v : e.getValue()) {
                values.add(v);
                sum += v;
            }
            if (sum > 0) series.add(new Series(e.getKey(), seriesLabels.get(e.getKey()), values));
        }

        return new CommitActivity(daily ? "day" : "week", labels, tooltips, starts, series, dates.size(), active.size());
    }

    private static String milestoneOf(LocalDate date, Milestones milestones) {
        for (Milestones.Milestone m : milestones.getAll()) {
            if (!date.isBefore(m.getStartDate()) && !date.isAfter(m.getEndDate())) return m.getKey();
        }
        return OUTSIDE;
    }

    public String getGranularity() { return granularity; }
    public String getTitle() { return title; }
    public List<String> getLabels() { return labels; }
    public List<String> getTooltips() { return tooltips; }
    public List<String> getStarts() { return starts; }
    public List<Series> getSeries() { return series; }
    public int getTotal() { return total; }
    public int getActiveDays() { return activeDays; }
    public String getSummary() { return summary; }
    /** Text of the chart for screen readers: "12 commits on 7 active days, per day". */
    public String getAriaLabel() {
        return total + (total == 1 ? " commit" : " commits") + " on " + activeDays
                + (activeDays == 1 ? " active day" : " active days") + ", per " + granularity;
    }

}
