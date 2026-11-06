package br.com.gradechecker.service;

import br.com.gradechecker.config.GradingConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Milestone grade (openspec capability milestone-grading): pure calculation, no I/O.
 * grade = maxPoints x min(1, (indexShare / fairShare) / tolerance), fairShare = 1 / students.
 */
public class GradeCalculator {

    /** Indicators of one author in the milestone period. */
    public static final class Indicators {
        final double effectiveLines;
        final int activeDays;
        final int relevantCommits;
        final int commits;
        final int lines;

        public Indicators(double effectiveLines, int activeDays, int relevantCommits, int commits, int lines) {
            this.effectiveLines = effectiveLines;
            this.activeDays = activeDays;
            this.relevantCommits = relevantCommits;
            this.commits = commits;
            this.lines = lines;
        }

        static final Indicators ZERO = new Indicators(0, 0, 0, 0, 0);
    }

    /** Grade of one student, with the details shown in the dashboard. */
    public static final class StudentGrade {
        private final String login;
        private final double grade;
        private final double effectiveLines;
        private final int activeDays;
        private final int relevantCommits;
        private final double indexShare;
        private final double ratio;

        StudentGrade(String login, double grade, double effectiveLines, int activeDays, int relevantCommits, double indexShare, double ratio) {
            this.login = login;
            this.grade = grade;
            this.effectiveLines = effectiveLines;
            this.activeDays = activeDays;
            this.relevantCommits = relevantCommits;
            this.indexShare = indexShare;
            this.ratio = ratio;
        }

        public String getLogin() { return login; }
        public double getGrade() { return grade; }
        public double getEffectiveLines() { return effectiveLines; }
        public int getActiveDays() { return activeDays; }
        public int getRelevantCommits() { return relevantCommits; }
        public double getIndexShare() { return indexShare; }
        public double getRatio() { return ratio; }
    }

    /** Grades of a group in a milestone. */
    public static final class Result {
        private final Map<String, StudentGrade> byLogin;   // key: login in lower case
        private final double fairShare;
        private final int excludedCommits;
        private final int excludedLines;

        Result(Map<String, StudentGrade> byLogin, double fairShare, int excludedCommits, int excludedLines) {
            this.byLogin = byLogin;
            this.fairShare = fairShare;
            this.excludedCommits = excludedCommits;
            this.excludedLines = excludedLines;
        }

        /** Grade of a student, or null when the author is not a student of the group. */
        public StudentGrade of(String login) {
            return login == null ? null : byLogin.get(login.trim().toLowerCase(Locale.ROOT));
        }

        public Collection<StudentGrade> students() { return byLogin.values(); }
        public double getFairShare() { return fairShare; }
        public int getExcludedCommits() { return excludedCommits; }
        public int getExcludedLines() { return excludedLines; }
    }

    private final GradingConfig config;

    public GradeCalculator(GradingConfig config) {
        this.config = config;
    }

    /**
     * @param indicatorsByAuthor indicators of every author of the period (linked logins and unlinked identities)
     * @param roster             logins of the group members (students before exclusions)
     */
    public Result calculate(Map<String, Indicators> indicatorsByAuthor, Collection<String> roster) {

        // students = roster without bots/excluded logins, case-insensitive, keeping the first spelling
        Map<String, String> students = new LinkedHashMap<>();
        for (String login : roster) {
            if (config.isStudentLogin(login)) students.putIfAbsent(login.trim().toLowerCase(Locale.ROOT), login.trim());
        }

        Map<String, Indicators> byStudent = new LinkedHashMap<>();
        for (String key : students.keySet()) byStudent.put(key, Indicators.ZERO);

        int excludedCommits = 0;
        int excludedLines = 0;
        for (Map.Entry<String, Indicators> e : indicatorsByAuthor.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
            if (students.containsKey(key)) {
                byStudent.put(key, e.getValue());
            } else {
                excludedCommits += e.getValue().commits;
                excludedLines += e.getValue().lines;
            }
        }

        if (students.isEmpty()) {
            return new Result(Collections.emptyMap(), 0, excludedCommits, excludedLines);
        }

        double totalLines = 0;
        double totalDays = 0;
        double totalRelevant = 0;
        for (Indicators i : byStudent.values()) {
            totalLines += i.effectiveLines;
            totalDays += i.activeDays;
            totalRelevant += i.relevantCommits;
        }

        double fairShare = 1.0 / students.size();
        Map<String, StudentGrade> grades = new LinkedHashMap<>();

        for (Map.Entry<String, Indicators> e : byStudent.entrySet()) {
            Indicators i = e.getValue();
            double share = config.getWeightEffectiveLines() * part(i.effectiveLines, totalLines)
                    + config.getWeightActiveDays() * part(i.activeDays, totalDays)
                    + config.getWeightRelevantCommits() * part(i.relevantCommits, totalRelevant);
            double ratio = share / fairShare;
            double grade = config.getMaxPoints() * Math.min(1, ratio / config.getTolerance());
            grades.put(e.getKey(), new StudentGrade(students.get(e.getKey()), round(grade), round(i.effectiveLines),
                    i.activeDays, i.relevantCommits, round4(share), round(ratio)));
        }

        return new Result(grades, fairShare, excludedCommits, excludedLines);
    }

    private static double part(double value, double total) {
        return total <= 0 ? 0 : value / total;
    }

    public static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

}
