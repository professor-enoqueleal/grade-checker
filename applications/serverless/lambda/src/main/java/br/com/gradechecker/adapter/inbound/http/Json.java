package br.com.gradechecker.adapter.inbound.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Gson of the API: nulls are written (the SPA reads "grading": null, "activity": null...). */
public final class Json {

    public static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private Json() {
    }
}
