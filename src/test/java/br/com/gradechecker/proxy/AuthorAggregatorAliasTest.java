package br.com.gradechecker.proxy;

import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.model.ContributorStatsResponseBody;
import org.junit.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** openspec capability identity-aliases: manual associations applied by the aggregator. */
public class AuthorAggregatorAliasTest {

    private static AuthorAggregator aggregator(Map<String, String> aliases) {
        return new AuthorAggregator(GradingConfig.defaults(), ZoneId.of("America/Sao_Paulo"), aliases);
    }

    private static ContributorStatsResponseBody row(List<ContributorStatsResponseBody> rows, String author) {
        return rows.stream().filter(r -> r.getAuthor().equals(author)).findFirst().orElse(null);
    }

    @Test
    public void identityKeyUsesTheEmailWhenValidOtherwiseTheName() {
        assertEquals("email:joao@escola.edu", AuthorAggregator.identityKey("Joao", " Joao@Escola.EDU "));
        assertEquals("name:joaolima", AuthorAggregator.identityKey(" JoaoLima ", "="));
        assertNull(AuthorAggregator.identityKey(" ", ""));
    }

    @Test
    public void unlinkedRowsExposeTheirIdentityKey() {
        AuthorAggregator a = aggregator(Map.of());
        a.add(null, "joaolima", "=", 10, 0);
        a.add(null, "Maria", "maria@escola.edu", 5, 0);
        a.add(null, "", "", 1, 0);

        List<ContributorStatsResponseBody> rows = a.result();

        assertEquals("name:joaolima", row(rows, "joaolima").getIdentityKey());
        assertEquals("email:maria@escola.edu", row(rows, "Maria").getIdentityKey());
        assertNull("unknown cannot be linked", row(rows, "unknown").getIdentityKey());
    }

    @Test
    public void manualAssociationMovesTheCommitsToTheLogin() {
        AuthorAggregator a = aggregator(Map.of("name:joaolima", "joaolima16"));
        a.add("joaolima16", "Joao Lima", "123+joaolima16@users.noreply.github.com", 100, 10);
        a.add(null, "joaolima", "=", 40, 0);

        List<ContributorStatsResponseBody> rows = a.result();

        assertEquals(1, rows.size());
        ContributorStatsResponseBody joao = rows.get(0);
        assertEquals("joaolima16", joao.getAuthor());
        assertEquals(2, joao.getCommits());
        assertEquals(140, joao.getTotalAdditions());
        assertTrue(joao.isLinked());
        assertEquals(List.of("joaolima <=>"), joao.getGitIdentities());
        assertEquals(1, joao.getManualIdentities().size());
        assertEquals("name:joaolima", joao.getManualIdentities().get(0).getKey());
        assertEquals("joaolima <=>", joao.getManualIdentities().get(0).getIdentity());
    }

    @Test
    public void manualAssociationTakesPrecedenceOverTheNameRule() {
        AuthorAggregator a = aggregator(Map.of("name:bob", "alice"));
        a.add("alice", "Alice", "alice@x.com", 1, 0);
        a.add("bob", "Bob", "bob@x.com", 1, 0);
        a.add(null, "bob", "=", 50, 0); // would go to bob by rule 2b

        List<ContributorStatsResponseBody> rows = a.result();

        assertEquals(51, row(rows, "alice").getTotalAdditions());
        assertEquals(1, row(rows, "bob").getTotalAdditions());
    }

    @Test
    public void linkedCommitsAreNeverMoved() {
        AuthorAggregator a = aggregator(Map.of("email:lab@escola.edu", "alice"));
        a.add("bob", "Lab", "lab@escola.edu", 30, 0);
        a.add("alice", "Alice", "alice@x.com", 1, 0);

        List<ContributorStatsResponseBody> rows = a.result();

        assertEquals(30, row(rows, "bob").getTotalAdditions());
        assertEquals(1, row(rows, "alice").getTotalAdditions());
        assertTrue(row(rows, "alice").getManualIdentities().isEmpty());
    }

    @Test
    public void loginWithoutCommitsInThePeriodGetsARow() {
        AuthorAggregator a = aggregator(Map.of("email:matheus@senacsp.edu.br", "matheusmbn070906"));
        a.add(null, "MATHEUS MEDEIROS", "Matheus@SenacSP.edu.br", 20, 5);

        List<ContributorStatsResponseBody> rows = a.result();

        assertEquals(1, rows.size());
        assertEquals("matheusmbn070906", rows.get(0).getAuthor());
        assertTrue(rows.get(0).isLinked());
        assertNull(rows.get(0).getIdentityKey());
    }

    @Test
    public void aliasUsesTheSpellingOfTheLinkedRow() {
        AuthorAggregator a = aggregator(Map.of("name:pedro", "pedroasmfodsj"));
        a.add("Pedroasmfodsj", "Pedro", "p@x.com", 1, 0);
        a.add(null, "pedro", "=", 9, 0);

        List<ContributorStatsResponseBody> rows = a.result();

        assertEquals(1, rows.size());
        assertEquals("Pedroasmfodsj", rows.get(0).getAuthor());
        assertEquals(10, rows.get(0).getTotalAdditions());
    }
}
