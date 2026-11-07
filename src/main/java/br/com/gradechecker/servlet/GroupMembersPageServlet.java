package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.model.Member;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.MemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * "Manage members" page of a group (openspec capability group-members): {@code GET /group/members?id=N}.
 * Administrators only. The list comes from the database; GitHub is never called here.
 * Additions, edits and removals go through the API {@code /group-members}.
 */
@WebServlet("/group/members")
public class GroupMembersPageServlet extends HttpServlet {

    static final String PAGE = "/group-members.jsp";
    static final String GROUP_NOT_FOUND = "Group not found.";
    static final String INVALID_ID = "The 'id' parameter is required.";

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final Supplier<List<Group>> groups;
    private final Function<Long, List<Member>> members;

    public GroupMembersPageServlet() {
        this(new GroupRepository()::findAll, new MemberRepository()::findByGroupId);
    }

    public GroupMembersPageServlet(Supplier<List<Group>> groups, Function<Long, List<Member>> members) {
        this.groups = groups;
        this.members = members;
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

        Optional<Group> group = groups.get().stream().filter(g -> g.getId() == groupId).findFirst();
        if (group.isEmpty()) {
            fail(request, response, HttpServletResponse.SC_NOT_FOUND, GROUP_NOT_FOUND);
            return;
        }

        request.setAttribute("group", group.get());
        request.setAttribute("groupId", groupId);
        request.setAttribute("members", members.apply(groupId));
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
