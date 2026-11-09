package br.com.gradechecker.tools;

import br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient;
import br.com.gradechecker.adapter.outbound.dynamodb.LocalTables;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Capability local-compose: tables created only when missing, same keys and TTL as template.yaml. */
public class LocalSetupIT {

    @Test
    public void createsTheMissingTablesOnce() {
        LocalTables helper = LocalTables.create();          // only for the client of DynamoDB Local
        DynamoClient base = helper.dynamo();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        DynamoClient dynamo = new DynamoClient(base.client(), "setup-data-" + suffix, "setup-ephemeral-" + suffix);
        try {
            assertEquals(2, LocalSetup.createTables(dynamo).size());
            assertTrue(LocalSetup.createTables(dynamo).isEmpty());
            assertEquals("expiresAt", base.client().describeTimeToLive(b -> b.tableName(dynamo.ephemeralTable()))
                    .timeToLiveDescription().attributeName());
            assertEquals(2, base.client().describeTable(b -> b.tableName(dynamo.dataTable())).table().keySchema().size());
        } finally {
            base.client().deleteTable(b -> b.tableName(dynamo.dataTable()));
            base.client().deleteTable(b -> b.tableName(dynamo.ephemeralTable()));
            helper.drop();
        }
    }
}
