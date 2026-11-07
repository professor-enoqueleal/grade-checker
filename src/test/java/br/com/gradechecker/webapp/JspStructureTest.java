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
    public void homePage() throws Exception {
        assertWellFormed("home.jsp");
    }
}
