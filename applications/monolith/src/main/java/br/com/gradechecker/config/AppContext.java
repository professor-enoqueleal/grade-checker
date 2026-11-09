package br.com.gradechecker.config;

import br.com.gradechecker.adapter.outbound.cache.CaffeineResultCache;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcGroupRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcIdentityAliasRepository;
import br.com.gradechecker.adapter.outbound.jdbc.JdbcMemberRepository;
import br.com.gradechecker.core.adapter.github.GitHubClient;
import br.com.gradechecker.core.adapter.github.GitHubOAuthClient;
import br.com.gradechecker.core.adapter.github.GitHubOAuthSettings;
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

/**
 * Composition root of the Jetty version: the adapters of this application (H2 repositories, Caffeine cache, GitHub
 * App settings) plugged into the ports and use cases of grade-checker-core. Created once, on first use, by the
 * no-argument constructors of the servlets (the container creates them); the tests build the servlets with their own
 * use cases. Manual wiring, no CDI container.
 */
public final class AppContext {

    private static volatile AppContext instance;

    public final PeriodResolver periods;
    public final GradingConfig grading;
    public final GitHubOAuthClient gitHubOAuth;
    public final GroupManagement groups;
    public final Membership membership;
    public final IdentityLinking identityLinking;
    public final ContributionsQuery contributions;
    public final CommitHistoryQuery commitHistory;
    public final ClassGradesQuery classGrades;

    public AppContext(GroupRepository groupRepository, MemberRepository memberRepository, IdentityAliasRepository aliasRepository,
                      GitHubGateway gitHub, PeriodResolver periods, GradingConfig grading) {
        this.periods = periods;
        this.grading = grading;
        this.gitHubOAuth = new GitHubOAuthClient(GitHubOAuthConfig.settings());

        this.groups = new GroupManagement(groupRepository, memberRepository, gitHub);
        this.membership = new Membership(groupRepository, memberRepository, aliasRepository, gitHub, grading);
        this.identityLinking = new IdentityLinking(groupRepository, memberRepository, aliasRepository, grading);
        this.contributions = new ContributionsQuery(groups, memberRepository, aliasRepository, gitHub, periods, grading);
        this.commitHistory = new CommitHistoryQuery(groups, aliasRepository, gitHub, periods, grading);
        this.classGrades = new ClassGradesQuery(groups, membership, aliasRepository, gitHub, periods, grading);
    }

    /** The context of the running application: H2, Caffeine, the GitHub API and the .properties of the classpath. */
    public static AppContext get() {
        AppContext context = instance;
        if (context == null) {
            synchronized (AppContext.class) {
                if (instance == null) {
                    instance = new AppContext(new JdbcGroupRepository(), new JdbcMemberRepository(), new JdbcIdentityAliasRepository(),
                            new GitHubClient(GitHubOAuthSettings.DEFAULT_API_URL, GitHubClient.DEFAULT_API_VERSION, new CaffeineResultCache()), new PeriodResolver(),
                            GradingConfig.fromClasspath());
                }
                context = instance;
            }
        }
        return context;
    }
}
