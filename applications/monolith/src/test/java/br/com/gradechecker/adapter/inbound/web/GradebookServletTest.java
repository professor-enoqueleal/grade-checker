package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.adapter.outbound.jdbc.JdbcGroupRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcIdentityAliasRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcMemberRepository;
import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.application.ClassGradesQuery;
import br.com.gradechecker.core.application.GroupManagement;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.port.GitHubGateway;

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
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** openspec capability class-gradebook. */
public class GradebookServletTest {

    private GitHubGateway gitHub;
    private final Map<String, List<HistoryCommit>> commitsByRepository = new HashMap<>();
    private final Map<String, GitHubApiException> errorsByRepository = new HashMap<>();
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

        gitHub = mock(GitHubGateway.class);
        when(gitHub.getHistory(any())).thenAnswer(call -> {
            HistoryRequest r = call.getArgument(0);
            if (errorsByRepository.containsKey(r.getRepository())) throw errorsByRepository.get(r.getRepository());
            return new RepositoryHistory(r.getSince(), r.getUntil(), false, commitsByRepository.getOrDefault(r.getRepository(), List.of()));
        });
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

        servlet = new GradebookServlet(classGrades(() -> groups, id -> members.getOrDefault(id, List.of()), grading, resolver, gitHub));

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

    /** The use case ClassGradesQuery over repositories that read the given groups and rosters. */
    private static ClassGradesQuery classGrades(Supplier<List<Group>> activeGroups, Function<Long, List<String>> roster,
                                                GradingConfig grading, PeriodResolver periods, GitHubGateway gitHub) {
        JdbcGroupRepository groupRepository = mock(JdbcGroupRepository.class);
        when(groupRepository.findActive()).thenAnswer(call -> activeGroups.get());
        when(groupRepository.findActive(anyLong())).thenAnswer(call -> activeGroups.get().stream()
                .filter(g -> g.getId() == (long) call.getArgument(0)).findFirst().orElse(null));
        JdbcMemberRepository memberRepository = mock(JdbcMemberRepository.class);
        when(memberRepository.findLoginsByGroupId(anyLong())).thenAnswer(call -> roster.apply(call.getArgument(0)));
        JdbcIdentityAliasRepository aliases = mock(JdbcIdentityAliasRepository.class);
        when(aliases.findByGroupId(anyLong())).thenReturn(Map.of());
        return new ClassGradesQuery(new GroupManagement(groupRepository, memberRepository, gitHub),
                new Membership(groupRepository, memberRepository, aliases, gitHub, grading), aliases, gitHub, periods, grading);
    }

    @After
    public void tearDown() {
        System.clearProperty(AdminConfig.ADMINS);
    }

    private static final String[][] MILESTONE_DAYS = {{"2026-08-20", "2026-08-21"}, {"2026-10-01", "2026-10-02"}, {"2026-11-02", "2026-11-03"}};

    /** Each login commits on two days of every milestone, with the same lines. */
    private void activeInEveryMilestone(String repository, String... logins) {
        List<HistoryCommit> commits = commitsByRepository.computeIfAbsent(repository, k -> new ArrayList<>());
        for (String login : logins) {
            for (String[] days : MILESTONE_DAYS) {
                for (String day : days) commits.add(commit(commits.size(), day, login, login + "@x.com"));
            }
        }
    }

    /** One commit of an identity without account in every milestone. */
    private void withoutAccountInEveryMilestone(String repository) {
        List<HistoryCommit> commits = commitsByRepository.computeIfAbsent(repository, k -> new ArrayList<>());
        for (String[] days : MILESTONE_DAYS) commits.add(commit(commits.size(), days[0], null, "no-account@home.dev"));
    }

    private static HistoryCommit commit(int n, String day, String login, String email) {
        return new HistoryCommit("c" + n, "work", null, day + "T15:00:00Z", 50, 0, 2, 1,
                login == null ? "No Account" : login, email, login);
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
        servlet = new GradebookServlet(classGrades(() -> { dataRead[0] = true; return groups; }, id -> { dataRead[0] = true; return List.of(); },
                GradingConfig.defaults(), new PeriodResolver(), gitHub));

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
        verifyNoInteractions(gitHub);
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
            verifyNoInteractions(gitHub);
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
        verifyNoInteractions(gitHub); // grades are loaded per group by the page
    }

    // ---------------------------------------------------------------- one group

    @Test
    public void groupJsonHasGradesProvisionalMilestonesAndExcludedCommits() throws Exception {
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("6");
        activeInEveryMilestone("cannamed", "thiagofp77");
        withoutAccountInEveryMilestone("cannamed");

        servlet.doGet(request, response);

        verify(response).setStatus(200);
        JsonObject json = json();
        assertEquals(6, json.get("groupId").getAsInt());
        assertEquals("m3", json.getAsJsonArray("provisionalMilestones").get(0).getAsString());
        assertEquals(3, json.get("excludedCommits").getAsInt()); // 1 per milestone
        JsonObject thiago = json.getAsJsonArray("students").get(1).getAsJsonObject();
        assertEquals("thiagofp77", thiago.get("login").getAsString());
        assertEquals(3.0, thiago.get("m1").getAsDouble(), 0);
        assertEquals(9.0, thiago.get("total").getAsDouble(), 0);
        JsonObject bia = json.getAsJsonArray("students").get(0).getAsJsonObject();
        assertEquals("Bia", bia.get("login").getAsString());
        assertEquals(0.0, bia.get("m2").getAsDouble(), 0);
        verify(gitHub, times(1)).getHistory(argThat(r -> "o".equals(r.getOwner()) && "cannamed".equals(r.getRepository())));
    }

    @Test
    public void gitHubErrorKeepsItsStatusAndMessage() throws Exception {
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("6");
        errorsByRepository.put("cannamed",
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

        verifyNoInteractions(gitHub);
    }

    @Test
    public void groupWithoutStudentsDoesNotCallGitHub() throws Exception {
        path("/grades/group");
        when(request.getParameter("id")).thenReturn("4");

        servlet.doGet(request, response);

        verify(response).setStatus(200);
        assertEquals(0, json().getAsJsonArray("students").size());
        verifyNoInteractions(gitHub);
    }

    // ---------------------------------------------------------------- CSV

    @Test
    public void csvInPtBrWithBomErrorColumnAndFormulaProtection() throws Exception {
        path("/grades.csv");
        errorsByRepository.put("alpha",
                new GitHubApiException(404, GitHubApiException.NOT_FOUND, "Repository not found or not accessible with this token."));
        activeInEveryMilestone("cannamed", "thiagofp77");
        activeInEveryMilestone("x", "thiagofp77", "x1");

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
