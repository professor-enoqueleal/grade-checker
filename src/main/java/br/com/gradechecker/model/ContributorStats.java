package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ContributorStats {

    @SerializedName("author")
    private Contributor author;

    @SerializedName("total")
    private int total;

    @SerializedName("weeks")
    private List<Week> weeks;

    public Contributor getAuthor() {
        return author;
    }

    public void setAuthor(Contributor author) {
        this.author = author;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<Week> getWeeks() {
        return weeks;
    }

    public void setWeeks(List<Week> weeks) {
        this.weeks = weeks;
    }

    // Método para obter a quantidade consolidada de additions e deletions
    public int getTotalAdditions() {
        return weeks.stream().mapToInt(Week::getAdditions).sum();
    }

    public int getTotalDeletions() {
        return weeks.stream().mapToInt(Week::getDeletions).sum();
    }

}
