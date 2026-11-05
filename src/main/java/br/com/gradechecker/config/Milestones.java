package br.com.gradechecker.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Evaluation milestones loaded from /milestones.properties (same pattern as queries.properties).
 * Invalid milestones are kept as "not configured" so that a typo never takes the application down.
 */
public final class Milestones {

    public static final int COUNT = 3;
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Logger logger = LogManager.getLogger(Milestones.class);

    private final ZoneId zone;
    private final List<Milestone> milestones;

    private Milestones(ZoneId zone, List<Milestone> milestones) {
        this.zone = zone;
        this.milestones = Collections.unmodifiableList(milestones);
    }

    /** Lazily loaded, application-wide instance. */
    public static Milestones fromClasspath() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final Milestones INSTANCE = load("/milestones.properties");
    }

    static Milestones load(String resource) {

        Properties properties = new Properties();

        try (InputStream in = Milestones.class.getResourceAsStream(resource)) {
            if (in == null) {
                logger.warn("{} not found on classpath: no milestone is configured", resource);
            } else {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.warn("Failed to read {}: no milestone is configured. Cause: {}", resource, e.getMessage());
        }

        return of(properties);
    }

    public static Milestones of(Properties properties) {

        ZoneId zone = DEFAULT_ZONE;
        String zoneValue = trim(properties.getProperty("milestone.timezone"));

        if (zoneValue != null) {
            try {
                zone = ZoneId.of(zoneValue);
            } catch (DateTimeException e) {
                logger.warn("Invalid milestone.timezone '{}', using {}", zoneValue, DEFAULT_ZONE);
            }
        }

        List<Milestone> list = new ArrayList<>();

        for (int n = 1; n <= COUNT; n++) {
            list.add(parse(properties, n, zone));
        }

        return new Milestones(zone, list);
    }

    private static Milestone parse(Properties p, int n, ZoneId zone) {

        String label = trim(p.getProperty("milestone." + n + ".label"));
        if (label == null) label = "Milestone " + n;

        String startValue = trim(p.getProperty("milestone." + n + ".start"));
        String endValue = trim(p.getProperty("milestone." + n + ".end"));

        if (startValue == null || endValue == null) {
            return Milestone.notConfigured(n, label, "Not configured");
        }

        try {

            LocalDate start = LocalDate.parse(startValue);
            LocalDate end = LocalDate.parse(endValue);

            if (start.isAfter(end)) {
                logger.warn("milestone.{} ignored: start {} is after end {}", n, start, end);
                return Milestone.notConfigured(n, label, "Not configured");
            }

            return new Milestone(n, label, start, end,
                    start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1),
                    null);

        } catch (DateTimeException e) {
            logger.warn("milestone.{} ignored: invalid date ({}/{}): {}", n, startValue, endValue, e.getMessage());
            return Milestone.notConfigured(n, label, "Not configured");
        }
    }

    private static String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public ZoneId getZone() {
        return zone;
    }

    public List<Milestone> getAll() {
        return milestones;
    }

    public Optional<Milestone> get(int number) {
        return number >= 1 && number <= milestones.size() ? Optional.of(milestones.get(number - 1)) : Optional.empty();
    }

    public static final class Milestone {

        private final int number;
        private final String label;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final Instant start;
        private final Instant end;
        private final String problem;

        Milestone(int number, String label, LocalDate startDate, LocalDate endDate, Instant start, Instant end, String problem) {
            this.number = number;
            this.label = label;
            this.startDate = startDate;
            this.endDate = endDate;
            this.start = start;
            this.end = end;
            this.problem = problem;
        }

        static Milestone notConfigured(int number, String label, String problem) {
            return new Milestone(number, label, null, null, null, null, problem);
        }

        public int getNumber() { return number; }
        public String getKey() { return "m" + number; }
        public String getLabel() { return label; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        /** First instant of the start day in the configured zone. */
        public Instant getStart() { return start; }
        /** Last second (23:59:59) of the end day in the configured zone. */
        public Instant getEnd() { return end; }
        public boolean isConfigured() { return problem == null; }
    }

}
