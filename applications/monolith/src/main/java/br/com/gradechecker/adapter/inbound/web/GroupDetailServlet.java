package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.GroupManagement;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.grading.GradingExplanation;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.view.GroupCard;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/detail")
public class GroupDetailServlet extends HttpServlet {

    private final GroupManagement groups;
    private final Membership membership;
    private final PeriodResolver periodResolver;
    private final GradingConfig grading;

    public GroupDetailServlet() {
        this(AppContext.get().groups, AppContext.get().membership, AppContext.get().periods, AppContext.get().grading);
    }

    public GroupDetailServlet(GroupManagement groups, Membership membership, PeriodResolver periodResolver, GradingConfig grading) {
        this.groups = groups;
        this.membership = membership;
        this.periodResolver = periodResolver;
        this.grading = grading;
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

        GroupManagement.GroupDetail detail;
        try {
            detail = groups.detail(groupId, accessToken);
        } catch (DomainException e) {   // missing or archived group (openspec capability group-management)
            httpServletResponse.sendError(DomainErrors.status(e), e.getMessage());
            return;
        }
        Group group = detail.getGroup();

        httpServletRequest.setAttribute("group", group);
        httpServletRequest.setAttribute("members", detail.getMembers());
        httpServletRequest.setAttribute("contributors", detail.getContributors());
        httpServletRequest.setAttribute("contributorsError", detail.getContributorsError());

        // "Contributors and members" cards (openspec capability group-members)
        httpServletRequest.setAttribute("cards", GroupCard.of(detail.getContributors(), detail.getMembers(),
                detail.getContributorsError() == null));
        // botões de período do dashboard (primeira carga em All)
        httpServletRequest.setAttribute("periodOptions", periodResolver.options(PeriodResolver.ALL));
        httpServletRequest.setAttribute("customUntil", periodResolver.toInputValue(periodResolver.all().getUntil()));
        // "Como as notas são calculadas" box, for every user (openspec capability milestone-grading)
        httpServletRequest.setAttribute("gradingExplanation", GradingExplanation.of(grading, periodResolver.getMilestones()));

        // manual association of identities (openspec capability identity-aliases): administrators only
        boolean admin = AdminConfig.isAdmin(httpServletRequest.getSession(false));
        httpServletRequest.setAttribute("isAdmin", admin);
        if (admin) httpServletRequest.setAttribute("aliasTargets", membership.students(group.getId()));

        httpServletRequest.getRequestDispatcher("detail.jsp").forward(httpServletRequest, httpServletResponse);

    }

}
