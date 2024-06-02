package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Committer {

    @SerializedName("name")
    private String name;

    @SerializedName("email")
    private String email;

    @SerializedName("date")
    private String date;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDate() {

        Instant instant = Instant.parse(this.date);

        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault()).format(instant);

    }

    public void setDate(String date) {
        this.date = date;
    }

}
