package br.com.gradechecker.model;

import org.junit.Test;

import static org.junit.Assert.*;

/** openspec capability group-management: URL of the repository of a group. */
public class GroupRepositoryUrlTest {

    @Test
    public void ownerAndRepositoryComeFromTheUrl() {
        GroupRepositoryUrl u = GroupRepositoryUrl.parse(" https://github.com/hugodev0711/clinica-pi.git ");
        assertEquals("hugodev0711", u.getOwner());
        assertEquals("clinica-pi", u.getRepository());

        u = GroupRepositoryUrl.parse("https://GitHub.com/LucasMoura-dev777/cannamed/");
        assertEquals("LucasMoura-dev777", u.getOwner());
        assertEquals("cannamed", u.getRepository());

        assertEquals("sip-pair.", GroupRepositoryUrl.parse("https://github.com/affernanda/sip-pair.").getRepository());
        assertEquals("Project_Papo-de.Viagem", GroupRepositoryUrl.parse("https://www.github.com/BrenoMS117/Project_Papo-de.Viagem").getRepository());
    }

    @Test
    public void otherUrlsAreRefused() {
        for (String url : new String[]{null, "", "github.com/clinica", "https://gitlab.com/a/b", "http://github.com/a/b",
                "https://github.com/a", "https://github.com/a/b/c", "https://github.com/-a/b", "https://github.com/a/.git",
                "https://github.com/a/..", "https://github.com/a b/c"}) {
            assertNull(url, GroupRepositoryUrl.parse(url));
        }
    }

    @Test
    public void normalizedUrlIgnoresCaseSchemeGitAndTrailingSlash() {
        String expected = "github.com/hugodev0711/clinica-pi";
        assertEquals(expected, GroupRepositoryUrl.normalize("https://github.com/Hugodev0711/clinica-pi"));
        assertEquals(expected, GroupRepositoryUrl.normalize("https://www.github.com/hugodev0711/clinica-pi.git/"));
        assertEquals(expected, GroupRepositoryUrl.normalize("http://GITHUB.com/hugodev0711/clinica-pi//"));
        assertEquals("", GroupRepositoryUrl.normalize(null));
    }
}
