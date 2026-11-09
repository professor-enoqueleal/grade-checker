package br.com.gradechecker.core.grading;

import br.com.gradechecker.core.grading.GradeCalculator;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.period.Milestones;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Values of the "Como as notas são calculadas" box of the group page (openspec capability milestone-grading),
 * taken from the configuration in use (grading.properties and milestones.properties) and formatted in pt-BR:
 * the box is written in Portuguese for the students (exception to the English interface).
 */
public final class GradingExplanation {

    /** Group size of the worked example. */
    static final int EXAMPLE_STUDENTS = 4;

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** One milestone line: "Milestone 1: de 13/08/2026 a 17/09/2026". */
    public static final class MilestoneRow {
        private final String label;
        private final String start;
        private final String end;

        MilestoneRow(String label, String start, String end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }

        public String getLabel() { return label; }
        public String getStart() { return start; }
        public String getEnd() { return end; }
    }

    private final List<MilestoneRow> milestones;
    private final String maxPoints;
    private final String totalPoints;
    private final String weightEffectiveLines;
    private final String weightActiveDays;
    private final String weightRelevantCommits;
    private final int lineCap;
    private final int reorganizationMinFiles;
    private final String reorganizationBalance;
    private final String reorganizationFactor;
    private final int relevantCommitMinLines;
    private final String tolerance;
    private final String timeZone;
    private final boolean excludesLogins;
    private final int exampleStudents;
    private final String exampleFairShare;
    private final String exampleFullShare;
    private final String exampleFullGrade;
    private final String exampleLowShare;
    private final String exampleLowGrade;

    private GradingExplanation(GradingConfig grading, Milestones config) {

        List<MilestoneRow> rows = new ArrayList<>();
        for (Milestones.Milestone m : config.getAll()) {
            rows.add(new MilestoneRow(m.getLabel(), DATE.format(m.getStartDate()), DATE.format(m.getEndDate())));
        }
        milestones = Collections.unmodifiableList(rows);

        double max = grading.getMaxPoints();
        int milestoneCount = Math.max(rows.size(), 1);
        maxPoints = number(max);
        totalPoints = number(max * milestoneCount);

        weightEffectiveLines = percent(grading.getWeightEffectiveLines());
        weightActiveDays = percent(grading.getWeightActiveDays());
        weightRelevantCommits = percent(grading.getWeightRelevantCommits());
        lineCap = grading.getLineCap();
        reorganizationMinFiles = grading.getReorganizationMinFiles();
        reorganizationBalance = percent(grading.getReorganizationBalance());
        reorganizationFactor = percent(grading.getReorganizationFactor());
        relevantCommitMinLines = grading.getRelevantCommitMinLines();
        tolerance = percent(grading.getTolerance());
        timeZone = zoneName(config.getZone());
        excludesLogins = !grading.getExcludedLogins().isEmpty();

        // worked example, with the same formula as GradeCalculator: grade = max x min(1, (share / fairShare) / tolerance)
        exampleStudents = EXAMPLE_STUDENTS;
        double fairShare = 1.0 / EXAMPLE_STUDENTS;
        double fullShare = fairShare * grading.getTolerance();   // from this share on, the grade is the maximum
        double lowShare = fullShare / 2;                          // half of it: half of the maximum
        exampleFairShare = percent(fairShare);
        exampleFullShare = percent(fullShare);
        exampleFullGrade = grade(gradeOf(grading, fullShare, fairShare));
        exampleLowShare = percent(lowShare);
        exampleLowGrade = grade(gradeOf(grading, lowShare, fairShare));
    }

    public static GradingExplanation of(GradingConfig grading, Milestones milestones) {
        return new GradingExplanation(grading, milestones);
    }

    static double gradeOf(GradingConfig grading, double share, double fairShare) {
        return GradeCalculator.round(grading.getMaxPoints() * Math.min(1, (share / fairShare) / grading.getTolerance()));
    }

    private static String zoneName(ZoneId zone) {
        return "America/Sao_Paulo".equals(zone.getId()) ? "horário de Brasília" : "fuso " + zone.getId();
    }

    /** 3 -> "3"; 2.5 -> "2,5". */
    private static String number(double value) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(2);
        f.setGroupingUsed(false);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(value);
    }

    /** 0.5 -> "50%"; 1/3 -> "33,3%". */
    private static String percent(double value) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(1);
        f.setGroupingUsed(false);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(value * 100) + "%";
    }

    /** 1.5 -> "1,50". */
    private static String grade(double value) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        f.setGroupingUsed(false);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(value);
    }

    public List<MilestoneRow> getMilestones() { return milestones; }
    public String getMaxPoints() { return maxPoints; }
    public String getTotalPoints() { return totalPoints; }
    public String getWeightEffectiveLines() { return weightEffectiveLines; }
    public String getWeightActiveDays() { return weightActiveDays; }
    public String getWeightRelevantCommits() { return weightRelevantCommits; }
    public int getLineCap() { return lineCap; }
    public int getReorganizationMinFiles() { return reorganizationMinFiles; }
    public String getReorganizationBalance() { return reorganizationBalance; }
    public String getReorganizationFactor() { return reorganizationFactor; }
    public int getRelevantCommitMinLines() { return relevantCommitMinLines; }
    public String getTolerance() { return tolerance; }
    public String getTimeZone() { return timeZone; }
    public boolean isExcludesLogins() { return excludesLogins; }
    public int getExampleStudents() { return exampleStudents; }
    public String getExampleFairShare() { return exampleFairShare; }
    public String getExampleFullShare() { return exampleFullShare; }
    public String getExampleFullGrade() { return exampleFullGrade; }
    public String getExampleLowShare() { return exampleLowShare; }
    public String getExampleLowGrade() { return exampleLowGrade; }

}
