package br.com.gradechecker.tools;

import br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoGroupRepository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Setup of the local database (capability local-compose): waits for DynamoDB Local, creates the two tables when they
 * are missing (same keys and TTL as template.yaml) and runs the seed. Refuses to run without DYNAMODB_ENDPOINT, so it
 * never creates tables in AWS. Used by the "setup" service of serverless/docker-compose.yml.
 */
public final class LocalSetup {

    private LocalSetup() {
    }

    public static void main(String[] args) throws InterruptedException {
        String endpoint = DynamoClient.env("DYNAMODB_ENDPOINT");
        if (endpoint == null) {
            System.err.println("LocalSetup: DYNAMODB_ENDPOINT is not set; this command only runs against DynamoDB Local.");
            System.exit(1);
            return;
        }
        DynamoClient dynamo = new DynamoClient(DynamoClient.client(endpoint),
                envOr("DATA_TABLE", "grade-checker-data"), envOr("EPHEMERAL_TABLE", "grade-checker-ephemeral"));
        waitFor(dynamo.client(), endpoint);
        List<String> created = createTables(dynamo);
        System.out.println("LocalSetup: tables " + (created.isEmpty() ? "already exist" : "created: " + created));
        System.out.println("LocalSetup: seed created " + Seed.run(new DynamoGroupRepository(dynamo)) + " group(s)");
    }

    /** Creates the missing tables; returns their names. */
    public static List<String> createTables(DynamoClient dynamo) {
        DynamoDbClient client = dynamo.client();
        List<String> existing = client.listTables().tableNames();
        List<String> created = new ArrayList<>();
        if (!existing.contains(dynamo.dataTable())) {
            client.createTable(b -> b.tableName(dynamo.dataTable()).billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(attribute("PK"), attribute("SK"))
                    .keySchema(key("PK", KeyType.HASH), key("SK", KeyType.RANGE)));
            created.add(dynamo.dataTable());
        }
        if (!existing.contains(dynamo.ephemeralTable())) {
            client.createTable(b -> b.tableName(dynamo.ephemeralTable()).billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(attribute("PK")).keySchema(key("PK", KeyType.HASH)));
            client.updateTimeToLive(b -> b.tableName(dynamo.ephemeralTable())
                    .timeToLiveSpecification(t -> t.enabled(true).attributeName("expiresAt")));
            created.add(dynamo.ephemeralTable());
        }
        return created;
    }

    private static void waitFor(DynamoDbClient client, String endpoint) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                client.listTables();
                return;
            } catch (RuntimeException e) {
                if (attempt >= 30) throw new IllegalStateException("DynamoDB Local did not answer at " + endpoint, e);
                Thread.sleep(1000);
            }
        }
    }

    private static AttributeDefinition attribute(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    private static String envOr(String name, String fallback) {
        String value = DynamoClient.env(name);
        return value == null ? fallback : value;
    }
}
