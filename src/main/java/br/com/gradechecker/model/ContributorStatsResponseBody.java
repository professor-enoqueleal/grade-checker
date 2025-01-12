package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ContributorStatsResponseBody {

    @SerializedName("author")
    private String author;

    @SerializedName("totalAdditions")
    private int totalAdditions;

    @SerializedName("totalDeletions")
    private int totalDeletions;

    public ContributorStatsResponseBody(String author, int totalAdditions, int totalDeletions) {
        this.author = author;
        this.totalAdditions = totalAdditions;
        this.totalDeletions = totalDeletions;
    }

    public String getAuthor() {
        return author;
    }

    public int getTotalAdditions() {
        return totalAdditions;
    }

    public int getTotalDeletions() {
        return totalDeletions;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setTotalAdditions(int totalAdditions) {
        this.totalAdditions = totalAdditions;
    }

    public void setTotalDeletions(int totalDeletions) {
        this.totalDeletions = totalDeletions;
    }

}
