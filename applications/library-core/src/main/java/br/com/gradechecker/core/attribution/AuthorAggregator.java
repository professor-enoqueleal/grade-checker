package br.com.gradechecker.core.attribution;

import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.history.ContributorStats;
import br.com.gradechecker.core.history.HistoryCommit;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Aggregates commits per author (openspec capability contributions-dashboard, requirement "Identificação do autor").
 * <ol>
 *   <li>commit linked to an account: its login (manual associations never change it);</li>
 *   <li>(0) unlinked commit whose identity key was linked manually to a login of the group: that login
 *       (openspec capability identity-aliases; takes precedence over the rules below);</li>
 *   <li>unlinked commit whose valid e-mail belongs to exactly one linked login: that login;</li>
 *   <li>(2b) unlinked commit whose git name is exactly a linked login (case and outer spaces ignored): that login;</li>
 *   <li>unlinked commit with a valid e-mail: one "no GitHub account" author per e-mail;</li>
 *   <li>otherwise: one "no GitHub account" author per git name (or "unknown").</li>
 * </ol>
 * Never merges authors by name similarity: only exact equality with an existing login (rule 2b).
 * Unlinked commits added to a login are listed in that row's gitIdentities, for auditing.
 * Used through {@link ContributionAnalyzer}.
 */
final class AuthorAggregator {

    private static final class Author {
        final String display;
        final boolean linked;
        final Set<String> identities = new LinkedHashSet<>();
        final Map<String, String> manual = new LinkedHashMap<>(); // identity key -> "name <email>"
        String identityKey; // authors without account: key used to link them manually
        int commits;
        int additions;
        int deletions;
        // grading indicators (openspec capability milestone-grading)
        double effectiveLines;
        final Set<LocalDate> activeDays = new HashSet<>();
        int relevantCommits;

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
        final LocalDate day;
        final int changedFiles;

        Unlinked(String name, String email, int additions, int deletions, LocalDate day, int changedFiles) {
            this.name = name;
            this.email = email;
            this.additions = additions;
            this.deletions = deletions;
            this.day = day;
            this.changedFiles = changedFiles;
        }
    }

    private final GradingConfig grading;
    private final ZoneId zone;
    private final Map<String, String> aliases; // identity key -> login (manual associations of the group)

    AuthorAggregator(GradingConfig grading, ZoneId zone, Map<String, String> aliases) {
        this.grading = grading;
        this.zone = zone;
        this.aliases = aliases == null ? Collections.emptyMap() : aliases;
    }

    private final Map<String, Author> authors = new LinkedHashMap<>();
    private final Map<String, Set<String>> loginsByEmail = new HashMap<>();
    private final Map<String, String> loginsByNormalizedName = new HashMap<>();
    private final List<Unlinked> unlinked = new ArrayList<>();

    /** Phase 1: called for every non-merge commit, in any order. */
    public void add(String login, String name, String email, int additions, int deletions, String committedDate, int changedFiles) {

        LocalDate day = dayOf(committedDate);

        if (!isBlank(login)) {
            Author linkedAuthor = authors.computeIfAbsent("login:" + login, k -> new Author(login, true));
            linkedAuthor.add(additions, deletions);
            indicators(linkedAuthor, additions, deletions, day, changedFiles);
            loginsByNormalizedName.putIfAbsent(normalize(login), login);
            if (isValidEmail(email)) {
                loginsByEmail.computeIfAbsent(normalize(email), k -> new LinkedHashSet<>()).add(login);
            }
            return;
        }

        // resolved only at the end: the linked commit with the same e-mail may come on a later page
        unlinked.add(new Unlinked(name, email, additions, deletions, day, changedFiles));
    }

    /** Phase 2: resolves unlinked commits and returns the report rows (percentage + ordering). */
    public List<ContributorStats> result() {

        for (Unlinked commit : unlinked) {

            Resolution r = resolveUnlinked(commit.name, commit.email);
            Author author = authors.computeIfAbsent(r.authorKey, k -> new Author(r.display, r.linked));

            if (r.source == AttributionSource.MANUAL) author.manual.putIfAbsent(r.identityKey, identity(commit.name, commit.email));
            if (!r.linked) author.identityKey = r.identityKey; // null for "unknown" (no name, no e-mail): cannot be linked

            author.add(commit.additions, commit.deletions);
            indicators(author, commit.additions, commit.deletions, commit.day, commit.changedFiles);

            // every unlinked commit is listed: on unlinked rows, and on linked rows it was added to (auditable)
            author.identities.add(identity(commit.name, commit.email));
        }

        unlinked.clear();

        long grandTotal = 0;
        for (Author a : authors.values()) grandTotal += (long) a.additions + a.deletions;

        List<ContributorStats> rows = new ArrayList<>();

        for (Author a : authors.values()) {
            double percentage = grandTotal == 0 ? 0 : Math.round(((double) (a.additions + a.deletions) / grandTotal) * 10000) / 100.0;
            ContributorStats row = new ContributorStats(a.display, a.commits, a.additions, a.deletions, percentage,
                    a.linked, new ArrayList<>(a.identities));
            row.setGradingIndicators(a.effectiveLines, a.activeDays.size(), a.relevantCommits);
            row.setIdentityKey(a.linked ? null : a.identityKey);
            List<ContributorStats.ManualIdentity> manual = new ArrayList<>();
            a.manual.forEach((k, label) -> manual.add(new ContributorStats.ManualIdentity(k, label)));
            row.setManualIdentities(manual);
            rows.add(row);
        }

        rows.sort(Comparator
                .comparingInt((ContributorStats s) -> s.getTotalAdditions() + s.getTotalDeletions()).reversed()
                .thenComparing(ContributorStats::getAuthor));

        return rows;
    }

    /** How an unlinked commit is resolved: the single place of the rules 0, 2, 2b, 3 and 4. */
    private static final class Resolution {
        final String authorKey;
        final String display;
        final boolean linked;
        final AttributionSource source;
        final String identityKey;

        Resolution(String authorKey, String display, boolean linked, AttributionSource source, String identityKey) {
            this.authorKey = authorKey;
            this.display = display;
            this.linked = linked;
            this.source = source;
            this.identityKey = identityKey;
        }
    }

    /** Needs every commit of the period registered (phase 1): rule 2 looks at all linked e-mails. */
    private Resolution resolveUnlinked(String name, String rawEmail) {

        String email = isValidEmail(rawEmail) ? normalize(rawEmail) : null;
        Set<String> logins = email == null ? null : loginsByEmail.get(email);
        String loginByName = isBlank(name) ? null : loginsByNormalizedName.get(normalize(name));
        String key = identityKey(name, rawEmail);
        String aliasLogin = key == null ? null : aliases.get(key);

        if (aliasLogin != null && !isBlank(aliasLogin)) {                    // rule 0: manual association
            // same spelling as the linked row when the login has commits in the period; a new row otherwise
            String login = loginsByNormalizedName.getOrDefault(normalize(aliasLogin), aliasLogin.trim());
            return new Resolution("login:" + login, login, true, AttributionSource.MANUAL, key);
        }
        if (logins != null && logins.size() == 1) {                          // rule 2: same e-mail as one linked login
            String login = logins.iterator().next();
            return new Resolution("login:" + login, login, true, AttributionSource.SAME_EMAIL, key);
        }
        if (loginByName != null) {                                           // rule 2b: git name is exactly a login
            return new Resolution("login:" + loginByName, loginByName, true, AttributionSource.SAME_NAME, key);
        }
        if (email != null) {                                                 // rule 3
            String display = isBlank(name) ? email : name.trim();
            return new Resolution("email:" + email, display, false, AttributionSource.NO_ACCOUNT, key);
        }
        String display = isBlank(name) ? "unknown" : name.trim();           // rule 4
        return new Resolution("name:" + display, display, false, AttributionSource.NO_ACCOUNT, key);
    }

    /**
     * Author of one commit, with the same rules as {@link #result()} (openspec capability commit-history-page).
     * Call only after every commit of the period was added.
     */
    public AttributedCommit attributionOf(HistoryCommit commit) {
        if (!isBlank(commit.getAuthorLogin())) {
            return new AttributedCommit(commit, commit.getAuthorLogin(), true, AttributionSource.ACCOUNT, null);
        }
        Resolution r = resolveUnlinked(commit.getAuthorName(), commit.getAuthorEmail());
        return new AttributedCommit(commit, r.display, r.linked, r.source, identity(commit.getAuthorName(), commit.getAuthorEmail()));
    }

    private void indicators(Author author, int additions, int deletions, LocalDate day, int changedFiles) {
        author.effectiveLines += grading.effectiveLines(additions, deletions, changedFiles);
        if (grading.isRelevantCommit(additions, deletions)) author.relevantCommits++;
        if (day != null) author.activeDays.add(day);
    }

    /** Commit date in the milestones time zone (a commit at 23:30 in Sao Paulo is still that day). */
    private LocalDate dayOf(String isoDate) {
        if (isBlank(isoDate)) return null;
        try {
            return OffsetDateTime.parse(isoDate.trim()).atZoneSameInstant(zone).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** e.g. "=" or "" are not e-mails: text before and after a single "@". */
    public static boolean isValidEmail(String email) {
        if (isBlank(email)) return false;
        String e = email.trim();
        int at = e.indexOf('@');
        return at > 0 && at == e.lastIndexOf('@') && at < e.length() - 1;
    }

    /**
     * Key of a git identity without account (openspec capability identity-aliases): "email:" + normalized e-mail
     * when valid, otherwise "name:" + normalized git name; null when both are missing.
     */
    public static String identityKey(String name, String email) {
        if (isValidEmail(email)) return "email:" + normalize(email);
        if (!isBlank(name)) return "name:" + normalize(name);
        return null;
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
