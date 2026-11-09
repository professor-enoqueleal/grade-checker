package br.com.gradechecker.lambda;

import br.com.gradechecker.adapter.outbound.cache.TwoLevelResultCache;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoClient;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoGroupRepository;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoIdentityAliasRepository;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoMemberRepository;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoResultCache;
import br.com.gradechecker.adapter.outbound.dynamodb.DynamoSessionStore;
import br.com.gradechecker.config.GitHubOAuthConfig;
import br.com.gradechecker.core.adapter.github.GitHubClient;
import br.com.gradechecker.core.adapter.github.GitHubOAuthClient;
import br.com.gradechecker.core.application.ClassGradesQuery;
import br.com.gradechecker.core.application.CommitHistoryQuery;
import br.com.gradechecker.core.application.ContributionsQuery;
import br.com.gradechecker.core.application.GroupManagement;
import br.com.gradechecker.core.application.IdentityLinking;
import br.com.gradechecker.core.application.Membership;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.IdentityAliasRepository;
import br.com.gradechecker.core.port.MemberRepository;
import br.com.gradechecker.core.port.ResultCache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.time.Clock;
import java.util.function.Supplier;

/**
 * Composition root of the Lambda: the adapters (DynamoDB, GitHub, cache) plugged into the ports and use cases of
 * grade-checker-core, created once per instance (static init of the handler, so they enter the SnapStart snapshot).
 * Manual wiring, no CDI container (decision 2.6 of the migration plan).
 */
public final class AppContext {

    private static final Logger logger = LogManager.getLogger(AppContext.class);

    public final PeriodResolver periods;
    public final GradingConfig grading;
    public final DynamoSessionStore sessions;
    public final GitHubOAuthClient gitHubOAuth;
    public final GroupManagement groups;
    public final Membership membership;
    public final IdentityLinking identityLinking;
    public final ContributionsQuery contributions;
    public final CommitHistoryQuery commitHistory;
    public final ClassGradesQuery classGrades;

    public AppContext(DynamoClient dynamo, GitHubGateway gitHub, GitHubOAuthClient gitHubOAuth, PeriodResolver periods,
                      GradingConfig grading, Clock clock) {
        GroupRepository groupRepository = new DynamoGroupRepository(dynamo);
        MemberRepository memberRepository = new DynamoMemberRepository(dynamo);
        IdentityAliasRepository aliasRepository = new DynamoIdentityAliasRepository(dynamo);

        this.periods = periods;
        this.grading = grading;
        this.sessions = new DynamoSessionStore(dynamo, clock);
        this.gitHubOAuth = gitHubOAuth;

        this.groups = new GroupManagement(groupRepository, memberRepository, gitHub);
        this.membership = new Membership(groupRepository, memberRepository, aliasRepository, gitHub, grading);
        this.identityLinking = new IdentityLinking(groupRepository, memberRepository, aliasRepository, grading);
        this.contributions = new ContributionsQuery(groups, memberRepository, aliasRepository, gitHub, periods, grading);
        this.commitHistory = new CommitHistoryQuery(groups, aliasRepository, gitHub, periods, grading);
        this.classGrades = new ClassGradesQuery(groups, membership, aliasRepository, gitHub, periods, grading);
    }

    /** Production wiring, from the environment of the Lambda. */
    public static AppContext fromEnvironment() {
        DynamoClient dynamo = DynamoClient.fromEnvironment();
        Clock clock = Clock.systemUTC();
        ResultCache cache = TwoLevelResultCache.fromEnvironment(() -> new DynamoResultCache(dynamo, clock, TwoLevelResultCache.ttlSeconds()));
        GitHubOAuthConfig.setSecretSupplier(ssmSecret(System.getenv("GITHUB_CLIENT_SECRET_PARAMETER")));
        GradingConfig grading = GradingConfig.fromClasspath();
        // grade-checker-core: the GitHub client gets this application's cache and GitHub App settings
        return new AppContext(dynamo, new GitHubClient(GitHubOAuthConfig.apiUrl(), GitHubClient.DEFAULT_API_VERSION, cache),
                new GitHubOAuthClient(GitHubOAuthConfig.settings()), new PeriodResolver(), grading, clock);
    }

    /**
     * Client secret of the GitHub App from an SSM SecureString, read on first use (after the SnapStart restore, never
     * in the snapshot) and kept in the instance. Null supplier when no parameter is configured (local: env variable).
     */
    static Supplier<String> ssmSecret(String parameter) {
        if (parameter == null || parameter.isBlank()) return null;
        return new Supplier<>() {
            private volatile String value;

            @Override
            public String get() {
                if (value == null) {
                    synchronized (this) {
                        if (value == null) {
                            String region = System.getenv("AWS_REGION");
                            try (SsmClient ssm = SsmClient.builder().httpClient(UrlConnectionHttpClient.create())
                                    .region(Region.of(region == null ? "us-east-1" : region)).build()) {
                                value = ssm.getParameter(GetParameterRequest.builder().name(parameter.trim())
                                        .withDecryption(true).build()).parameter().value();
                            } catch (RuntimeException e) {
                                logger.error("Could not read the GitHub client secret from SSM ({}): {}", parameter, e.getMessage());
                                return null;
                            }
                        }
                    }
                }
                return value;
            }
        };
    }
}
