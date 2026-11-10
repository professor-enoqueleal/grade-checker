package br.com.gradechecker.adapter.outbound.dynamodb;

import br.com.gradechecker.core.group.Member;
import br.com.gradechecker.core.port.MemberRepository;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.bool;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.n;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.s;

/**
 * Members (students) of a group (capability serverless-dynamodb-data): {@code GROUP#0000000003 / MEMBER#<lower-case login>},
 * with login (GitHub spelling), name and createdManually. The memberCount of the group changes in the same transaction.
 */
public class DynamoMemberRepository implements MemberRepository {

    private final DynamoClient dynamo;
    private final DynamoIdentityAliasRepository aliases;

    public DynamoMemberRepository(DynamoClient dynamo) {
        this.dynamo = dynamo;
        this.aliases = new DynamoIdentityAliasRepository(dynamo);
    }

    static String memberSk(String login) { return "MEMBER#" + login.trim().toLowerCase(Locale.ROOT); }

    static Map<String, AttributeValue> memberKey(long groupId, String login) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s(DynamoGroupRepository.groupPk(groupId)));
        key.put("SK", s(memberSk(login)));
        return key;
    }

    /** Members of the group, by login ignoring case. */
    public List<Member> findByGroupId(long groupId) {
        List<Member> members = new ArrayList<>();
        QueryRequest query = QueryRequest.builder().tableName(dynamo.dataTable())
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .expressionAttributeValues(Map.of(":pk", s(DynamoGroupRepository.groupPk(groupId)), ":prefix", s("MEMBER#")))
                .consistentRead(true).build();
        dynamo.client().queryPaginator(query).items().forEach(item ->
                members.add(new Member(DynamoClient.str(item, "name"), DynamoClient.str(item, "login"), groupId)));
        return members;
    }

    /** GitHub logins of the members, sorted (as MEMBERS ORDER BY GITHUB_USERNAME of the Jetty version). */
    public List<String> findLoginsByGroupId(long groupId) {
        List<String> logins = new ArrayList<>();
        for (Member m : findByGroupId(groupId)) logins.add(m.getLogin());
        logins.sort(null);
        return logins;
    }

    public boolean isMember(long groupId, String login) {
        Map<String, AttributeValue> item = dynamo.client().getItem(GetItemRequest.builder().tableName(dynamo.dataTable())
                .key(memberKey(groupId, login)).consistentRead(true).build()).item();
        return item != null && !item.isEmpty();
    }

    /** @return false when the login (ignoring case) is already a member */
    public boolean addIfAbsent(long groupId, String login, String name, boolean manual) {
        Map<String, AttributeValue> item = memberKey(groupId, login);
        item.put("login", s(login));
        item.put("name", s(name));
        item.put("createdManually", bool(manual));
        try {
            dynamo.client().transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    TransactWriteItem.builder().put(Put.builder().tableName(dynamo.dataTable()).item(item)
                            .conditionExpression("attribute_not_exists(SK)").build()).build(),
                    countChange(groupId, 1)).build());
            return true;
        } catch (TransactionCanceledException e) {
            if (DynamoClient.failedCondition(e) == 0) return false;
            throw e;
        }
    }

    /** @return false when the login is not a member */
    public boolean delete(long groupId, String login) {
        try {
            dynamo.client().transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    TransactWriteItem.builder().delete(Delete.builder().tableName(dynamo.dataTable())
                            .key(memberKey(groupId, login)).conditionExpression("attribute_exists(SK)").build()).build(),
                    countChange(groupId, -1)).build());
            return true;
        } catch (TransactionCanceledException e) {
            if (DynamoClient.failedCondition(e) == 0) return false;
            throw e;
        }
    }

    /**
     * Changes name and login of a member in one transaction; the manual identity links of the old login move to the
     * new one (capability identity-aliases).
     */
    @Override
    public RenameResult rename(long groupId, String login, String newLogin, String name) {
        if (memberSk(login).equals(memberSk(newLogin))) {
            Map<String, AttributeValue> values = Map.of(":login", s(newLogin), ":name", s(name));
            try {
                dynamo.client().transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                        TransactWriteItem.builder().update(Update.builder().tableName(dynamo.dataTable())
                                .key(memberKey(groupId, login)).updateExpression("SET login = :login, #name = :name")
                                .expressionAttributeNames(Map.of("#name", "name")).expressionAttributeValues(values)
                                .conditionExpression("attribute_exists(SK)").build()).build()).build());
                return RenameResult.OK;
            } catch (TransactionCanceledException e) {
                if (DynamoClient.failedCondition(e) == 0) return RenameResult.NOT_MEMBER;
                throw e;
            }
        }

        Map<String, AttributeValue> current = dynamo.client().getItem(GetItemRequest.builder().tableName(dynamo.dataTable())
                .key(memberKey(groupId, login)).consistentRead(true).build()).item();
        if (current == null || current.isEmpty()) return RenameResult.NOT_MEMBER;

        List<String> aliasKeysOfLogin = aliases.keysOfLogin(groupId, login);
        Map<String, AttributeValue> item = memberKey(groupId, newLogin);
        item.put("login", s(newLogin));
        item.put("name", s(name));
        item.put("createdManually", bool(DynamoClient.flag(current, "createdManually")));

        List<TransactWriteItem> items = new ArrayList<>();
        items.add(TransactWriteItem.builder().delete(Delete.builder().tableName(dynamo.dataTable())
                .key(memberKey(groupId, login)).conditionExpression("attribute_exists(SK)").build()).build());
        items.add(TransactWriteItem.builder().put(Put.builder().tableName(dynamo.dataTable()).item(item)
                .conditionExpression("attribute_not_exists(SK)").build()).build());
        for (String key : aliasKeysOfLogin) {
            items.add(TransactWriteItem.builder().update(Update.builder().tableName(dynamo.dataTable())
                    .key(DynamoIdentityAliasRepository.aliasKey(groupId, key)).updateExpression("SET login = :login")
                    .expressionAttributeValues(Map.of(":login", s(newLogin))).conditionExpression("attribute_exists(SK)")
                    .build()).build());
        }
        try {
            dynamo.client().transactWriteItems(TransactWriteItemsRequest.builder().transactItems(items).build());
            return RenameResult.OK;
        } catch (TransactionCanceledException e) {
            int failed = DynamoClient.failedCondition(e);
            if (failed == 0) return RenameResult.NOT_MEMBER;
            if (failed == 1) return RenameResult.ALREADY_MEMBER;
            throw e;
        }
    }

    private TransactWriteItem countChange(long groupId, int delta) {
        return TransactWriteItem.builder().update(Update.builder().tableName(dynamo.dataTable())
                .key(DynamoGroupRepository.groupKey(groupId)).updateExpression("ADD memberCount :delta")
                .expressionAttributeValues(Map.of(":delta", n(delta))).conditionExpression("attribute_exists(PK)")
                .build()).build();
    }
}
