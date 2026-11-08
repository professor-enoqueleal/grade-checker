package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.config.Milestones;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.GradingExplanation;
import br.com.gradechecker.model.GroupCard;
import br.com.gradechecker.repository.MemberRepository;
import br.com.gradechecker.service.GroupService;
import br.com.gradechecker.service.GroupServiceImpl;
import br.com.gradechecker.service.PeriodResolver;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@WebServlet("/detail")
public class GroupDetailServlet extends HttpServlet {

    private final GroupService groupService;
    private final PeriodResolver periodResolver;
    private final MemberRepository memberRepository;
    private final GradingConfig grading;

    public GroupDetailServlet() {
        this.groupService = new GroupServiceImpl();
        this.periodResolver = new PeriodResolver();
        this.memberRepository = new MemberRepository();
        this.grading = GradingConfig.fromClasspath();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        long groupId;
        try {
            groupId = Long.parseLong(httpServletRequest.getParameter("id").trim());
        } catch (NumberFormatException | NullPointerException e) {
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "The 'id' parameter is required.");
            return;
        }

        String accessToken = (String) httpServletRequest.getSession().getAttribute("accessToken");

        Group group = groupService.getGroup(groupId, accessToken);
        if (group == null) {   // missing or archived group (openspec capability group-management)
            httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Group not found.");
            return;
        }

        httpServletRequest.setAttribute("group", group);

        // "Contributors and members" cards (openspec capability group-members)
        httpServletRequest.setAttribute("cards", GroupCard.of(group.getContributors(),
                group.getMembers() == null ? List.of() : group.getMembers(), group.getContributorsError() == null));
        // botões de período do dashboard (primeira carga em All)
        httpServletRequest.setAttribute("periodOptions", periodResolver.options(PeriodResolver.ALL));
        httpServletRequest.setAttribute("customUntil", periodResolver.toInputValue(periodResolver.all().getUntil()));
        // "Como as notas são calculadas" box, for every user (openspec capability milestone-grading)
        httpServletRequest.setAttribute("gradingExplanation", GradingExplanation.of(grading, Milestones.fromClasspath()));

        // manual association of identities (openspec capability identity-aliases): administrators only
        boolean admin = AdminConfig.isAdmin(httpServletRequest.getSession(false));
        httpServletRequest.setAttribute("isAdmin", admin);
        if (admin) {
            List<String> students = memberRepository.findUsernamesByGroupId(group.getId()).stream()
                    .filter(grading::isStudentLogin)
                    .collect(Collectors.toList());
            httpServletRequest.setAttribute("aliasTargets", students);
        }

        httpServletRequest.getRequestDispatcher("detail.jsp").forward(httpServletRequest, httpServletResponse);

    }

}
