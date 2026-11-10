package br.com.gradechecker.tools;

import br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoGroupRepository;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.RepositoryTakenException;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Seed of the groups of the semester (capability serverless-dynamodb-data): the groups of seed-groups.json (the same of db.sql in
 * the Jetty version) whose repository is not registered yet, active or archived. Never changes existing groups, so it
 * can run any number of times. Run with scripts/seed.sh (DynamoDB Local or AWS, from DATA_TABLE / DYNAMODB_ENDPOINT).
 */
public final class Seed {

    public static final String RESOURCE = "/seed-groups.json";

    private Seed() {
    }

    /** @return number of groups created */
    public static int run(GroupRepository groups) {
        int created = 0;
        for (Map<String, String> seed : load()) {
            String url = seed.get("repositoryUrl");
            if (groups.findByRepository(url, null) != null) continue;
            Group group = new Group(seed.get("name"), url, seed.get("owner"), seed.get("repository"));
            try {
                groups.create(group);
                created++;
            } catch (RepositoryTakenException e) {
                // registered meanwhile: nothing to do
            }
        }
        return created;
    }

    public static List<Map<String, String>> load() {
        try (InputStream in = Seed.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " not found on the classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<List<Map<String, String>>>() { }.getType());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        DynamoClient dynamo = DynamoClient.fromEnvironment();
        int created = run(new DynamoGroupRepository(dynamo));
        System.out.println("Seed: " + created + " group(s) created in " + dynamo.dataTable());
    }
}
