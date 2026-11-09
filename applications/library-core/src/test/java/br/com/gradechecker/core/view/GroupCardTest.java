package br.com.gradechecker.core.view;

import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.group.Member;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/** openspec capability group-members: "Contributors and members" cards. */
public class GroupCardTest {

    private static final List<Contributor> CONTRIBUTORS = List.of(
            new Contributor("Pedro", 1, "a1", "h1", 7), new Contributor("thiagofp77", 2, "a2", "h2", 6));

    private static List<String> logins(List<GroupCard> cards) {
        return cards.stream().map(GroupCard::getLogin).collect(Collectors.toList());
    }

    @Test
    public void contributorsFirstThenMembersWithoutCommitsByLogin() {
        List<GroupCard> cards = GroupCard.of(CONTRIBUTORS, List.of(
                new Member("Zoe", "zoe", 6), new Member("THIAGOFP77", "THIAGOFP77", 6), new Member("Ana", "ana", 6)), true);

        assertEquals(List.of("Pedro", "thiagofp77", "ana", "zoe"), logins(cards));
        assertFalse(cards.get(0).isMember());
        assertTrue("member ignoring case, no duplicated card", cards.get(1).isMember());
        assertEquals(Integer.valueOf(6), cards.get(1).getContributions());

        GroupCard ana = cards.get(2);
        assertTrue(ana.isMember());
        assertEquals(Integer.valueOf(0), ana.getContributions());
        assertEquals("https://github.com/ana.png", ana.getAvatarUrl());
        assertEquals("https://github.com/ana", ana.getHtmlUrl());
    }

    @Test
    public void withoutGitHubMembersShowUnknownContributions() {
        List<GroupCard> cards = GroupCard.of(List.of(), List.of(new Member("bia", "bia", 6)), false);

        assertEquals(1, cards.size());
        assertNull(cards.get(0).getContributions());
        assertTrue(cards.get(0).isMember());
    }

    @Test
    public void noContributorsAndNoMembers() {
        assertTrue(GroupCard.of(List.of(), List.of(), true).isEmpty());
    }
}
