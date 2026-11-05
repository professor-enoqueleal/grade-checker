package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

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

    public ContributorStatsResponseBody(String author, int totalAdditions, int totalDeletions) {
        this(author, 0, totalAdditions, totalDeletions, 0);
    }

    public ContributorStatsResponseBody(String author, int commits, int totalAdditions, int totalDeletions, double percentage) {
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
