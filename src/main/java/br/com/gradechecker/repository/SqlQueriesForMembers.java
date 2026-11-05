package br.com.gradechecker.repository;

import br.com.gradechecker.repository.queries.Queries;

public final class SqlQueriesForMembers {

    private SqlQueriesForMembers() {}

    public static final String SELECT_USERNAMES_BY_GROUP_AND_LIST = Queries.get("member.selectUsernamesByGroupIdAndUsernames");

}

