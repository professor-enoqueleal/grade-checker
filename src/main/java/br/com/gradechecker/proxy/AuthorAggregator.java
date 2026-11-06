package br.com.gradechecker.proxy;

import br.com.gradechecker.model.ContributorStatsResponseBody;

import java.util.*;

/**
 * Aggregates commits per author (openspec capability contributions-dashboard, requirement "Identificação do autor").
 * <ol>
 *   <li>commit linked to an account: its login;</li>
 *   <li>unlinked commit whose valid e-mail belongs to exactly one linked login: that login;</li>
 *   <li>(2b) unlinked commit whose git name is exactly a linked login (case and outer spaces ignored): that login;</li>
 *   <li>unlinked commit with a valid e-mail: one "no GitHub account" author per e-mail;</li>
 *   <li>otherwise: one "no GitHub account" author per git name (or "unknown").</li>
 * </ol>
 * Never merges authors by name similarity: only exact equality with an existing login (rule 2b).
 * Unlinked commits added to a login are listed in that row's gitIdentities, for auditing.
 */
final class AuthorAggregator {

    private static final class Author {
        final String display;
        final boolean linked;
        final Set<String> identities = new LinkedHashSet<>();
        int commits;
        int additions;
        int deletions;

        Author(String display, boolean linked) {
            this.display = display;
            this.linked = linked;
        }

        void add(int additions, int deletions) {
            this.commits++;
            this.additions += additions;
            this.deletions += deletions;
        }
    }

    private static final class Unlinked {
        final String name;
        final String email;
        final int additions;
        final int deletions;

        Unlinked(String name, String email, int additions, int deletions) {
            this.name = name;
            this.email = email;
            this.additions = additions;
            this.deletions = deletions;
        }
    }

    private final Map<String, Author> authors = new LinkedHashMap<>();
    private final Map<String, Set<String>> loginsByEmail = new HashMap<>();
    private final Map<String, String> loginsByNormalizedName = new HashMap<>();
    private final List<Unlinked> unlinked = new ArrayList<>();

    /** Phase 1: called for every non-merge commit, in any order. */
    void add(String login, String name, String email, int additions, int deletions) {

        if (!isBlank(login)) {
            authors.computeIfAbsent("login:" + login, k -> new Author(login, true)).add(additions, deletions);
            loginsByNormalizedName.putIfAbsent(normalize(login), login);
            if (isValidEmail(email)) {
                loginsByEmail.computeIfAbsent(normalize(email), k -> new LinkedHashSet<>()).add(login);
            }
            return;
        }

        // resolved only at the end: the linked commit with the same e-mail may come on a later page
        unlinked.add(new Unlinked(name, email, additions, deletions));
    }

    /** Phase 2: resolves unlinked commits and returns the report rows (percentage + ordering). */
    List<ContributorStatsResponseBody> result() {

        for (Unlinked commit : unlinked) {

            String email = isValidEmail(commit.email) ? normalize(commit.email) : null;
            Set<String> logins = email == null ? null : loginsByEmail.get(email);

            String loginByName = isBlank(commit.name) ? null : loginsByNormalizedName.get(normalize(commit.name));

            Author author;

            if (logins != null && logins.size() == 1) {                       // rule 2: same e-mail as one linked login
                String login = logins.iterator().next();
                author = authors.computeIfAbsent("login:" + login, k -> new Author(login, true));
            } else if (loginByName != null) {                                // rule 2b: git name is exactly a login
                author = authors.computeIfAbsent("login:" + loginByName, k -> new Author(loginByName, true));
            } else if (email != null) {
                String display = isBlank(commit.name) ? email : commit.name.trim();
                author = authors.computeIfAbsent("email:" + email, k -> new Author(display, false));
            } else {
                String display = isBlank(commit.name) ? "unknown" : commit.name.trim();
                author = authors.computeIfAbsent("name:" + display, k -> new Author(display, false));
            }

            author.add(commit.additions, commit.deletions);

            // every unlinked commit is listed: on unlinked rows, and on linked rows it was added to (auditable)
            author.identities.add(identity(commit.name, commit.email));
        }

        unlinked.clear();

        long grandTotal = 0;
        for (Author a : authors.values()) grandTotal += (long) a.additions + a.deletions;

        List<ContributorStatsResponseBody> rows = new ArrayList<>();

        for (Author a : authors.values()) {
            double percentage = grandTotal == 0 ? 0 : Math.round(((double) (a.additions + a.deletions) / grandTotal) * 10000) / 100.0;
            rows.add(new ContributorStatsResponseBody(a.display, a.commits, a.additions, a.deletions, percentage,
                    a.linked, new ArrayList<>(a.identities)));
        }

        rows.sort(Comparator
                .comparingInt((ContributorStatsResponseBody s) -> s.getTotalAdditions() + s.getTotalDeletions()).reversed()
                .thenComparing(ContributorStatsResponseBody::getAuthor));

        return rows;
    }

    /** e.g. "=" or "" are not e-mails: text before and after a single "@". */
    static boolean isValidEmail(String email) {
        if (isBlank(email)) return false;
        String e = email.trim();
        int at = e.indexOf('@');
        return at > 0 && at == e.lastIndexOf('@') && at < e.length() - 1;
    }

    private static String identity(String name, String email) {
        String n = isBlank(name) ? "" : name.trim();
        String e = email == null ? "" : email.trim();
        return (n + " <" + e + ">").trim();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
