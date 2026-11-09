package br.com.gradechecker.core.application;

import br.com.gradechecker.core.error.DomainException;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.group.Group;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class MembershipTest {

    private InMemoryPorts.Groups groups;
    private InMemoryPorts.Aliases aliases;
    private InMemoryPorts.Members members;
    private InMemoryPorts.GitHub gitHub;
    private Membership membership;
    private IdentityLinking linking;
    private long id;

    @Before
    public void setUp() {
        groups = new InMemoryPorts.Groups();
        aliases = new InMemoryPorts.Aliases();
        members = new InMemoryPorts.Members(aliases);
        gitHub = new InMemoryPorts.GitHub();
        Properties p = new Properties();
        p.setProperty("grading.excludedLogins", "professor");
        GradingConfig grading = GradingConfig.of(p);
        membership = new Membership(groups, members, aliases, gitHub, grading);
        linking = new IdentityLinking(groups, members, aliases, grading);
        id = groups.add("A", "https://github.com/acme/demo", false).getId();
    }

    private static DomainException refused(Runnable action) {
        try {
            action.run();
        } catch (DomainException e) {
            return e;
        }
        throw new AssertionError("expected a DomainException");
    }

    @Test
    public void addUsesTheSpellingOfGitHubAndTheLoginAsDefaultName() {
        gitHub.user("Ana-Dev");
        Membership.MemberChange added = membership.add(id, "ana-dev", null, true, "t");
        assertEquals("Ana-Dev", added.getLogin());
        assertEquals("Ana-Dev", added.getName());
        assertTrue(members.isMember(id, "ANA-DEV"));
    }

    @Test
    public void addChecksFormatBeforeTheGroupAndGitHub() {
        DomainException e = refused(() -> membership.add(999, "not a login", null, false, "t"));
        assertEquals(Membership.LOGIN_REQUIRED, e.getMessage());
        assertEquals(0, gitHub.calls);

        assertEquals(DomainException.Kind.NOT_FOUND, refused(() -> membership.add(999, "ana", null, false, "t")).getKind());
        assertEquals("invalid_login", refused(() -> membership.add(id, "ghost", null, false, "t")).getCode());
    }

    @Test
    public void aLoginIsMemberOnceIgnoringCase() {
        gitHub.user("ana");
        membership.add(id, "ana", "Ana", false, "t");
        DomainException e = refused(() -> membership.add(id, "ANA", "Ana", false, "t"));
        assertEquals(DomainException.Kind.CONFLICT, e.getKind());
        assertEquals("already_member", e.getCode());
    }

    @Test
    public void editMovesTheLinksToTheNewLogin() {
        members.addIfAbsent(id, "ana", "Ana", false);
        aliases.save(id, "email:ana@x.com", "ana", "prof");
        gitHub.user("Ana2");

        Membership.MemberChange edited = membership.edit(id, "ana", "ana2", "Ana B", "t");
        assertEquals("Ana2", edited.getLogin());
        assertEquals("ana", edited.getPreviousLogin());
        assertEquals("Ana2", aliases.findByGroupId(id).get("email:ana@x.com"));
    }

    @Test
    public void editOfANonMember() {
        DomainException e = refused(() -> membership.edit(id, "ana", null, null, "t"));
        assertEquals(Membership.NOT_MEMBER, e.getMessage());
    }

    @Test
    public void aStudentWithLinkedIdentitiesIsNotRemoved() {
        members.addIfAbsent(id, "ana", "Ana", false);
        aliases.save(id, "name:Ana", "ana", "prof");
        DomainException e = refused(() -> membership.remove(id, "ana"));
        assertEquals("has_aliases", e.getCode());
        assertTrue(members.isMember(id, "ana"));

        aliases.delete(id, "name:Ana");
        membership.remove(id, "ana");
        assertFalse(members.isMember(id, "ana"));
    }

    @Test
    public void studentsLeaveOutBotsAndExcludedLogins() {
        members.addIfAbsent(id, "zed", "Zed", false);
        members.addIfAbsent(id, "Bea", "Bea", false);
        members.addIfAbsent(id, "dependabot[bot]", "bot", false);
        members.addIfAbsent(id, "professor", "Prof", false);
        assertEquals(List.of("Bea", "zed"), membership.students(id));
    }

    @Test
    public void linksOnlyToStudentsOfTheGroup() {
        members.addIfAbsent(id, "Ana", "Ana", false);
        members.addIfAbsent(id, "professor", "Prof", false);

        assertEquals("Ana", linking.link(id, "email:a@x.com", "ana", "prof"));
        assertEquals("invalid_login", refused(() -> linking.link(id, "email:p@x.com", "professor", "prof")).getCode());
        assertEquals("invalid_identity", refused(() -> linking.link(id, "a@x.com", "ana", "prof")).getCode());

        linking.unlink(id, "email:a@x.com");
        assertEquals(IdentityLinking.NOT_LINKED, refused(() -> linking.unlink(id, "email:a@x.com")).getMessage());
    }

    @Test
    public void archivedGroupsAreNotFound() {
        Group old = groups.add("Old", "https://github.com/acme/old", true);
        assertEquals(DomainException.Kind.NOT_FOUND, refused(() -> membership.members(old.getId())).getKind());
        assertEquals(DomainException.Kind.NOT_FOUND, refused(() -> linking.unlink(old.getId(), "name:x")).getKind());
    }
}
