package br.com.gradechecker.adapter.outbound.dynamodb;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tables of one test run on DynamoDB Local (DYNAMODB_TEST_ENDPOINT, default http://localhost:8000; start it with
 * scripts/local-dynamodb.sh). Same keys as template.yaml. Used by the *IT tests (mvn test -Pintegration).
 */
public final class LocalTables {

    private final DynamoDbClient client;
    private final String data;
    private final String ephemeral;

    private LocalTables(DynamoDbClient client, String data, String ephemeral) {
        this.client = client;
        this.data = data;
        this.ephemeral = ephemeral;
    }

    public static LocalTables create() {
        String endpoint = System.getenv().getOrDefault("DYNAMODB_TEST_ENDPOINT", "http://localhost:8000");
        DynamoDbClient client = DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalTables tables = new LocalTables(client, "it-data-" + suffix, "it-ephemeral-" + suffix);
        client.createTable(b -> b.tableName(tables.data).billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attribute("PK"), attribute("SK"))
                .keySchema(key("PK", KeyType.HASH), key("SK", KeyType.RANGE)));
        client.createTable(b -> b.tableName(tables.ephemeral).billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attribute("PK")).keySchema(key("PK", KeyType.HASH)));
        return tables;
    }

    private static AttributeDefinition attribute(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    public DynamoClient dynamo() {
        return new DynamoClient(client, data, ephemeral);
    }

    /** Deletes every item of both tables. */
    public void clear() {
        client.scanPaginator(b -> b.tableName(data)).items().forEach(item -> {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", item.get("PK"));
            key.put("SK", item.get("SK"));
            client.deleteItem(d -> d.tableName(data).key(key));
        });
        client.scanPaginator(b -> b.tableName(ephemeral)).items().forEach(item ->
                client.deleteItem(d -> d.tableName(ephemeral).key(Map.of("PK", item.get("PK")))));
    }

    public void drop() {
        client.deleteTable(b -> b.tableName(data));
        client.deleteTable(b -> b.tableName(ephemeral));
    }
}
