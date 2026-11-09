package br.com.gradechecker.core.application;

import br.com.gradechecker.core.attribution.ContributionAnalyzer;
import br.com.gradechecker.core.grading.GradeCalculator;
import br.com.gradechecker.core.grading.GradeCalculator.StudentGrade;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.history.ContributionsReport;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.period.PeriodResolver.ResolvedPeriod;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.IdentityAliasRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Grades of M1, M2, M3 and Total of the students of one group: the single source of the "All" grades of the
 * dashboard (openspec capability milestone-grading) and of the class gradebook (capability class-gradebook), built by
 * the use cases. The manual associations of the group are applied.
 */
public final class MilestoneGrades {

    public static final String[] MILESTONES = {"m1", "m2", "m3"};

    /** Grades of one student: null for a milestone not configured or not started yet. */
    public static final class StudentGrades {
        private final String login;
        private final Map<String, Double> milestones; // m1, m2, m3 (in this order)
        private final double total;

        StudentGrades(String login, Map<String, Double> milestones, double total) {
            this.login = login;
            this.milestones = milestones;
            this.total = total;
        }

        public String getLogin() { return login; }
        public Double get(String milestone) { return milestones.get(milestone); }
        public Map<String, Double> getMilestones() { return milestones; }
        public double getTotal() { return total; }
    }

    /** Grades of a group: students in roster order, milestones in progress and commits left out of the grades. */
    public static final class GroupGrades {
        private final List<StudentGrades> students;
        private final List<String> provisionalMilestones;
        private final int excludedCommits;

        GroupGrades(List<StudentGrades> students, List<String> provisionalMilestones, int excludedCommits) {
            this.students = students;
            this.provisionalMilestones = provisionalMilestones;
            this.excludedCommits = excludedCommits;
        }

        public List<StudentGrades> getStudents() { return students; }
        public List<String> getProvisionalMilestones() { return provisionalMilestones; }
        public int getExcludedCommits() { return excludedCommits; }

        /** Grades of a login (case-insensitive), or null when it is not a student of the group. */
        public StudentGrades student(String login) {
            if (login == null) return null;
            for (StudentGrades s : students) if (s.login.equalsIgnoreCase(login.trim())) return s;
            return null;
        }
    }

    private final GitHubGateway gitHub;
    private final IdentityAliasRepository aliases;
    private final PeriodResolver periods;
    private final GradingConfig grading;
    private final ContributionAnalyzer analyzer;

    MilestoneGrades(GitHubGateway gitHub, IdentityAliasRepository aliases, PeriodResolver periods, GradingConfig grading) {
        this.gitHub = gitHub;
        this.aliases = aliases;
        this.periods = periods;
        this.grading = grading;
        this.analyzer = new ContributionAnalyzer(grading, periods.getZone());
    }

    /**
     * @param students GitHub logins of the members (bots and excluded logins are removed by the calculator)
     * @throws br.com.gradechecker.core.github.GitHubApiException when the history cannot be read
     */
    GroupGrades grades(Group group, Collection<String> students, String accessToken) {

        GradeCalculator calculator = new GradeCalculator(grading);
        Map<String, Map<String, Double>> byLogin = new LinkedHashMap<>(); // lower-case login -> milestone -> grade
        Map<String, String> spelling = new LinkedHashMap<>();
        List<String> provisional = new ArrayList<>();
        int excludedCommits = 0;

        // started milestones (not configured or not started: "—")
        Map<String, ResolvedPeriod> started = new LinkedHashMap<>();
        for (String key : MILESTONES) {
            ResolvedPeriod milestone = periods.resolve(key, null, null);
            if (milestone.getError() == null) started.put(key, milestone);
        }
        Map<String, ContributionsReport> reports = reports(started, group, accessToken);

        for (Map.Entry<String, ResolvedPeriod> entry : started.entrySet()) {
            String key = entry.getKey();
            GradeCalculator.Result result = calculator.calculate(GradeCalculator.indicators(reports.get(key).getStats()), students);

            excludedCommits += result.getExcludedCommits();
            for (StudentGrade g : result.students()) {
                String lower = g.getLogin().toLowerCase(Locale.ROOT);
                spelling.putIfAbsent(lower, g.getLogin());
                byLogin.computeIfAbsent(lower, k -> new LinkedHashMap<>()).put(key, g.getGrade());
            }
            if (periods.isInProgress(entry.getValue())) provisional.add(key);
        }

        List<StudentGrades> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : byLogin.entrySet()) {
            Map<String, Double> all = new LinkedHashMap<>();
            double total = 0;
            for (String key : MILESTONES) {
                Double g = e.getValue().get(key);
                all.put(key, g);
                if (g != null) total += g;
            }
            result.add(new StudentGrades(spelling.get(e.getKey()), all, GradeCalculator.round(total)));
        }

        return new GroupGrades(result, provisional, excludedCommits);
    }

    /**
     * One report per started milestone: one read of the history from the start of the first started milestone to the
     * end of the last one, sliced in memory (the API Gateway of the serverless version answers in up to 30 s). When
     * that history is truncated (commit limit), each milestone is read on its own, so the result is always the same as
     * the per-milestone reads.
     */
    private Map<String, ContributionsReport> reports(Map<String, ResolvedPeriod> started, Group group, String accessToken) {
        Map<String, ContributionsReport> reports = new LinkedHashMap<>();
        if (started.isEmpty()) return reports;
        Map<String, String> groupAliases = aliases.findByGroupId(group.getId());

        List<ResolvedPeriod> milestones = new ArrayList<>(started.values());
        RepositoryHistory history = gitHub.getHistory(request(group, milestones.get(0).getSinceIso(),
                milestones.get(milestones.size() - 1).getUntilIso(), accessToken));
        if (!history.isTruncated()) {
            for (Map.Entry<String, ResolvedPeriod> e : started.entrySet()) {
                reports.put(e.getKey(), analyzer.report(history, e.getValue().getSinceIso(), e.getValue().getUntilIso(), groupAliases));
            }
            return reports;
        }

        for (Map.Entry<String, ResolvedPeriod> e : started.entrySet()) {
            RepositoryHistory milestone = gitHub.getHistory(request(group, e.getValue().getSinceIso(), e.getValue().getUntilIso(), accessToken));
            reports.put(e.getKey(), analyzer.report(milestone, groupAliases));
        }
        return reports;
    }

    private static HistoryRequest request(Group group, String since, String until, String accessToken) {
        return new HistoryRequest(group.getOwner(), group.getRepository(), since, until, accessToken);
    }
}
