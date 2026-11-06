package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class ContributorStatsResponseBody {

    @SerializedName("author")
    private String author;

    @SerializedName("commits")
    private int commits;

    @SerializedName("totalAdditions")
    private int totalAdditions;

    @SerializedName("totalDeletions")
    private int totalDeletions;

    @SerializedName("percentage")
    private double percentage;

    /** false when the author has no GitHub account (commits not linked to a login). */
    @SerializedName("linked")
    private boolean linked;

    /** "name <email>" identities of the commits of an author without GitHub account; empty when linked. */
    @SerializedName("gitIdentities")
    private List<String> gitIdentities;

    // grading indicators (openspec capability milestone-grading); transient: exposed through gradeDetails only
    private transient double effectiveLines;
    private transient int activeDays;
    private transient int relevantCommits;

    public ContributorStatsResponseBody(String author, int totalAdditions, int totalDeletions) {
        this(author, 0, totalAdditions, totalDeletions, 0);
    }

    public ContributorStatsResponseBody(String author, int commits, int totalAdditions, int totalDeletions, double percentage) {
        this(author, commits, totalAdditions, totalDeletions, percentage, true, Collections.emptyList());
    }

    public ContributorStatsResponseBody(String author, int commits, int totalAdditions, int totalDeletions, double percentage,
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
