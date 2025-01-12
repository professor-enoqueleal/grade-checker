package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

public class Contributor {

    @SerializedName("login")
    private String login;

    @SerializedName("id")
    private int id;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("html_url")
    private String htmlUrl;

    @SerializedName("contributions")
    private int contributions;

    public Contributor() {

    }

    public Contributor(String login, int id, String avatarUrl, String htmlUrl, int contributions) {
        this.login = login;
        this.id = id;
        this.avatarUrl = avatarUrl;
        this.htmlUrl = htmlUrl;
        this.contributions = contributions;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }


    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public int getContributions() {
        return contributions;
    }

    public void setContributions(int contributions) {
        this.contributions = contributions;
    }

}

