package br.com.gradechecker.core.group;

import org.junit.Test;

import static org.junit.Assert.*;

/** openspec capability group-management: URL of the repository of a group. */
public class RepositoryUrlTest {

    @Test
    public void ownerAndRepositoryComeFromTheUrl() {
        RepositoryUrl u = RepositoryUrl.parse(" https://github.com/hugodev0711/clinica-pi.git ");
        assertEquals("hugodev0711", u.getOwner());
        assertEquals("clinica-pi", u.getRepository());

        u = RepositoryUrl.parse("https://GitHub.com/LucasMoura-dev777/cannamed/");
        assertEquals("LucasMoura-dev777", u.getOwner());
        assertEquals("cannamed", u.getRepository());

        assertEquals("sip-pair.", RepositoryUrl.parse("https://github.com/affernanda/sip-pair.").getRepository());
        assertEquals("Project_Papo-de.Viagem", RepositoryUrl.parse("https://www.github.com/BrenoMS117/Project_Papo-de.Viagem").getRepository());
    }

    @Test
    public void otherUrlsAreRefused() {
        for (String url : new String[]{null, "", "github.com/clinica", "https://gitlab.com/a/b", "http://github.com/a/b",
                "https://github.com/a", "https://github.com/a/b/c", "https://github.com/-a/b", "https://github.com/a/.git",
                "https://github.com/a/..", "https://github.com/a b/c"}) {
            assertNull(url, RepositoryUrl.parse(url));
        }
    }

    @Test
    public void normalizedUrlIgnoresCaseSchemeGitAndTrailingSlash() {
        String expected = "github.com/hugodev0711/clinica-pi";
        assertEquals(expected, RepositoryUrl.normalize("https://github.com/Hugodev0711/clinica-pi"));
        assertEquals(expected, RepositoryUrl.normalize("https://www.github.com/hugodev0711/clinica-pi.git/"));
        assertEquals(expected, RepositoryUrl.normalize("http://GITHUB.com/hugodev0711/clinica-pi//"));
        assertEquals("", RepositoryUrl.normalize(null));
    }
}
