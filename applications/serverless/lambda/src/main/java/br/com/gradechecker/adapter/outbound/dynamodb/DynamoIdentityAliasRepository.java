package br.com.gradechecker.adapter.outbound.dynamodb;

import br.com.gradechecker.core.port.IdentityAliasRepository;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.n;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.s;

/**
 * Manual links of identities without a GitHub account to students (capability identity-aliases):
 * {@code GROUP#0000000003 / ALIAS#<identityKey>} with login, createdBy and createdAt.
 */
public class DynamoIdentityAliasRepository implements IdentityAliasRepository {

    private final DynamoClient dynamo;

    public DynamoIdentityAliasRepository(DynamoClient dynamo) {
        this.dynamo = dynamo;
    }

    static Map<String, AttributeValue> aliasKey(long groupId, String identityKey) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s(DynamoGroupRepository.groupPk(groupId)));
        key.put("SK", s("ALIAS#" + identityKey));
        return key;
    }

    /** identity key -> login, ordered by key. */
    public Map<String, String> findByGroupId(long groupId) {
        Map<String, String> aliases = new TreeMap<>();
        QueryRequest query = QueryRequest.builder().tableName(dynamo.dataTable())
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .expressionAttributeValues(Map.of(":pk", s(DynamoGroupRepository.groupPk(groupId)), ":prefix", s("ALIAS#")))
                .consistentRead(true).build();
        dynamo.client().queryPaginator(query).items().forEach(item ->
                aliases.put(DynamoClient.str(item, "SK").substring("ALIAS#".length()), DynamoClient.str(item, "login")));
        return aliases;
    }

    /** Links (or re-links) the identity to the login. */
    public void save(long groupId, String identityKey, String login, String createdBy) {
        Map<String, AttributeValue> item = aliasKey(groupId, identityKey);
        item.put("login", s(login));
        if (createdBy != null) item.put("createdBy", s(createdBy));
        item.put("createdAt", n(Instant.now().getEpochSecond()));
        dynamo.client().putItem(PutItemRequest.builder().tableName(dynamo.dataTable()).item(item).build());
    }

    /** @return false when the identity was not linked */
    public boolean delete(long groupId, String identityKey) {
        Map<String, AttributeValue> old = dynamo.client().deleteItem(DeleteItemRequest.builder().tableName(dynamo.dataTable())
                .key(aliasKey(groupId, identityKey)).returnValues(ReturnValue.ALL_OLD).build()).attributes();
        return old != null && !old.isEmpty();
    }
}
