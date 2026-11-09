package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.error.DomainException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * "Manage members" page of a group (openspec capability group-members): {@code GET /group/members?id=N}.
 * Administrators only. The list comes from the database; GitHub is never called here.
 * Additions, edits and removals go through the API {@code /group-members}.
 */
@WebServlet("/group/members")
public class GroupMembersPageServlet extends HttpServlet {

    static final String PAGE = "/group-members.jsp";
    static final String INVALID_ID = "The 'id' parameter is required.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final Membership membership;

    public GroupMembersPageServlet() {
        this(AppContext.get().membership);
    }

    public GroupMembersPageServlet(Membership membership) {
        this.membership = membership;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // authorization first: nothing is read from the database for other users
        if (!AdminConfig.isAdmin(request.getSession(false))) {
            logger.warn("Manage members refused for {}", AdminConfig.userLogin(request.getSession(false)));
            fail(request, response, HttpServletResponse.SC_FORBIDDEN, GroupMembersServlet.FORBIDDEN);
            return;
        }

        long groupId;
        try {
            groupId = Long.parseLong(request.getParameter("id").trim());
        } catch (NumberFormatException | NullPointerException e) {
            fail(request, response, HttpServletResponse.SC_BAD_REQUEST, INVALID_ID);
            return;
        }

        Membership.GroupMembers result;
        try {
            result = membership.members(groupId);
        } catch (DomainException e) {                         // missing or archived group
            fail(request, response, DomainErrors.status(e), e.getMessage());
            return;
        }

        request.setAttribute("group", result.getGroup());
        request.setAttribute("groupId", groupId);
        request.setAttribute("members", result.getMembers());
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    private static void fail(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws ServletException, IOException {
        response.setStatus(status);
        request.setAttribute("pageError", message);
        String id = request.getParameter("id");
        if (id != null && id.trim().matches("\\d{1,18}")) request.setAttribute("groupId", Long.parseLong(id.trim()));
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

}
