package br.com.gradechecker.core.application;

import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver;

import org.junit.Test;

import java.time.Clock;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class ClassGradesQueryTest {

    @Test
    public void groupsByNameWithStudentsAndNoGitHubCallWithoutStudents() {
        InMemoryPorts.Groups groups = new InMemoryPorts.Groups();
        InMemoryPorts.Aliases aliases = new InMemoryPorts.Aliases();
        InMemoryPorts.Members members = new InMemoryPorts.Members(aliases);
        InMemoryPorts.GitHub gitHub = new InMemoryPorts.GitHub();
        long b = groups.add("beta", "https://github.com/acme/b", false).getId();
        groups.add("Alpha", "https://github.com/acme/a", false);
        groups.add("Gone", "https://github.com/acme/g", true);
        members.addIfAbsent(b, "ana", "Ana", false);

        GroupManagement management = new GroupManagement(groups, members, gitHub);
        Membership membership = new Membership(groups, members, aliases, gitHub, GradingConfig.defaults());
        ClassGradesQuery grades = new ClassGradesQuery(management, membership, aliases, gitHub,
                new PeriodResolver(Milestones.of(new Properties()), Clock.systemUTC()), GradingConfig.defaults());

        List<ClassGradesQuery.GroupStudents> list = grades.groups();
        assertEquals(2, list.size());
        assertEquals("Alpha", list.get(0).getGroup().getName());
        assertEquals(List.of("ana"), list.get(1).getStudents());

        MilestoneGrades.GroupGrades empty = grades.grades(list.get(0).getGroup().getId(), "t");
        assertTrue(empty.getStudents().isEmpty());
        assertEquals(0, gitHub.calls);
    }
}
