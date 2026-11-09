package br.com.gradechecker.adapter.outbound.dynamodb;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * DynamoDB client and table names (capability dynamodb-data), from the environment:
 * DATA_TABLE, EPHEMERAL_TABLE and DYNAMODB_ENDPOINT (only locally: DynamoDB Local).
 */
public final class DynamoClient {

    private final DynamoDbClient client;
    private final String dataTable;
    private final String ephemeralTable;

    public DynamoClient(DynamoDbClient client, String dataTable, String ephemeralTable) {
        this.client = client;
        this.dataTable = dataTable;
        this.ephemeralTable = ephemeralTable;
    }

    public static DynamoClient fromEnvironment() {
        return new DynamoClient(client(env("DYNAMODB_ENDPOINT")),
                envOr("DATA_TABLE", "grade-checker-data"), envOr("EPHEMERAL_TABLE", "grade-checker-ephemeral"));
    }

    /** Client with the light url-connection HTTP client; endpoint only for DynamoDB Local. */
    public static DynamoDbClient client(String endpoint) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create());
        String region = env("AWS_REGION");
        builder.region(Region.of(region == null ? "us-east-1" : region));
        if (endpoint != null) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    public DynamoDbClient client() { return client; }
    public String dataTable() { return dataTable; }
    public String ephemeralTable() { return ephemeralTable; }

    // ------------------------------------------------------------------------------------------- attribute helpers

    public static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    public static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    public static AttributeValue bool(boolean value) { return AttributeValue.builder().bool(value).build(); }

    public static String str(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        return v == null ? null : v.s();
    }

    public static long num(Map<String, AttributeValue> item, String name, long fallback) {
        AttributeValue v = item.get(name);
        return v == null || v.n() == null ? fallback : Long.parseLong(v.n());
    }

    public static boolean flag(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        return v != null && Boolean.TRUE.equals(v.bool());
    }

    /** Index of the first transaction item whose condition failed, or -1. */
    public static int failedCondition(TransactionCanceledException e) {
        List<CancellationReason> reasons = e.cancellationReasons();
        for (int i = 0; i < reasons.size(); i++) {
            if ("ConditionalCheckFailed".equals(reasons.get(i).code())) return i;
        }
        return -1;
    }

    public static String env(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String envOr(String name, String fallback) {
        String value = env(name);
        return value == null ? fallback : value;
    }
}
