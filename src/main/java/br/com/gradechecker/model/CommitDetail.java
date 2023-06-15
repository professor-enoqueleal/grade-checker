package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

public class CommitDetail {

    @SerializedName("message")
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
