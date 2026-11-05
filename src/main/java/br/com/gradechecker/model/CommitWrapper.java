package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

public class CommitWrapper {

    // matches JSON field 'commit'
    @SerializedName("commit")
    public Commit commit;

    // matches possible top-level 'author' object with 'login'
    @SerializedName("author")
    public Contributor author;

}
