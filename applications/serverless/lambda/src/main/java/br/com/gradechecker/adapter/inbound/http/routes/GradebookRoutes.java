package br.com.gradechecker.adapter.inbound.http.routes;

import br.com.gradechecker.adapter.inbound.http.Call;
import br.com.gradechecker.adapter.inbound.http.Maps;
import br.com.gradechecker.adapter.inbound.http.Response;
import br.com.gradechecker.adapter.inbound.http.Router;
import br.com.gradechecker.core.application.ClassGradesQuery;
import br.com.gradechecker.core.application.MilestoneGrades;
import br.com.gradechecker.core.application.MilestoneGrades.StudentGrades;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.github.GitHubApiException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.gradechecker.adapter.inbound.http.Router.Access.admin;

/**
 * Class grades (capability gradebook-api), administrators only: the groups and their students (no GitHub call), and
 * the grades of one group (use case ClassGradesQuery of grade-checker-core). The CSV is made by the SPA.
 */
public final class GradebookRoutes {

    static final String FORBIDDEN = "Only administrators can see class grades.";

    private static final Logger logger = LogManager.getLogger(GradebookRoutes.class);

    private final ClassGradesQuery classGrades;

    public GradebookRoutes(ClassGradesQuery classGrades) {
        this.classGrades = classGrades;
    }

    public void register(Router router) {
        Router.Access gradesAdmin = admin("FORBIDDEN", FORBIDDEN);
        router.get("/api/grades", gradesAdmin, this::groups)
                .get("/api/grades/{id}", gradesAdmin, this::groupGrades);
    }

    private Response groups(Call call) {
        List<Object> rows = new ArrayList<>();
        for (ClassGradesQuery.GroupStudents g : classGrades.groups()) {
            rows.add(Maps.of("id", g.getGroup().getId(), "name", g.getGroup().getName(), "students", g.getStudents()));
        }
        return Response.ok(Maps.of("groups", rows));
    }

    private Response groupGrades(Call call) {
        long id = GroupRoutes.id(call);
        MilestoneGrades.GroupGrades result;
        try {
            result = classGrades.grades(id, call.token());
        } catch (GitHubApiException | DomainException e) {
            throw e;                                           // status and message of GitHub or of the rule
        } catch (RuntimeException e) {
            logger.error("Class grades failed for group {}", id, e);
            return Response.error(500, "error", "Could not calculate the grades of this group.");
        }
        List<Object> rows = new ArrayList<>();
        for (StudentGrades s : result.getStudents()) {
            Map<String, Object> row = Maps.of("login", s.getLogin());
            for (String key : MilestoneGrades.MILESTONES) row.put(key, s.get(key));
            row.put("total", s.getTotal());
            rows.add(row);
        }
        return Response.ok(Maps.of("groupId", id, "students", rows,
                "provisionalMilestones", result.getProvisionalMilestones(), "excludedCommits", result.getExcludedCommits()));
    }
}
