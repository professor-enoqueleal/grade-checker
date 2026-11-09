package br.com.gradechecker.core.grading;

import br.com.gradechecker.core.internal.Log;
import br.com.gradechecker.core.internal.Resources;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Parameters of the milestone grade (openspec capability milestone-grading), from /grading.properties. */
public final class GradingConfig {

    private static final Log logger = Log.of(GradingConfig.class);

    private final double maxPoints;
    private final double tolerance;
    private final double weightEffectiveLines;
    private final double weightActiveDays;
    private final double weightRelevantCommits;
    private final int lineCap;
    private final int reorganizationMinFiles;
    private final double reorganizationBalance;
    private final double reorganizationFactor;
    private final int relevantCommitMinLines;
    private final Set<String> excludedLogins;

    private GradingConfig(Properties p) {
        maxPoints = positive(p, "grading.milestone.maxPoints", 3);
        tolerance = range(p, "grading.tolerance", 0.8, 0.01, 1);
        lineCap = (int) positive(p, "grading.commit.lineCap", 400);
        reorganizationMinFiles = (int) positive(p, "grading.reorganization.minFiles", 30);
        reorganizationBalance = range(p, "grading.reorganization.balance", 0.6, 0, 1);
        reorganizationFactor = range(p, "grading.reorganization.factor", 0.1, 0, 1);
        relevantCommitMinLines = (int) range(p, "grading.relevantCommit.minLines", 20, 0, Integer.MAX_VALUE);

        double[] w = {
                range(p, "grading.weight.effectiveLines", 0.5, 0, Double.MAX_VALUE),
                range(p, "grading.weight.activeDays", 0.3, 0, Double.MAX_VALUE),
                range(p, "grading.weight.relevantCommits", 0.2, 0, Double.MAX_VALUE)};
        double sum = w[0] + w[1] + w[2];
        if (sum <= 0) {
            logger.warn("grading weights are all zero: using 0.5/0.3/0.2");
            w = new double[]{0.5, 0.3, 0.2};
            sum = 1;
        }
        weightEffectiveLines = w[0] / sum;
        weightActiveDays = w[1] / sum;
        weightRelevantCommits = w[2] / sum;

        Set<String> excluded = new HashSet<>();
        String list = p.getProperty("grading.excludedLogins", "");
        for (String login : list.split(",")) {
            if (!login.trim().isEmpty()) excluded.add(login.trim().toLowerCase(Locale.ROOT));
        }
        excludedLogins = Collections.unmodifiableSet(excluded);
    }

    public static GradingConfig of(Properties properties) {
        return new GradingConfig(properties);
    }

    /** Defaults of the specification (no file). */
    public static GradingConfig defaults() {
        return new GradingConfig(new Properties());
    }

    public static GradingConfig fromClasspath() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final GradingConfig INSTANCE = load("/grading.properties");
    }

    static GradingConfig load(String resource) {
        Properties properties = new Properties();
        try (InputStream in = Resources.open(resource, GradingConfig.class)) {
            if (in == null) {
                logger.warn("{} not found on classpath: using default grading parameters", resource);
            } else {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.warn("Failed to read {}: using default grading parameters. Cause: {}", resource, e.getMessage());
        }
        return of(properties);
    }

    private static double positive(Properties p, String key, double def) {
        return range(p, key, def, Double.MIN_VALUE, Double.MAX_VALUE);
    }

    private static double range(Properties p, String key, double def, double min, double max) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) return def;
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed < min || parsed > max || Double.isNaN(parsed)) throw new NumberFormatException("out of range");
            return parsed;
        } catch (NumberFormatException e) {
            logger.warn("Invalid {}='{}': using {}", key, value, def);
            return def;
        }
    }

    /** Lines of a commit counted in the index: reorganization discount, then cap. */
    public double effectiveLines(int additions, int deletions, int changedFiles) {
        double lines = additions + deletions;
        boolean reorganization = changedFiles >= reorganizationMinFiles
                && Math.min(additions, deletions) >= reorganizationBalance * Math.max(additions, deletions)
                && lines > 0;
        if (reorganization) lines *= reorganizationFactor;
        return Math.min(lines, lineCap);
    }

    public boolean isRelevantCommit(int additions, int deletions) {
        return additions + deletions >= relevantCommitMinLines;
    }

    /** Bots and configured logins (e.g. the professor) are never students. */
    public boolean isStudentLogin(String login) {
        if (login == null || login.trim().isEmpty()) return false;
        String l = login.trim().toLowerCase(Locale.ROOT);
        return !l.endsWith("[bot]") && !excludedLogins.contains(l);
    }

    public double getMaxPoints() { return maxPoints; }
    public double getTolerance() { return tolerance; }
    public double getWeightEffectiveLines() { return weightEffectiveLines; }
    public double getWeightActiveDays() { return weightActiveDays; }
    public double getWeightRelevantCommits() { return weightRelevantCommits; }
    public int getLineCap() { return lineCap; }
    public int getRelevantCommitMinLines() { return relevantCommitMinLines; }
    public Set<String> getExcludedLogins() { return excludedLogins; }
    public int getReorganizationMinFiles() { return reorganizationMinFiles; }
    public double getReorganizationBalance() { return reorganizationBalance; }
    public double getReorganizationFactor() { return reorganizationFactor; }

}
