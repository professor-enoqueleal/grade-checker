package br.com.gradechecker.webapp;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** Guards against the unclosed <div> and duplicated ids that used to hide/break page sections. */
public class JspStructureTest {

    private static final Pattern STATIC_ID = Pattern.compile("\\sid=\"([^\"$]+)\"");

    private static void assertWellFormed(String page) throws Exception {

        String source = Files.readString(Path.of("src/main/webapp", page));

        int open = count(source, "<div\\b");
        int close = count(source, "</div>");
        assertEquals(page + ": <div> opened vs closed", open, close);

        Set<String> ids = new HashSet<>();
        Matcher m = STATIC_ID.matcher(source);
        while (m.find()) {
            assertTrue(page + ": duplicated id '" + m.group(1) + "'", ids.add(m.group(1)));
        }
    }

    /** Ids of a page plus the ids of the navbar it includes must not repeat (openspec capability navigation-bar). */
    private static void assertNoIdClashWithNavbar(String page) throws Exception {
        String source = Files.readString(Path.of("src/main/webapp", page));
        assertTrue(page + " uses the navbar", source.contains("<gc:navbar"));
        // the phone menu needs Bootstrap JS exactly once: two copies open the menu and never close it
        assertEquals(page + ": Bootstrap JS scripts", 1, count(source, "<script[^>]+src=\"[^\"]*/bootstrap/[^\"]*\""));
        Set<String> ids = new HashSet<>();
        for (String file : new String[]{page, "WEB-INF/tags/navbar.tag"}) {
            Matcher m = STATIC_ID.matcher(Files.readString(Path.of("src/main/webapp", file)));
            while (m.find()) assertTrue(page + ": id '" + m.group(1) + "' also in the navbar", ids.add(m.group(1)));
        }
    }

    @Test
    public void navbarTag() throws Exception {
        assertWellFormed("WEB-INF/tags/navbar.tag");
    }

    @Test
    public void everyAuthenticatedPageHasTheNavbarWithoutRepeatedIds() throws Exception {
        for (String page : new String[]{"home.jsp", "detail.jsp", "commits-detail.jsp", "grades.jsp", "groups.jsp", "group-members.jsp"}) {
            assertNoIdClashWithNavbar(page);
        }
        assertFalse("the login page has no navbar",
                Files.readString(Path.of("src/main/webapp", "authentication.jsp")).contains("<gc:navbar"));
    }

    private static int count(String source, String regex) {
        Matcher m = Pattern.compile(regex).matcher(source);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    @Test
    public void commitsDetailPage() throws Exception {
        assertWellFormed("commits-detail.jsp");
    }

    @Test
    public void groupDetailPage() throws Exception {
        assertWellFormed("detail.jsp");
    }

    @Test
    public void classGradesPage() throws Exception {
        assertWellFormed("grades.jsp");
    }

    @Test
    public void manageMembersPage() throws Exception {
        assertWellFormed("group-members.jsp");
    }

    @Test
    public void manageGroupsPage() throws Exception {
        assertWellFormed("groups.jsp");
    }

    @Test
    public void homePage() throws Exception {
        assertWellFormed("home.jsp");
    }
}
