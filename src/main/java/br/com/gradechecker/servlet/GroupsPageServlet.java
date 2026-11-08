package br.com.gradechecker.servlet;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.Milestones;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.repository.GroupRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Manage groups" page (openspec capability group-management): {@code GET /groups}, administrators only.
 * {@code ?add=1} opens the "Add a new group" form (link "Add a new group" of the empty home).
 * Changes go through the API {@code /groups/api}.
 */
@WebServlet("/groups")
public class GroupsPageServlet extends HttpServlet {

    static final String PAGE = "/groups.jsp";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final GroupRepository groups;
    private final ZoneId zone;

    public GroupsPageServlet() {
        this(new GroupRepository(), Milestones.fromClasspath().getZone());
    }

    public GroupsPageServlet(GroupRepository groups, ZoneId zone) {
        this.groups = groups;
        this.zone = zone;
    }

    /** One line of the page. */
    public static final class Row {
        private final long id;
        private final String name;
        private final String repositoryUrl;
        private final int members;
        private final String archivedOn;

        Row(Group group, int members, String archivedOn) {
            this.id = group.getId();
            this.name = group.getName();
            this.repositoryUrl = group.getRepositoryUrl();
            this.members = members;
            this.archivedOn = archivedOn;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public String getRepositoryUrl() { return repositoryUrl; }
        /** Link only for GitHub URLs (seeds are stored as typed). */
        public String getRepositoryLink() {
            return repositoryUrl != null && repositoryUrl.startsWith("https://github.com/") ? repositoryUrl : null;
        }
        /** Short text of the repository: "owner/repository" (the link keeps the full URL). */
        public String getRepositoryLabel() {
            if (repositoryUrl == null) return "";
            String label = repositoryUrl.replaceFirst("(?i)^https?://(www\\.)?github\\.com/", "");
            return label.endsWith("/") ? label.substring(0, label.length() - 1) : label;
        }
        public int getMembers() { return members; }
        public String getArchivedOn() { return archivedOn; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // authorization first: nothing is read from the database for other users
        if (!AdminConfig.isAdmin(request.getSession(false))) {
            logger.warn("Manage groups refused for {}", AdminConfig.userLogin(request.getSession(false)));
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("pageError", GroupsApiServlet.FORBIDDEN);
            request.getRequestDispatcher(PAGE).forward(request, response);
            return;
        }

        Map<Long, Integer> members = groups.countMembers();
        List<Row> active = new ArrayList<>();
        for (Group g : groups.findAll()) active.add(new Row(g, members.getOrDefault(g.getId(), 0), null));
        List<Row> archived = new ArrayList<>();
        for (Group g : groups.findArchived()) {
            archived.add(new Row(g, members.getOrDefault(g.getId(), 0), DATE.format(g.getArchivedAt().atZone(zone))));
        }

        request.setAttribute("groups", active);
        request.setAttribute("archivedGroups", archived);
        request.setAttribute("openAddForm", "1".equals(request.getParameter("add")));
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

}
