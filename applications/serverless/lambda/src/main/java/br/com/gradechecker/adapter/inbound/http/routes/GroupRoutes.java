package br.com.gradechecker.adapter.inbound.http.routes;

import br.com.gradechecker.adapter.inbound.http.Call;
import br.com.gradechecker.adapter.inbound.http.Maps;
import br.com.gradechecker.adapter.inbound.http.Response;
import br.com.gradechecker.adapter.inbound.http.Router;
import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.core.application.GroupManagement;
import br.com.gradechecker.core.application.GroupManagement.GroupDetail;
import br.com.gradechecker.core.application.IdentityLinking;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.application.Membership.MemberChange;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.group.Member;
import br.com.gradechecker.core.view.GroupCard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static br.com.gradechecker.adapter.inbound.http.Router.Access.USER;
import static br.com.gradechecker.adapter.inbound.http.Router.Access.admin;

/**
 * Groups, members and manual identity links (capabilities serverless-groups-api, serverless-members-api, serverless-identity-aliases-api): reads the
 * request, calls the use cases of grade-checker-core (GroupManagement, Membership, IdentityLinking) and writes JSON.
 */
public final class GroupRoutes {

    static final String GROUPS_FORBIDDEN = "Only administrators can manage groups.";
    static final String MEMBERS_FORBIDDEN = "Only administrators can manage members.";
    static final String ALIASES_FORBIDDEN = "Only administrators can link identities.";

    static final String INVALID_ID = "The 'id' parameter is required.";

    private static final DateTimeFormatter ARCHIVED_ON = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final Logger logger = LogManager.getLogger(GroupRoutes.class);

    private final GroupManagement groups;
    private final Membership membership;
    private final IdentityLinking linking;
    private final ZoneId zone;

    public GroupRoutes(GroupManagement groups, Membership membership, IdentityLinking linking, ZoneId zone) {
        this.groups = groups;
        this.membership = membership;
        this.linking = linking;
        this.zone = zone;
    }

    public void register(Router router) {
        Router.Access groupsAdmin = admin("FORBIDDEN", GROUPS_FORBIDDEN);
        Router.Access membersAdmin = admin("FORBIDDEN", MEMBERS_FORBIDDEN);
        Router.Access aliasesAdmin = admin("forbidden", ALIASES_FORBIDDEN);

        router.get("/api/groups", USER, this::list)
                .get("/api/groups/archived", groupsAdmin, this::archived)          // before /api/groups/{id}
                .post("/api/groups", groupsAdmin, this::create)
                .get("/api/groups/{id}", USER, this::detail)
                .put("/api/groups/{id}", groupsAdmin, this::update)
                .post("/api/groups/{id}/archive", groupsAdmin, call -> archiveOrRestore(call, true))
                .post("/api/groups/{id}/restore", groupsAdmin, call -> archiveOrRestore(call, false))
                .get("/api/groups/{id}/members", membersAdmin, this::members)
                .post("/api/groups/{id}/members", membersAdmin, this::addMember)
                .put("/api/groups/{id}/members/{login}", membersAdmin, this::editMember)
                .delete("/api/groups/{id}/members/{login}", membersAdmin, this::removeMember)
                .post("/api/groups/{id}/aliases", aliasesAdmin, this::link)
                .delete("/api/groups/{id}/aliases", aliasesAdmin, this::unlink);
    }

    static long id(Call call) {
        return call.request().longParam("id", INVALID_ID);
    }

    static Map<String, Object> summary(Group g) {
        return Maps.of("id", g.getId(), "name", g.getName(), "repositoryUrl", g.getRepositoryUrl(),
                "owner", g.getOwner(), "repository", g.getRepository(), "members", g.getMemberCount());
    }

    private static List<Object> memberRows(List<Member> members) {
        List<Object> rows = new ArrayList<>();
        for (Member m : members) rows.add(Maps.of("login", m.getLogin(), "name", m.getName()));
        return rows;
    }

    // ------------------------------------------------------------------------------------------- groups

    private Response list(Call call) {
        List<Object> body = new ArrayList<>();
        for (Group g : groups.activeGroups()) body.add(summary(g));
        return Response.ok(body);
    }

    private Response archived(Call call) {
        List<Object> body = new ArrayList<>();
        for (Group g : groups.archivedGroups()) {
            Map<String, Object> row = summary(g);
            row.put("archivedOn", ARCHIVED_ON.format(g.getArchivedAt().atZone(zone)));
            body.add(row);
        }
        return Response.ok(Maps.of("groups", body));
    }

    /** Group, members, cards (contributors from GitHub + members without commits) and students for aliases. */
    private Response detail(Call call) {
        GroupDetail detail = groups.detail(id(call), call.token());
        Group group = detail.getGroup();
        boolean contributorsOk = detail.getContributorsError() == null;
        List<String> aliasTargets = AdminConfig.isAdmin(call.login()) ? membership.students(group.getId()) : Collections.emptyList();

        return Response.ok(Maps.of(
                "group", summary(group),
                "members", memberRows(detail.getMembers()),
                "cards", GroupCard.of(detail.getContributors(), detail.getMembers(), contributorsOk),
                "contributorsCount", contributorsOk ? detail.getContributors().size() : null,
                "contributorsError", detail.getContributorsError(),
                "aliasTargets", aliasTargets));
    }

    private Response create(Call call) {
        Group group = groups.create(call.request().string("name"), call.request().string("repositoryUrl"), call.token());
        logger.info("Group {} ({}) created by {}", group.getId(), group.getRepositoryUrl(), call.login());
        return Response.ok(summary(group));
    }

    private Response update(Call call) {
        long id = id(call);
        Group group = groups.update(id, call.request().string("name"), call.request().string("repositoryUrl"), call.token());
        logger.info("Group {} edited ({}) by {}", id, group.getRepositoryUrl(), call.login());
        return Response.ok(summary(group));
    }

    private Response archiveOrRestore(Call call, boolean archive) {
        long id = id(call);
        Group group = archive ? groups.archive(id) : groups.restore(id);
        logger.info("Group {} {} by {}", id, archive ? "archived" : "restored", call.login());
        return Response.ok(summary(group));
    }

    // ------------------------------------------------------------------------------------------- members

    private Response members(Call call) {
        Membership.GroupMembers result = membership.members(id(call));
        Group group = result.getGroup();
        return Response.ok(Maps.of("group", Maps.of("id", group.getId(), "name", group.getName()), "members", memberRows(result.getMembers())));
    }

    private Response addMember(Call call) {
        MemberChange added = membership.add(id(call), call.request().string("login"), call.request().string("name"),
                call.request().bool("manual"), call.token());
        logger.info("Member {} added to group {} by {}", added.getLogin(), added.getGroupId(), call.login());
        return Response.ok(Maps.of("groupId", added.getGroupId(), "login", added.getLogin(), "name", added.getName()));
    }

    private Response editMember(Call call) {
        MemberChange edited = membership.edit(id(call), call.request().pathParam("login"), call.request().string("login"),
                call.request().string("name"), call.token());
        logger.info("Member {} of group {} edited to {} by {}", edited.getPreviousLogin(), edited.getGroupId(), edited.getLogin(), call.login());
        return Response.ok(Maps.of("groupId", edited.getGroupId(), "login", edited.getLogin(), "name", edited.getName(),
                "previousLogin", edited.getPreviousLogin()));
    }

    private Response removeMember(Call call) {
        long id = id(call);
        String login = call.request().pathParam("login");
        membership.remove(id, login);
        logger.info("Member {} removed from group {} by {}", login, id, call.login());
        return Response.ok(Maps.of("removed", true));
    }

    // ------------------------------------------------------------------------------------------- identity aliases

    private Response link(Call call) {
        long id = id(call);
        String identityKey = call.request().string("identityKey");
        String student = linking.link(id, identityKey, call.request().string("login"), call.login());
        return Response.ok(Maps.of("groupId", id, "identityKey", identityKey, "login", student));
    }

    private Response unlink(Call call) {
        linking.unlink(id(call), call.request().query("identityKey"));
        return Response.ok(Maps.of("removed", true));
    }
}
