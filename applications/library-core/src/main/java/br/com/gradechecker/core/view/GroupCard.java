package br.com.gradechecker.core.view;

import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.group.Member;

import java.util.*;

/**
 * One card of "Contributors and members" (openspec capability group-members): a GitHub contributor, or a member
 * without commits. Built on the server so the page only renders.
 */
public class GroupCard {

    private final String login;
    private final String avatarUrl;
    private final String htmlUrl;
    private final Integer contributions;   // null when GitHub could not be read
    private final boolean member;

    public GroupCard(String login, String avatarUrl, String htmlUrl, Integer contributions, boolean member) {
        this.login = login;
        this.avatarUrl = avatarUrl;
        this.htmlUrl = htmlUrl;
        this.contributions = contributions;
        this.member = member;
    }

    public String getLogin() { return login; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getHtmlUrl() { return htmlUrl; }
    public Integer getContributions() { return contributions; }
    public boolean isMember() { return member; }

    /**
     * Contributors first (GitHub order), then the members that are not contributors (by login, ignoring case).
     *
     * @param contributorsAvailable false when GitHub could not be read: members get "—" instead of 0
     */
    public static List<GroupCard> of(List<Contributor> contributors, List<Member> members, boolean contributorsAvailable) {

        Set<String> memberLogins = new HashSet<>();
        for (Member m : members) memberLogins.add(m.getLogin().toLowerCase(Locale.ROOT));

        List<GroupCard> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Contributor c : contributors) {
            if (c.getLogin() == null) continue;
            String key = c.getLogin().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            cards.add(new GroupCard(c.getLogin(), c.getAvatarUrl(), c.getHtmlUrl(), c.getContributions(), memberLogins.contains(key)));
        }

        List<Member> withoutCommits = new ArrayList<>();
        for (Member m : members) {
            if (!seen.contains(m.getLogin().toLowerCase(Locale.ROOT))) withoutCommits.add(m);
        }
        withoutCommits.sort(Comparator.comparing(m -> m.getLogin().toLowerCase(Locale.ROOT)));
        for (Member m : withoutCommits) {
            String login = m.getLogin();
            cards.add(new GroupCard(login, "https://github.com/" + login + ".png", "https://github.com/" + login,
                    contributorsAvailable ? 0 : null, true));
        }
        return cards;
    }
}
