package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

public class Week {

    @SerializedName("w")
    private long week;

    @SerializedName("a")
    private int additions;

    @SerializedName("d")
    private int deletions;

    @SerializedName("c")
    private int commits;

    public long getWeek() {
        return week;
    }

    public void setWeek(long week) {
        this.week = week;
    }

    public int getAdditions() {
        return additions;
    }

    public void setAdditions(int additions) {
        this.additions = additions;
    }

    public int getDeletions() {
        return deletions;
    }

    public void setDeletions(int deletions) {
        this.deletions = deletions;
    }

    public int getCommits() {
        return commits;
    }

    public void setCommits(int commits) {
        this.commits = commits;
    }
}
