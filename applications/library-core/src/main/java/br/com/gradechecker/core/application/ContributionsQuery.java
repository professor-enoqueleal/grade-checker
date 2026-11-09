package br.com.gradechecker.core.application;

import br.com.gradechecker.core.attribution.ContributionAnalyzer;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.grading.GradeCalculator;
import br.com.gradechecker.core.grading.GradeCalculator.StudentGrade;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.history.ContributionsReport;
import br.com.gradechecker.core.history.ContributorStats;
import br.com.gradechecker.core.history.ContributorStats.ManualIdentity;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.period.PeriodResolver.ResolvedPeriod;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.IdentityAliasRepository;
import br.com.gradechecker.core.port.MemberRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Contributions report of a group in a period (capability core-use-cases): one row per author, with the grades of the
 * milestone or the grades of every started milestone (All); students without commits are listed with zeros, so that
 * a 0.00 grade is visible. The custom period has no grades.
 */
public class ContributionsQuery {

    /** Kind of grades of the report. */
    public enum GradeKind { MILESTONE, ALL }

    /** One author of the report; the grade fields of the other kind are null. */
    public static final class Row {
        private final String author;
        private final int commits;
        private final int totalAdditions;
        private final int totalDeletions;
        private final double percentage;
        private final boolean linked;
        private final List<String> gitIdentities;
        private final String identityKey;
        private final List<ManualIdentity> manualIdentities;
        private StudentGrade grade;
        private MilestoneGrades.StudentGrades milestoneGrades;

        Row(String author, int commits, int totalAdditions, int totalDeletions, double percentage, boolean linked,
            List<String> gitIdentities, String identityKey, List<ManualIdentity> manualIdentities) {
            this.author = author;
            this.commits = commits;
            this.totalAdditions = totalAdditions;
            this.totalDeletions = totalDeletions;
            this.percentage = percentage;
            this.linked = linked;
            this.gitIdentities = gitIdentities;
            this.identityKey = identityKey;
            this.manualIdentities = manualIdentities;
        }

        static Row of(ContributorStats s) {
            return new Row(s.getAuthor(), s.getCommits(), s.getTotalAdditions(), s.getTotalDeletions(), s.getPercentage(),
                    s.isLinked(), s.getGitIdentities(), s.getIdentityKey(), s.getManualIdentities());
        }

        static Row withoutCommits(String login) {
            return new Row(login, 0, 0, 0, 0.0, true, Collections.emptyList(), null, Collections.emptyList());
        }

        public String getAuthor() { return author; }
        public int getCommits() { return commits; }
        public int getTotalAdditions() { return totalAdditions; }
        public int getTotalDeletions() { return totalDeletions; }
        public double getPercentage() { return percentage; }
        public boolean isLinked() { return linked; }
        public List<String> getGitIdentities() { return gitIdentities; }
        public String getIdentityKey() { return identityKey; }
        public List<ManualIdentity> getManualIdentities() { return manualIdentities; }
        /** Grade of the milestone (kind MILESTONE); null for a non-student. */
        public StudentGrade getGrade() { return grade; }
        /** Grades of every started milestone and their sum (kind ALL); null for a non-student. */
        public MilestoneGrades.StudentGrades getMilestoneGrades() { return milestoneGrades; }
    }

    /** Summary of the grades of the report. */
    public static final class Grading {
        private final GradeKind kind;
        private final double maxPoints;
        private final double tolerance;
        private final int students;
        private double fairShare;
        private int excludedCommits;
        private int excludedLines;
        private boolean provisional;
        private List<String> provisionalMilestones = Collections.emptyList();

        Grading(GradeKind kind, GradingConfig config, int students) {
            this.kind = kind;
            this.maxPoints = config.getMaxPoints();
            this.tolerance = config.getTolerance();
            this.students = students;
        }

        public GradeKind getKind() { return kind; }
        public double getMaxPoints() { return maxPoints; }
        public double getTolerance() { return tolerance; }
        public int getStudents() { return students; }
        /** MILESTONE only. */
        public double getFairShare() { return fairShare; }
        public int getExcludedCommits() { return excludedCommits; }
        public int getExcludedLines() { return excludedLines; }
        /** MILESTONE only: the milestone is in progress. */
        public boolean isProvisional() { return provisional; }
        /** ALL only: milestones in progress. */
        public List<String> getProvisionalMilestones() { return provisionalMilestones; }
    }

    /** The report of the period. */
    public static final class Report {
        private final ResolvedPeriod period;
        private final String periodText;
        private final int totalCommits;
        private final boolean truncated;
        private final Grading grading;
        private final List<Row> rows;

        Report(ResolvedPeriod period, String periodText, int totalCommits, boolean truncated, Grading grading, List<Row> rows) {
            this.period = period;
            this.periodText = periodText;
            this.totalCommits = totalCommits;
            this.truncated = truncated;
            this.grading = grading;
            this.rows = rows;
        }

        public ResolvedPeriod getPeriod() { return period; }
        public String getPeriodText() { return periodText; }
        public int getTotalCommits() { return totalCommits; }
        public boolean isTruncated() { return truncated; }
        /** Null for the custom period. */
        public Grading getGrading() { return grading; }
        public List<Row> getRows() { return rows; }
    }

    private final GroupManagement groups;
    private final MemberRepository members;
    private final IdentityAliasRepository aliases;
    private final GitHubGateway gitHub;
    private final PeriodResolver periods;
    private final GradingConfig grading;
    private final ContributionAnalyzer analyzer;
    private final MilestoneGrades milestoneGrades;

    public ContributionsQuery(GroupManagement groups, MemberRepository members, IdentityAliasRepository aliases,
                              GitHubGateway gitHub, PeriodResolver periods, GradingConfig grading) {
        this.groups = groups;
        this.members = members;
        this.aliases = aliases;
        this.gitHub = gitHub;
        this.periods = periods;
        this.grading = grading;
        this.analyzer = new ContributionAnalyzer(grading, periods.getZone());
        this.milestoneGrades = new MilestoneGrades(gitHub, aliases, periods, grading);
    }

    /**
     * @param period key of the period (all, m1, m2, m3, custom); since/until for the custom period
     * @throws br.com.gradechecker.core.github.GitHubApiException when GitHub cannot be read
     */
    public Report report(long groupId, String period, String since, String until, String accessToken) {
        Group group = groups.activeGroup(groupId);
        ResolvedPeriod resolved = periods.resolve(period, since, until);
        if (resolved.getError() != null) throw DomainException.invalid("invalid_period", resolved.getError());

        RepositoryHistory history = gitHub.getHistory(new HistoryRequest(group.getOwner(), group.getRepository(),
                resolved.getSinceIso(), resolved.getUntilIso(), accessToken));
        ContributionsReport report = analyzer.report(history, aliases.findByGroupId(groupId));

        List<Row> rows = new ArrayList<>();
        for (ContributorStats s : report.getStats()) rows.add(Row.of(s));
        Grading gradingInfo = addGrades(resolved, group, accessToken, report, rows, members.findLoginsByGroupId(groupId));

        return new Report(resolved, periods.describe(resolved), report.getTotalCommits(), report.isTruncated(), gradingInfo, rows);
    }

    private Grading addGrades(ResolvedPeriod period, Group group, String accessToken, ContributionsReport report,
                              List<Row> rows, List<String> memberLogins) {
        if (PeriodResolver.CUSTOM.equals(period.getKey())) return null;

        if (period.getKey().startsWith("m")) {
            GradeCalculator.Result result = new GradeCalculator(grading)
                    .calculate(GradeCalculator.indicators(report.getStats()), memberLogins);
            List<String> logins = new ArrayList<>();
            for (StudentGrade g : result.students()) logins.add(g.getLogin());
            addMissingStudents(rows, logins);
            for (Row row : rows) row.grade = result.student(row.author);

            Grading info = new Grading(GradeKind.MILESTONE, grading, result.students().size());
            info.fairShare = result.getFairShare();
            info.excludedCommits = result.getExcludedCommits();
            info.excludedLines = result.getExcludedLines();
            info.provisional = periods.isInProgress(period);
            return info;
        }

        // all: grades of every milestone already started and their sum (same service as the class grades)
        MilestoneGrades.GroupGrades grades = milestoneGrades.grades(group, memberLogins, accessToken);
        List<String> logins = new ArrayList<>();
        for (MilestoneGrades.StudentGrades g : grades.getStudents()) logins.add(g.getLogin());
        addMissingStudents(rows, logins);
        for (Row row : rows) row.milestoneGrades = grades.student(row.author);

        Grading info = new Grading(GradeKind.ALL, grading, grades.getStudents().size());
        info.provisionalMilestones = grades.getProvisionalMilestones();
        return info;
    }

    private static void addMissingStudents(List<Row> rows, Collection<String> students) {
        Set<String> present = new HashSet<>();
        for (Row row : rows) present.add(row.author.toLowerCase(Locale.ROOT));
        for (String login : students) {
            if (present.add(login.toLowerCase(Locale.ROOT))) rows.add(Row.withoutCommits(login));
        }
    }
}
