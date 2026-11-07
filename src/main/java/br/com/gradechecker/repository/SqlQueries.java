package br.com.gradechecker.repository;

import br.com.gradechecker.repository.queries.Queries;

public final class SqlQueries {

    private SqlQueries() {}

    // GROUPS
    public static final String GROUPS_SELECT_ALL = Queries.get("group.findAll");
    public static final String GROUPS_SELECT_BY_ID = Queries.get("group.findById");
    public static final String GROUPS_INSERT = Queries.get("group.insert");

    // MEMBERS
    public static final String MEMBERS_INSERT = Queries.get("member.insert");
    public static final String MEMBERS_COUNT_BY_GROUP_ID = Queries.get("member.countByGroupId");
    public static final String MEMBERS_SELECT_USERNAMES_BY_GROUP_ID = Queries.get("member.selectUsernamesByGroupId");
    public static final String MEMBERS_COUNT_BY_GROUP_ID_AND_USERNAME = Queries.get("member.countByGroupIdAndUsername");
    public static final String MEMBERS_UPDATE_GITHUB_ID_AND_CONTRIBUTIONS = Queries.get("member.updateGithubIdAndContributions");
    /** Contains %s: fill it ONLY with "?" placeholders. */
    public static final String MEMBERS_SELECT_USERNAMES_BY_GROUP_AND_LIST = Queries.get("member.selectUsernamesByGroupIdAndUsernames");

    // CONTRIBUTORS
    public static final String CONTRIBUTORS_SELECT_BY_GROUP_ID = Queries.get("contributors.findByGroupId");
    public static final String CONTRIBUTORS_INSERT = Queries.get("contributors.insert");
    public static final String CONTRIBUTORS_EXISTS_BY_ID = Queries.get("contributors.existsById");

    // IDENTITY_ALIASES
    public static final String ALIASES_SELECT_BY_GROUP_ID = Queries.get("alias.findByGroupId");
    public static final String ALIASES_SAVE = Queries.get("alias.save");
    public static final String ALIASES_DELETE = Queries.get("alias.delete");

}
