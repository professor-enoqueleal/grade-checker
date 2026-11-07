package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.config.Milestones;
import br.com.gradechecker.model.ContributionsReport;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.proxy.GitHubApiException;
import br.com.gradechecker.service.ContributorsService;
import br.com.gradechecker.service.GroupGradesService;
import br.com.gradechecker.service.PeriodResolver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** openspec capability class-gradebook. */
public class GradebookServletTest {

    private ContributorsService contributors;
    private GradebookServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;
    private StringWriter body;
    private List<Group> groups;
    private Map<Long, List<String>> members;

    @Before
    public void setUp() throws Exception {
        System.setProperty(AdminConfig.ADMINS, "professor-enoqueleal");

        Properties semester = new Properties();
        semester.setProperty("milestone.1.start", "2026-08-13");
        semester.setProperty("milestone.1.end", "2026-09-17");
        semester.setProperty("milestone.2.start", "2026-09-18");
        semester.setProperty("milestone.2.end", "2026-10-29");
        semester.setProperty("milestone.3.start", "2026-10-30");
        semester.setProperty("milestone.3.end", "2026-11-19");
        // 2026-11-06: M1 and M2 ended, M3 in progress
        PeriodResolver resolver = new PeriodResolver(Milestones.of(semester), Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));

        Properties gradingProps = new Properties();
        gradingProps.setProperty("grading.excludedLogins", "professor-enoqueleal");
        GradingConfig grading = GradingConfig.of(gradingProps);

        contributors = mock(ContributorsService.class);
        groups = new ArrayList<>(List.of(
                new Group(6, "cannamed", "https://github.com/o/cannamed", "o", "cannamed"),
                new Group(2, "alpha", "https://github.com/o/alpha", "o", "alpha"),
                new Group(9, "=HYPERLINK(\"http://x\")", "https://github.com/o/x", "o", "x"),
                new Group(4, "empty", "https://github.com/o/empty", "o", "empty")));
        members = new HashMap<>();
        members.put(6L, List.of("thiagofp77", "Bia", "professor-enoqueleal", "dependabot[bot]"));
        members.put(2L, List.of("zed"));
        members.put(9L, List.of("x1"));
        members.put(4L, List.of("professor-enoqueleal"));

        servlet = new GradebookServlet(() -> groups, id -> members.getOrDefault(id, List.of()), grading,
                new GroupGradesService(contributors, resolver, grading));

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);
        body = new StringWriter();
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("accessToken")).thenReturn("token");
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("Professor-EnoqueLeal");
        when(request.getRequestDispatcher("/grades.jsp")).thenReturn(dispatcher);
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    private static ContributorStatsResponseBody stat(String author, double effectiveLines, int days, int relevant, int commits, boolean linked) {
        ContributorStatsResponseBody s = new ContributorStatsResponseBody(author, commits, (int) effectiveLines, 0, 0, linked, Collections.emptyList());
        s.setGradingIndicators(effectiveLines, days, relevant);
        return s;
    }

    private void reports(ContributorStatsResponseBody... stats) {
        when(contributors.getContributionsReport(any())).thenReturn(new ContributionsReport("s", "u", stats.length, false, List.of(stats)));
    }

    private void path(String servletPath) {
        when(request.getServletPath()).thenReturn(servletPath);
    }

    private JsonObject json() {
        return JsonParser.parseString(body.toString()).getAsJsonObject();
    }

    // ---------------------------------------------------------------- authorization

    @Test
    public void otherUserGets403OnTheThreePathsWithoutTouchingDataOrGitHub() throws Exception {
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn("alice");
        boolean[] dataRead = {false};
        servlet = new GradebookServlet(() -> { dataRead[0] = true; return groups; }, id -> { dataRead[0] = true; return List.of(); },
                GradingConfig.defaults(), new GroupGradesService(contributors, new PeriodResolver(), GradingConfig.defaults()));

        path("/grades");
        servlet.doGet(request, response);
        verify(response).setStatus(403);
        verify(request).setAttribute("forbidden", GradebookServlet.FORBIDDEN);
        verify(dispatcher).forward(request, response);

        for (String p : List.of("/grades/group", "/grades.csv")) {
            reset(response);
            body.getBuffer().setLength(0);
            when(response.getWriter()).thenReturn(new PrintWriter(body, true));
            when(request.getParameter("id")).thenReturn("6");
            path(p);
            servlet.doGet(request, response);
            verify(response).setStatus(403);
            assertEquals("FORBIDDEN", json().get("error").getAsString());
            assertEquals(GradebookServlet.FORBIDDEN, json().get("message").getAsString());
        }

        assertFalse("groups and members are not read", dataRead[0]);
        verifyNoInteractions(contributors);
    }

    @Test
    public void withoutAdminsVariableNobodyAccesses() throws Exception {
        System.clearProperty(AdminConfig.ADMINS);
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("6");

        servlet.doGet(request, response);

        // the variable may exist in the environment of the build machine: then this user is not listed in it
        if (AdminConfig.admins().isEmpty() || !AdminConfig.isAdmin("Professor-EnoqueLeal")) {
            verify(response).setStatus(403);
            verifyNoInteractions(contributors);
        }
    }

    @Test
    public void sessionWithoutLoginIsNotAdmin() throws Exception {
        when(session.getAttribute(AdminConfig.USER_LOGIN_ATTRIBUTE)).thenReturn(null);
        path("/grades.csv");

        servlet.doGet(request, response);

        verify(response).setStatus(403);
    }

    // ---------------------------------------------------------------- page

    @Test
    @SuppressWarnings("unchecked")
    public void pageListsGroupsByNameWithStudentsSortedAndWithoutExcludedLogins() throws Exception {
        path("/grades");

        servlet.doGet(request, response);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq("groups"), captor.capture());
        List<GradebookServlet.GroupRow> rows = (List<GradebookServlet.GroupRow>) captor.getValue();
        assertEquals(List.of("=HYPERLINK(\"http://x\")", "alpha", "cannamed", "empty"),
                rows.stream().map(GradebookServlet.GroupRow::getName).collect(java.util.stream.Collectors.toList()));
        assertEquals(List.of("Bia", "thiagofp77"), rows.get(2).getStudents());
        assertTrue("only the professor: no students", rows.get(3).getStudents().isEmpty());
        verify(dispatcher).forward(request, response);
        verifyNoInteractions(contributors); // grades are loaded per group by the page
    }

    // ---------------------------------------------------------------- one group

    @Test
    public void groupJsonHasGradesProvisionalMilestonesAndExcludedCommits() throws Exception {
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("6");
        reports(stat("thiagofp77", 100, 3, 2, 2, true), stat("NO ACCOUNT", 10, 1, 0, 3, false));

        servlet.doGet(request, response);

        verify(response).setStatus(200);
        JsonObject json = json();
        assertEquals(6, json.get("groupId").getAsInt());
        assertEquals("m3", json.getAsJsonArray("provisionalMilestones").get(0).getAsString());
        assertEquals(9, json.get("excludedCommits").getAsInt()); // 3 per milestone
        JsonObject thiago = json.getAsJsonArray("students").get(1).getAsJsonObject();
        assertEquals("thiagofp77", thiago.get("login").getAsString());
        assertEquals(3.0, thiago.get("m1").getAsDouble(), 0);
        assertEquals(9.0, thiago.get("total").getAsDouble(), 0);
        JsonObject bia = json.getAsJsonArray("students").get(0).getAsJsonObject();
        assertEquals("Bia", bia.get("login").getAsString());
        assertEquals(0.0, bia.get("m2").getAsDouble(), 0);
        verify(contributors, times(3)).getContributionsReport(argThat(r -> "6".equals(r.getGroupId()) && "cannamed".equals(r.getRepository())));
    }

    @Test
    public void gitHubErrorKeepsItsStatusAndMessage() throws Exception {
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("6");
        when(contributors.getContributionsReport(any())).thenThrow(
                new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found or not accessible with this token."));

        servlet.doGet(request, response);

        verify(response).setStatus(404);
        assertEquals("Repository not found or not accessible with this token.", json().get("message").getAsString());
    }

    @Test
    public void invalidIdIs400AndUnknownGroupIs404WithoutGitHub() throws Exception {
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("abc");
        servlet.doGet(request, response);
        verify(response).setStatus(400);

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter(), true));
        when(request.getParameter("id")).thenReturn("999");
        servlet.doGet(request, response);
        verify(response).setStatus(404);

        verifyNoInteractions(contributors);
    }

    @Test
    public void groupWithoutStudentsDoesNotCallGitHub() throws Exception {
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("4");

        servlet.doGet(request, response);

        verify(response).setStatus(200);
        assertEquals(0, json().getAsJsonArray("students").size());
        verifyNoInteractions(contributors);
    }

    // ---------------------------------------------------------------- CSV

    @Test
    public void csvInPtBrWithBomErrorColumnAndFormulaProtection() throws Exception {
        path("/grades.csv");
        when(contributors.getContributionsReport(argThat(r -> r != null && "alpha".equals(r.getRepository()))))
                .thenThrow(new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found or not accessible with this token."));
        when(contributors.getContributionsReport(argThat(r -> r != null && !"alpha".equals(r.getRepository()))))
                .thenReturn(new ContributionsReport("s", "u", 1, false, List.of(stat("thiagofp77", 100, 3, 2, 2, true), stat("x1", 100, 3, 2, 2, true))));

        servlet.doGet(request, response);

        verify(response).setContentType("text/csv; charset=UTF-8");
        verify(response).setHeader("Content-Disposition", "attachment; filename=\"grades.csv\"");
        String csv = body.toString();
        assertTrue("BOM", csv.startsWith("﻿"));
        String[] lines = csv.substring(1).split("\r\n");
        assertEquals(GradebookServlet.CSV_HEADER, lines[0]);
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\";x1;3,00;3,00;3,00;9,00;m3;", lines[1]);
        assertEquals("alpha;zed;;;;;;Repository not found or not accessible with this token.", lines[2]);
        assertEquals("cannamed;Bia;0,00;0,00;0,00;0,00;m3;", lines[3]);
        assertEquals("cannamed;thiagofp77;3,00;3,00;3,00;9,00;m3;", lines[4]);
        assertEquals("group without students has no line", 5, lines.length);
    }

    @Test
    public void csvCells() {
        assertEquals("", GradebookServlet.csvCell(null));
        assertEquals("plain", GradebookServlet.csvCell("plain"));
        assertEquals("'+1", GradebookServlet.csvCell("+1"));
        assertEquals("'-1", GradebookServlet.csvCell("-1"));
        assertEquals("'@SUM(A1)", GradebookServlet.csvCell("@SUM(A1)"));
        assertEquals("\"a;b\"", GradebookServlet.csvCell("a;b"));
        assertEquals("\"a\nb\"", GradebookServlet.csvCell("a\nb"));
        assertEquals("2,91", GradebookServlet.decimal(2.91));
        assertEquals("", GradebookServlet.decimal(null));
    }

}
