package br.com.gradechecker.service;

import br.com.gradechecker.config.Milestones;
import br.com.gradechecker.config.Milestones.Milestone;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the "period" request parameter (m1, m2, m3, all, custom) into since/until instants.
 * Single place where milestone dates and the configured time zone are applied
 * (openspec/changes/filtro-por-marcos).
 */
public class PeriodResolver {

    public static final String ALL = "all";
    public static final String CUSTOM = "custom";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US);
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final Milestones milestones;
    private final Clock clock;

    public PeriodResolver() {
        this(Milestones.fromClasspath(), Clock.systemUTC());
    }

    public PeriodResolver(Milestones milestones, Clock clock) {
        this.milestones = milestones;
        this.clock = clock;
    }

    public ZoneId getZone() {
        return milestones.getZone();
    }

    /**
     * Resolves the period. When the requested period cannot be used (milestone not configured or in the
     * future, invalid custom dates) the result has {@link ResolvedPeriod#getError()} set; callers decide
     * whether to reject the request or fall back to {@link #all()}.
     */
    public ResolvedPeriod resolve(String period, String dateSince, String dateUntil) {

        String key = period == null ? ALL : period.trim().toLowerCase(Locale.ROOT);

        if (key.matches("m[1-9]")) {
            return milestone(Integer.parseInt(key.substring(1)));
        }

        if (CUSTOM.equals(key)) {
            return custom(dateSince, dateUntil);
        }

        return all();
    }

    public ResolvedPeriod all() {
        return new ResolvedPeriod(ALL, null, ceilToMinute(clock.instant()), null);
    }

    private ResolvedPeriod milestone(int number) {

        Milestone m = milestones.get(number).orElse(null);
        String key = "m" + number;

        if (m == null || !m.isConfigured()) {
            String label = m == null ? "Milestone " + number : m.getLabel();
            return new ResolvedPeriod(key, null, null, label + " is not configured.");
        }

        if (m.getStart().isAfter(clock.instant())) {
            return new ResolvedPeriod(key, null, null, m.getLabel() + " starts on " + DAY.format(m.getStartDate()) + ".");
        }

        return new ResolvedPeriod(key, m.getStart(), m.getEnd(), null);
    }

    private ResolvedPeriod custom(String dateSince, String dateUntil) {

        try {

            Instant since = isBlank(dateSince) ? null : parse(dateSince);
            Instant until = isBlank(dateUntil) ? ceilToMinute(clock.instant()) : parse(dateUntil);

            if (since != null && since.isAfter(until)) {
                return new ResolvedPeriod(CUSTOM, null, null, "The start date must be before the end date.");
            }

            return new ResolvedPeriod(CUSTOM, since, until, null);

        } catch (DateTimeParseException e) {
            return new ResolvedPeriod(CUSTOM, null, null, "Invalid custom date. Use the date pickers to select the period.");
        }
    }

    /** Accepts ISO instants/offsets ("…Z", "…-03:00") or local values from datetime-local inputs (zone = configured). */
    private Instant parse(String value) {

        String v = value.trim();

        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(v).atZone(getZone()).toInstant();
        }
    }

    /** Buttons shown on the pages: milestones (enabled only when configured and already started) and All. */
    public List<PeriodOption> options(String selectedKey) {

        List<PeriodOption> options = new ArrayList<>();
        Instant now = clock.instant();

        for (Milestone m : milestones.getAll()) {

            String hint;
            boolean enabled;

            if (!m.isConfigured()) {
                enabled = false;
                hint = "Not configured";
            } else if (m.getStart().isAfter(now)) {
                enabled = false;
                hint = "Starts on " + DAY.format(m.getStartDate());
            } else {
                enabled = true;
                hint = DAY.format(m.getStartDate()) + " – " + DAY.format(m.getEndDate());
            }

            options.add(new PeriodOption(m.getKey(), m.getLabel(), enabled, hint, m.getKey().equals(selectedKey)));
        }

        options.add(new PeriodOption(ALL, "All", true, "All history", ALL.equals(selectedKey)));

        return options;
    }

    /** Human readable period in the configured zone, e.g. "Aug 13, 2026 – Sep 17, 2026" or "All history". */
    public String describe(ResolvedPeriod period) {

        if (period.getError() != null) return "";

        if (period.getSince() == null) return "All history";

        if (period.getKey().startsWith("m")) {
            return DAY.format(period.getSince().atZone(getZone())) + " – " + DAY.format(period.getUntil().atZone(getZone()));
        }

        return DAY_TIME.format(period.getSince().atZone(getZone())) + " – " + DAY_TIME.format(period.getUntil().atZone(getZone()));
    }

    /** Value for a datetime-local input (yyyy-MM-ddTHH:mm) in the configured zone; empty when null. */
    public String toInputValue(Instant instant) {
        return instant == null ? "" : INPUT.format(instant.atZone(getZone()));
    }

    /** Formats an ISO date coming from GitHub, e.g. "Mar 1, 2026 11:05"; returns the input if unparseable. */
    public String formatDateTime(String iso) {

        if (isBlank(iso)) return "";

        try {
            return DAY_TIME.format(OffsetDateTime.parse(iso).atZoneSameInstant(getZone()));
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    /** Rounded up to the next minute: stable cache key within the minute, no recent commit left out. */
    public static Instant ceilToMinute(Instant now) {

        Instant truncated = now.truncatedTo(ChronoUnit.MINUTES);

        return truncated.equals(now) ? truncated : truncated.plus(1, ChronoUnit.MINUTES);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class ResolvedPeriod {

        private final String key;
        private final Instant since;
        private final Instant until;
        private final String error;

        public ResolvedPeriod(String key, Instant since, Instant until, String error) {
            this.key = key;
            this.since = since;
            this.until = until;
            this.error = error;
        }

        public String getKey() { return key; }
        /** Null means "from the beginning of the history". */
        public Instant getSince() { return since; }
        public Instant getUntil() { return until; }
        public String getError() { return error; }
        public String getSinceIso() { return since == null ? null : since.toString(); }
        public String getUntilIso() { return until == null ? null : until.toString(); }
    }

    public static final class PeriodOption {

        private final String key;
        private final String label;
        private final boolean enabled;
        private final String hint;
        private final boolean selected;

        public PeriodOption(String key, String label, boolean enabled, String hint, boolean selected) {
            this.key = key;
            this.label = label;
            this.enabled = enabled;
            this.hint = hint;
            this.selected = selected;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public boolean isEnabled() { return enabled; }
        public String getHint() { return hint; }
        public boolean isSelected() { return selected; }
    }

}
