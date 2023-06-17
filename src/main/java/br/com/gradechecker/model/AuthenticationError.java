package br.com.gradechecker.model;

import com.google.gson.annotations.SerializedName;

public class AuthenticationError {

    @SerializedName("error")
    private String error;

    @SerializedName("error_description")
    private String errorDescription;

    @SerializedName("error_uri")
    private String errorUri;

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public String getErrorUri() {
        return errorUri;
    }

    public void setErrorUri(String errorUri) {
        this.errorUri = errorUri;
    }

}
