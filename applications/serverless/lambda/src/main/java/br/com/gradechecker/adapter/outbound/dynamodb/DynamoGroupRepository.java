package br.com.gradechecker.adapter.outbound.dynamodb;

import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.group.RepositoryUrl;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.RepositoryTakenException;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.bool;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.n;
import static br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient.s;

/**
 * Groups in the data table (capability serverless-dynamodb-data):
 * <ul>
 *   <li>{@code GROUPS / GROUP#0000000003}: the group (id, name, repository, owner, private, archivedAt, memberCount);</li>
 *   <li>{@code REPO#<normalized url> / REPO}: groupId, so a repository belongs to one group (active or archived);</li>
 *   <li>{@code COUNTER / GROUP}: next group id.</li>
 * </ul>
 */
public class DynamoGroupRepository implements GroupRepository {

    static final String GROUPS = "GROUPS";

    private final DynamoClient dynamo;

    public DynamoGroupRepository(DynamoClient dynamo) {
        this.dynamo = dynamo;
    }

    static String groupSk(long id) { return String.format("GROUP#%010d", id); }
    static String groupPk(long id) { return groupSk(id); }               // partition of the members and aliases
    static String repoPk(String url) { return "REPO#" + RepositoryUrl.normalize(url); }

    static Map<String, AttributeValue> groupKey(long id) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s(GROUPS));
        key.put("SK", s(groupSk(id)));
        return key;
    }

    private static Map<String, AttributeValue> repoKey(String url) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s(repoPk(url)));
        key.put("SK", s("REPO"));
        return key;
    }

    /** Every group, active and archived, by id (one query). */
    public List<Group> findAll() {
        List<Group> groups = new ArrayList<>();
        QueryRequest query = QueryRequest.builder().tableName(dynamo.dataTable())
                .keyConditionExpression("PK = :pk").expressionAttributeValues(Map.of(":pk", s(GROUPS)))
                .consistentRead(true).build();
        dynamo.client().queryPaginator(query).items().forEach(item -> groups.add(toGroup(item)));
        return groups;
    }

    public List<Group> findActive() {
        List<Group> active = new ArrayList<>();
        for (Group g : findAll()) if (!g.isArchived()) active.add(g);
        return active;
    }

    public List<Group> findArchived() {
        List<Group> archived = new ArrayList<>();
        for (Group g : findAll()) if (g.isArchived()) archived.add(g);
        return archived;
    }

    /** The group, active or archived; null when missing. */
    public Group find(long id) {
        Map<String, AttributeValue> item = dynamo.client().getItem(GetItemRequest.builder().tableName(dynamo.dataTable())
                .key(groupKey(id)).consistentRead(true).build()).item();
        return item == null || item.isEmpty() ? null : toGroup(item);
    }

    /** The group when it exists and is active; null otherwise. */
    public Group findActive(long id) {
        Group group = find(id);
        return group == null || group.isArchived() ? null : group;
    }

    /** Another group (active or archived) with this repository; null when free or used only by exceptGroupId. */
    public Group findByRepository(String repositoryUrl, Long exceptGroupId) {
        Map<String, AttributeValue> item = dynamo.client().getItem(GetItemRequest.builder().tableName(dynamo.dataTable())
                .key(repoKey(repositoryUrl)).consistentRead(true).build()).item();
        if (item == null || item.isEmpty()) return null;
        long id = DynamoClient.num(item, "groupId", -1);
        if (exceptGroupId != null && exceptGroupId == id) return null;
        return find(id);
    }

    /**
     * Creates the group with the next id and reserves its repository in one transaction.
     *
     * @throws RepositoryTakenException when the repository belongs to another group
     */
    public long create(Group group) {
        long id = nextId();
        Map<String, AttributeValue> item = groupKey(id);
        item.put("id", n(id));
        item.put("name", s(group.getName()));
        item.put("repositoryUrl", s(group.getRepositoryUrl()));
        item.put("owner", s(group.getOwner()));
        item.put("repository", s(group.getRepository()));
        item.put("private", bool(group.isRepositoryPrivate()));
        item.put("memberCount", n(0));

        Map<String, AttributeValue> repo = repoKey(group.getRepositoryUrl());
        repo.put("groupId", n(id));

        try {
            dynamo.client().transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    TransactWriteItem.builder().put(Put.builder().tableName(dynamo.dataTable()).item(item)
                            .conditionExpression("attribute_not_exists(PK)").build()).build(),
                    TransactWriteItem.builder().put(Put.builder().tableName(dynamo.dataTable()).item(repo)
                            .conditionExpression("attribute_not_exists(PK)").build()).build()
            ).build());
        } catch (TransactionCanceledException e) {
            if (DynamoClient.failedCondition(e) == 1) throw new RepositoryTakenException();
            throw e;
        }
        group.setId(id);
        return id;
    }

    /**
     * Edits name and repository of an active group (members and aliases stay); a new repository is reserved and the
     * old one released in the same transaction.
     *
     * @return false when the group is missing or archived
     * @throws RepositoryTakenException when the new repository belongs to another group
     */
    public boolean update(Group group) {
        Group current = findActive(group.getId());
        if (current == null) return false;

        List<TransactWriteItem> items = new ArrayList<>();
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":name", s(group.getName()));
        values.put(":url", s(group.getRepositoryUrl()));
        values.put(":owner", s(group.getOwner()));
        values.put(":repo", s(group.getRepository()));
        values.put(":private", bool(group.isRepositoryPrivate()));
        items.add(TransactWriteItem.builder().update(Update.builder().tableName(dynamo.dataTable()).key(groupKey(group.getId()))
                .updateExpression("SET #name = :name, repositoryUrl = :url, #owner = :owner, #repository = :repo, #private = :private")
                .conditionExpression("attribute_exists(PK) AND attribute_not_exists(archivedAt)")
                .expressionAttributeNames(Map.of("#name", "name", "#owner", "owner", "#repository", "repository", "#private", "private"))
                .expressionAttributeValues(values).build()).build());

        boolean repositoryChanged = !RepositoryUrl.normalize(current.getRepositoryUrl())
                .equals(RepositoryUrl.normalize(group.getRepositoryUrl()));
        if (repositoryChanged) {
            items.add(TransactWriteItem.builder().delete(Delete.builder().tableName(dynamo.dataTable())
                    .key(repoKey(current.getRepositoryUrl())).build()).build());
            Map<String, AttributeValue> repo = repoKey(group.getRepositoryUrl());
            repo.put("groupId", n(group.getId()));
            items.add(TransactWriteItem.builder().put(Put.builder().tableName(dynamo.dataTable()).item(repo)
                    .conditionExpression("attribute_not_exists(PK)").build()).build());
        }

        try {
            dynamo.client().transactWriteItems(TransactWriteItemsRequest.builder().transactItems(items).build());
            return true;
        } catch (TransactionCanceledException e) {
            int failed = DynamoClient.failedCondition(e);
            if (failed == 0) return false;                      // archived or deleted meanwhile
            if (failed == 2) throw new RepositoryTakenException();
            throw e;
        }
    }

    /** @return false when the group is missing or already archived */
    public boolean archive(long id) {
        return conditionalUpdate(id, "SET archivedAt = :now", "attribute_exists(PK) AND attribute_not_exists(archivedAt)",
                Map.of(":now", n(Instant.now().getEpochSecond())));
    }

    /** @return false when the group is missing or not archived */
    public boolean restore(long id) {
        return conditionalUpdate(id, "REMOVE archivedAt", "attribute_exists(archivedAt)", null);
    }

    private boolean conditionalUpdate(long id, String update, String condition, Map<String, AttributeValue> values) {
        try {
            UpdateItemRequest.Builder request = UpdateItemRequest.builder().tableName(dynamo.dataTable()).key(groupKey(id))
                    .updateExpression(update).conditionExpression(condition);
            if (values != null) request.expressionAttributeValues(values);
            dynamo.client().updateItem(request.build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /** Atomic counter COUNTER / GROUP: 1, 2, 3... */
    long nextId() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s("COUNTER"));
        key.put("SK", s("GROUP"));
        Map<String, AttributeValue> updated = dynamo.client().updateItem(UpdateItemRequest.builder()
                .tableName(dynamo.dataTable()).key(key)
                .updateExpression("ADD #value :one").expressionAttributeNames(Map.of("#value", "value"))
                .expressionAttributeValues(Map.of(":one", n(1))).returnValues(ReturnValue.UPDATED_NEW).build()).attributes();
        return DynamoClient.num(updated, "value", 1);
    }

    static Group toGroup(Map<String, AttributeValue> item) {
        Group group = new Group(DynamoClient.num(item, "id", 0), DynamoClient.str(item, "name"), DynamoClient.str(item, "repositoryUrl"),
                DynamoClient.str(item, "owner"), DynamoClient.str(item, "repository"));
        group.setRepositoryPrivate(DynamoClient.flag(item, "private"));
        group.setMemberCount((int) DynamoClient.num(item, "memberCount", 0));
        long archivedAt = DynamoClient.num(item, "archivedAt", -1);
        if (archivedAt >= 0) group.setArchivedAt(Instant.ofEpochSecond(archivedAt));
        return group;
    }
}
