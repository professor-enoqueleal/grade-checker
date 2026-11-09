package br.com.gradechecker.core.history;

import java.util.Collections;
import java.util.List;

public class ContributorStats {
    private String author;
    private int commits;
    private int totalAdditions;
    private int totalDeletions;
    private double percentage;

    /** false when the author has no GitHub account (commits not linked to a login). */
    private boolean linked;

    /** "name <email>" identities of the commits of an author without GitHub account; empty when linked. */
    private List<String> gitIdentities;

    /** Key of an author without account ("email:..." or "name:..."), used to link it manually; null for logins. */
    private String identityKey;

    /** Identities linked manually to this login (openspec capability identity-aliases); empty when none. */
    private List<ManualIdentity> manualIdentities = Collections.emptyList();

    /** A manual association shown on the login row: its key (for Unlink) and its "name <email>" label. */
    public static final class ManualIdentity {
        private final String key;
        private final String identity;

        public ManualIdentity(String key, String identity) {
            this.key = key;
            this.identity = identity;
        }

        public String getKey() {
            return key;
        }

        public String getIdentity() {
            return identity;
        }
    }

    // grading indicators (openspec capability milestone-grading)
    private double effectiveLines;
    private int activeDays;
    private int relevantCommits;

    public ContributorStats(String author, int totalAdditions, int totalDeletions) {
        this(author, 0, totalAdditions, totalDeletions, 0);
    }

    public ContributorStats(String author, int commits, int totalAdditions, int totalDeletions, double percentage) {
        this(author, commits, totalAdditions, totalDeletions, percentage, true, Collections.emptyList());
    }

    public ContributorStats(String author, int commits, int totalAdditions, int totalDeletions, double percentage,
                            boolean linked, List<String> gitIdentities) {
        this.linked = linked;
        this.gitIdentities = gitIdentities;
        this.author = author;
        this.commits = commits;
        this.totalAdditions = totalAdditions;
        this.totalDeletions = totalDeletions;
        this.percentage = percentage;
    }

    public String getAuthor() {
        return author;
    }

    public int getCommits() {
        return commits;
    }

    public int getTotalAdditions() {
        return totalAdditions;
    }

    public int getTotalDeletions() {
        return totalDeletions;
    }

    public double getPercentage() {
        return percentage;
    }

    public boolean isLinked() {
        return linked;
    }

    public List<String> getGitIdentities() {
        return gitIdentities;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public void setIdentityKey(String identityKey) {
        this.identityKey = identityKey;
    }

    public List<ManualIdentity> getManualIdentities() {
        return manualIdentities;
    }

    public void setManualIdentities(List<ManualIdentity> manualIdentities) {
        this.manualIdentities = manualIdentities == null ? Collections.emptyList() : manualIdentities;
    }

    public void setGradingIndicators(double effectiveLines, int activeDays, int relevantCommits) {
        this.effectiveLines = effectiveLines;
        this.activeDays = activeDays;
        this.relevantCommits = relevantCommits;
    }

    public double getEffectiveLines() {
        return effectiveLines;
    }

    public int getActiveDays() {
        return activeDays;
    }

    public int getRelevantCommits() {
        return relevantCommits;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setCommits(int commits) {
        this.commits = commits;
    }

    public void setTotalAdditions(int totalAdditions) {
        this.totalAdditions = totalAdditions;
    }

    public void setTotalDeletions(int totalDeletions) {
        this.totalDeletions = totalDeletions;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

}
