package br.com.gradechecker.core.application;

import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.IdentityAliasRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Class grades (capability core-use-cases): the active groups with their students (no GitHub call), and the grades of
 * one group, the same as the period All of the report.
 */
public class ClassGradesQuery {

    /** An active group and its students. */
    public static final class GroupStudents {
        private final Group group;
        private final List<String> students;

        GroupStudents(Group group, List<String> students) {
            this.group = group;
            this.students = students;
        }

        public Group getGroup() { return group; }
        public List<String> getStudents() { return students; }
    }

    private final GroupManagement groups;
    private final Membership membership;
    private final MilestoneGrades milestoneGrades;

    public ClassGradesQuery(GroupManagement groups, Membership membership, IdentityAliasRepository aliases,
                            GitHubGateway gitHub, PeriodResolver periods, GradingConfig grading) {
        this.groups = groups;
        this.membership = membership;
        this.milestoneGrades = new MilestoneGrades(gitHub, aliases, periods, grading);
    }

    /** Active groups by name (ignoring case), then id, with their students. */
    public List<GroupStudents> groups() {
        List<Group> active = new ArrayList<>(groups.activeGroups());
        active.sort(Comparator.comparing((Group g) -> g.getName() == null ? "" : g.getName().toLowerCase(Locale.ROOT))
                .thenComparingLong(Group::getId));
        List<GroupStudents> result = new ArrayList<>();
        for (Group g : active) result.add(new GroupStudents(g, membership.students(g.getId())));
        return result;
    }

    /**
     * Grades of the students of an active group; no GitHub call when it has no students.
     *
     * @throws br.com.gradechecker.core.github.GitHubApiException when GitHub cannot be read
     */
    public MilestoneGrades.GroupGrades grades(long groupId, String accessToken) {
        Group group = groups.activeGroup(groupId);
        List<String> students = membership.students(groupId);
        if (students.isEmpty()) return new MilestoneGrades.GroupGrades(Collections.emptyList(), Collections.emptyList(), 0);
        return milestoneGrades.grades(group, students, accessToken);
    }
}
