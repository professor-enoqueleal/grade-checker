package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

public class CommitDetail {

    @SerializedName("message")
    private String message;

    @SerializedName("committer")
    private Committer committer;

    public String getMessage() {
        return message;
    }

    public Committer getCommitter() {
        return committer;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCommitter(Committer committer) {
        this.committer = committer;
    }

}
