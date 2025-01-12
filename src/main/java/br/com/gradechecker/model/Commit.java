package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

public class Commit {

    @SerializedName("sha")
    private String sha;

    @SerializedName("commit")
    private CommitDetail commit;

    @SerializedName("html_url")
    private String htmlUrl;

    @SerializedName("stats")
    private Stats stats;

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public CommitDetail getCommit() {
        return commit;
    }

    public void setCommit(CommitDetail commit) {
        this.commit = commit;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats;
    }
    
}
