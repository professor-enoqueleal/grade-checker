package br.com.gradechecker.service;

import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import br.com.gradechecker.service.GradeCalculator.Indicators;
import br.com.gradechecker.service.GradeCalculator.StudentGrade;
import br.com.gradechecker.service.PeriodResolver.ResolvedPeriod;

import java.util.*;

/**
 * Grades of M1, M2, M3 and Total of the students of one group: the single source of the "All" grades of the
 * dashboard (openspec capability milestone-grading) and of the class gradebook (capability class-gradebook).
 * Manual associations of the group are applied because the group id goes in the request.
 */
public class GroupGradesService {

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
        public StudentGrades of(String login) {
            if (login == null) return null;
            for (StudentGrades s : students) if (s.login.equalsIgnoreCase(login.trim())) return s;
            return null;
        }
    }

    private final ContributorsService contributorsService;
    private final PeriodResolver periodResolver;
    private final GradingConfig grading;

    public GroupGradesService(ContributorsService contributorsService, PeriodResolver periodResolver, GradingConfig grading) {
        this.contributorsService = contributorsService;
        this.periodResolver = periodResolver;
        this.grading = grading;
    }

    /**
     * @param members GitHub usernames of the group members (bots and excluded logins are removed here)
     * @throws br.com.gradechecker.proxy.GitHubApiException when a milestone report cannot be read
     */
    public GroupGrades of(long groupId, String owner, String repo, String accessToken, Collection<String> members) {

        GradeCalculator calculator = new GradeCalculator(grading);
        Map<String, Map<String, Double>> byLogin = new LinkedHashMap<>(); // lower-case login -> milestone -> grade
        Map<String, String> spelling = new LinkedHashMap<>();
        List<String> provisional = new ArrayList<>();
        int excludedCommits = 0;

        for (String key : MILESTONES) {
            ResolvedPeriod milestone = periodResolver.resolve(key, null, null);
            if (milestone.getError() != null) continue; // not configured or not started: "—"

            CommitRequestBody request = new CommitRequestBody(repo, owner, null,
                    milestone.getSinceIso(), milestone.getUntilIso(), accessToken, String.valueOf(groupId));
            GradeCalculator.Result result = calculator.calculate(
                    indicators(contributorsService.getContributionsReport(request).getConsolidatedStats()), members);

            excludedCommits += result.getExcludedCommits();
            for (StudentGrade g : result.students()) {
                String lower = g.getLogin().toLowerCase(Locale.ROOT);
                spelling.putIfAbsent(lower, g.getLogin());
                byLogin.computeIfAbsent(lower, k -> new LinkedHashMap<>()).put(key, g.getGrade());
            }
            if (periodResolver.isInProgress(milestone)) provisional.add(key);
        }

        List<StudentGrades> students = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : byLogin.entrySet()) {
            Map<String, Double> all = new LinkedHashMap<>();
            double total = 0;
            for (String key : MILESTONES) {
                Double g = e.getValue().get(key);
                all.put(key, g);
                if (g != null) total += g;
            }
            students.add(new StudentGrades(spelling.get(e.getKey()), all, GradeCalculator.round(total)));
        }

        return new GroupGrades(students, provisional, excludedCommits);
    }

    /** Indicators per author of a report (input of the GradeCalculator). */
    public static Map<String, Indicators> indicators(List<ContributorStatsResponseBody> stats) {
        Map<String, Indicators> byAuthor = new LinkedHashMap<>();
        for (ContributorStatsResponseBody s : stats) {
            byAuthor.put(s.getAuthor(), new Indicators(s.getEffectiveLines(), s.getActiveDays(), s.getRelevantCommits(),
                    s.getCommits(), s.getTotalAdditions() + s.getTotalDeletions()));
        }
        return byAuthor;
    }

}
