package br.com.gradechecker.core.group;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL of the repository of a group (openspec capability group-management): https://github.com/<owner>/<repository>,
 * with or without ".git" and a trailing slash. The owner and the repository come from the URL.
 */
public final class RepositoryUrl {

    /** Owner: GitHub login (letters, digits, hyphens); repository: letters, digits, ".", "_" and "-". */
    private static final Pattern URL = Pattern.compile(
            "^https://(?:www\\.)?github\\.com/([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9._-]{1,100})/?$",
            Pattern.CASE_INSENSITIVE);

    public static final String INVALID = "Enter the URL of a GitHub repository (https://github.com/owner/repository).";

    private final String owner;
    private final String repository;

    private RepositoryUrl(String owner, String repository) {
        this.owner = owner;
        this.repository = repository;
    }

    /** @return null when the URL is not a GitHub repository URL */
    public static RepositoryUrl parse(String url) {
        if (url == null) return null;
        Matcher m = URL.matcher(url.trim());
        if (!m.matches()) return null;
        String repository = m.group(2);
        if (repository.toLowerCase(Locale.ROOT).endsWith(".git")) repository = repository.substring(0, repository.length() - 4);
        if (repository.isEmpty() || repository.equals(".") || repository.equals("..")) return null;
        return new RepositoryUrl(m.group(1), repository);
    }

    /**
     * Key to compare repositories: "github.com/owner/repository" in lower case, without scheme, "www.", ".git" and
     * trailing slashes. Also used for URLs already stored (seeds), even when they do not parse.
     */
    public static String normalize(String url) {
        if (url == null) return "";
        String s = url.trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public String getOwner() { return owner; }
    public String getRepository() { return repository; }

}
