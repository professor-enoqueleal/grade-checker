package br.com.gradechecker.repository;

import br.com.gradechecker.repository.queries.Queries;

public final class SqlQueries {

    private SqlQueries() {}

    // GROUPS
    public static final String GROUPS_SELECT_ALL = Queries.get("group.findAll");
    public static final String GROUPS_SELECT_ARCHIVED = Queries.get("group.findArchived");
    public static final String GROUPS_SELECT_REPOSITORIES = Queries.get("group.findRepositories");
    public static final String GROUPS_UPDATE = Queries.get("group.update");
    public static final String GROUPS_ARCHIVE = Queries.get("group.archive");
    public static final String GROUPS_RESTORE = Queries.get("group.restore");
    public static final String GROUPS_SELECT_BY_ID = Queries.get("group.findById");
    public static final String GROUPS_INSERT = Queries.get("group.insert");

    // MEMBERS (openspec capability group-members)
    public static final String MEMBERS_INSERT = Queries.get("member.insert");
    public static final String MEMBERS_COUNT_BY_GROUP_ID = Queries.get("member.countByGroupId");
    public static final String MEMBERS_COUNT_ALL_GROUPS = Queries.get("member.countAllGroups");
    public static final String MEMBERS_SELECT_USERNAMES_BY_GROUP_ID = Queries.get("member.selectUsernamesByGroupId");
    public static final String MEMBERS_SELECT_BY_GROUP_ID = Queries.get("member.findByGroupId");
    public static final String MEMBERS_COUNT_BY_GROUP_ID_AND_USERNAME = Queries.get("member.countByGroupIdAndUsername");
    public static final String MEMBERS_DELETE = Queries.get("member.delete");
    public static final String MEMBERS_UPDATE = Queries.get("member.update");

    // IDENTITY_ALIASES
    public static final String ALIASES_SELECT_BY_GROUP_ID = Queries.get("alias.findByGroupId");
    public static final String ALIASES_SAVE = Queries.get("alias.save");
    public static final String ALIASES_DELETE = Queries.get("alias.delete");
    public static final String ALIASES_EXISTS_BY_LOGIN = Queries.get("alias.existsByLogin");
    public static final String ALIASES_UPDATE_LOGIN = Queries.get("alias.updateLogin");

}
