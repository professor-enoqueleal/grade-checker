package br.com.gradechecker.model;

/** Why a commit was counted for an author (openspec capability commit-history-page, rules of contributions-dashboard). */
public enum AttributionSource {
    /** commit linked to the GitHub account */
    ACCOUNT,
    /** identity linked manually by an administrator (identity-aliases) */
    MANUAL,
    /** unlinked commit with the e-mail of a linked commit (rule 2) */
    SAME_EMAIL,
    /** unlinked commit whose git name is exactly the login (rule 2b) */
    SAME_NAME,
    /** author without GitHub account (rules 3 and 4) */
    NO_ACCOUNT
}
