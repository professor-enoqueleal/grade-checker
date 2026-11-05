package br.com.gradechecker.service;

import br.com.gradechecker.config.ConnectionPoolConfig;
import br.com.gradechecker.model.Contributor;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.ContributorsRepository;
import br.com.gradechecker.repository.GroupRepository;
import br.com.gradechecker.repository.MemberRepository;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class GroupServiceImplTest {

    @BeforeClass
    public static void setupDatabase() throws Exception {
        System.setProperty("DB_URL", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        System.setProperty("DB_USER", "sa");
        System.setProperty("DB_PASS", "");

        try (Connection conn = ConnectionPoolConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            InputStream is = GroupServiceImplTest.class.getResourceAsStream("/db.sql");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                for (String sql : sb.toString().split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            stmt.execute("INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('test', 'url', 'owner', 'repo')");
        }
    }

    @Test
    public void getGroup_shouldPersistMembers_whenContributorsEmpty() {
        // create a ContributorsRepository that returns empty initially
        ContributorsRepository contributorsRepository = new ContributorsRepository();
        MemberRepository memberRepository = new MemberRepository();
        GroupRepository groupRepository = new GroupRepository();

        // stubbed GitHubProxy
        GitHubProxy gitHubProxy = new GitHubProxy() {
            @Override
            public List<Contributor> getContributors(String accessKey, String owner, String repo) {
                List<Contributor> list = new ArrayList<>();
                Contributor c = new Contributor("johndoe", 123, "", "", 10);
                list.add(c);
                return list;
            }
        };

        GroupServiceImpl service = new GroupServiceImpl(contributorsRepository, groupRepository, memberRepository, gitHubProxy);

        Group group = service.getGroup(1L, "token");

        assertNotNull(group);
        assertEquals(1, group.getContributors().size());
        assertTrue(group.getTotalOfMembers() >= 1);
    }
}

