package br.com.gradechecker.core.application;

import br.com.gradechecker.core.github.Contributor;
import br.com.gradechecker.core.github.GitHubApiException;
import br.com.gradechecker.core.github.GitHubRepository;
import br.com.gradechecker.core.group.Group;
import br.com.gradechecker.core.group.Member;
import br.com.gradechecker.core.group.RepositoryUrl;
import br.com.gradechecker.core.history.HistoryRequest;
import br.com.gradechecker.core.history.RepositoryHistory;
import br.com.gradechecker.core.port.GitHubGateway;
import br.com.gradechecker.core.port.GroupRepository;
import br.com.gradechecker.core.port.IdentityAliasRepository;
import br.com.gradechecker.core.port.MemberRepository;
import br.com.gradechecker.core.port.RepositoryTakenException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** The ports of the library in memory, for the tests of the use cases. */
final class InMemoryPorts {

    private InMemoryPorts() { }

    static final class Groups implements GroupRepository {
        final Map<Long, Group> byId = new LinkedHashMap<>();
        long next = 1;

        Group add(String name, String url, boolean archived) {
            RepositoryUrl parsed = RepositoryUrl.parse(url);
            Group g = new Group(next++, name, url, parsed.getOwner(), parsed.getRepository());
            if (archived) g.setArchivedAt(Instant.EPOCH);
            byId.put(g.getId(), g);
            return g;
        }

        @Override public List<Group> findActive() { List<Group> r = new ArrayList<>(); byId.values().forEach(g -> { if (!g.isArchived()) r.add(g); }); return r; }
        @Override public List<Group> findArchived() { List<Group> r = new ArrayList<>(); byId.values().forEach(g -> { if (g.isArchived()) r.add(g); }); return r; }
        @Override public Group find(long id) { return byId.get(id); }
        @Override public Group findActive(long id) { Group g = byId.get(id); return g == null || g.isArchived() ? null : g; }

        @Override
        public Group findByRepository(String repositoryUrl, Long exceptGroupId) {
            for (Group g : byId.values()) {
                if (RepositoryUrl.normalize(g.getRepositoryUrl()).equals(RepositoryUrl.normalize(repositoryUrl))
                        && (exceptGroupId == null || g.getId() != exceptGroupId)) return g;
            }
            return null;
        }

        @Override
        public long create(Group group) {
            if (findByRepository(group.getRepositoryUrl(), null) != null) throw new RepositoryTakenException();
            group.setId(next++);
            byId.put(group.getId(), group);
            return group.getId();
        }

        @Override
        public boolean update(Group group) {
            if (findActive(group.getId()) == null) return false;
            if (findByRepository(group.getRepositoryUrl(), group.getId()) != null) throw new RepositoryTakenException();
            byId.put(group.getId(), group);
            return true;
        }

        @Override public boolean archive(long id) { Group g = findActive(id); if (g == null) return false; g.setArchivedAt(Instant.EPOCH); return true; }
        @Override public boolean restore(long id) { Group g = byId.get(id); if (g == null || !g.isArchived()) return false; g.setArchivedAt(null); return true; }
    }

    static final class Members implements MemberRepository {
        final Map<Long, Map<String, Member>> byGroup = new HashMap<>();
        final Aliases aliases;

        Members(Aliases aliases) { this.aliases = aliases; }

        private Map<String, Member> of(long groupId) { return byGroup.computeIfAbsent(groupId, k -> new TreeMap<>()); }
        private static String key(String login) { return login.trim().toLowerCase(Locale.ROOT); }

        @Override public List<Member> findByGroupId(long groupId) { return new ArrayList<>(of(groupId).values()); }

        @Override
        public List<String> findLoginsByGroupId(long groupId) {
            List<String> logins = new ArrayList<>();
            for (Member m : of(groupId).values()) logins.add(m.getLogin());
            logins.sort(null);
            return logins;
        }

        @Override public boolean isMember(long groupId, String login) { return of(groupId).containsKey(key(login)); }

        @Override
        public boolean addIfAbsent(long groupId, String login, String name, boolean manual) {
            return of(groupId).putIfAbsent(key(login), new Member(name, login, groupId)) == null;
        }

        @Override public boolean delete(long groupId, String login) { return of(groupId).remove(key(login)) != null; }

        @Override
        public RenameResult rename(long groupId, String login, String newLogin, String name) {
            Map<String, Member> members = of(groupId);
            if (!members.containsKey(key(login))) return RenameResult.NOT_MEMBER;
            if (!key(login).equals(key(newLogin)) && members.containsKey(key(newLogin))) return RenameResult.ALREADY_MEMBER;
            List<String> keys = aliases.keysOfLogin(groupId, login);
            members.remove(key(login));
            members.put(key(newLogin), new Member(name, newLogin, groupId));
            for (String k : keys) aliases.save(groupId, k, newLogin, null);
            return RenameResult.OK;
        }
    }

    static final class Aliases implements IdentityAliasRepository {
        final Map<Long, Map<String, String>> byGroup = new HashMap<>();

        @Override public Map<String, String> findByGroupId(long groupId) { return new TreeMap<>(byGroup.computeIfAbsent(groupId, k -> new TreeMap<>())); }
        @Override public void save(long groupId, String identityKey, String login, String createdBy) { byGroup.computeIfAbsent(groupId, k -> new TreeMap<>()).put(identityKey, login); }
        @Override public boolean delete(long groupId, String identityKey) { return byGroup.computeIfAbsent(groupId, k -> new TreeMap<>()).remove(identityKey) != null; }
    }

    static final class GitHub implements GitHubGateway {
        final Map<String, GitHubRepository> repositories = new HashMap<>();
        final Map<String, Contributor> users = new HashMap<>();
        List<Contributor> contributors = new ArrayList<>();
        GitHubApiException contributorsError;
        /** History of a request (default: no commits). */
        Function<HistoryRequest, RepositoryHistory> history =
                r -> new RepositoryHistory(r.getSince(), r.getUntil(), false, new ArrayList<>());
        final List<HistoryRequest> requests = new ArrayList<>();
        int calls;

        /** GitHub answers for owner/repo (any case) with this spelling. */
        void repository(String typed, String owner, String name, boolean isPrivate) {
            repositories.put(typed.toLowerCase(Locale.ROOT),
                    new GitHubRepository(owner, name, "https://github.com/" + owner + "/" + name, isPrivate));
        }

        void user(String login) {
            users.put(login.toLowerCase(Locale.ROOT), new Contributor(login, 1, null, null, 0));
        }

        @Override
        public GitHubRepository getRepository(String owner, String repo, String accessToken) {
            calls++;
            return repositories.get((owner + "/" + repo).toLowerCase(Locale.ROOT));
        }

        @Override
        public List<Contributor> getContributors(String owner, String repo, String accessToken) {
            calls++;
            if (contributorsError != null) throw contributorsError;
            return contributors;
        }

        @Override
        public Contributor getUser(String login, String accessToken) {
            calls++;
            return users.get(login.toLowerCase(Locale.ROOT));
        }

        @Override
        public RepositoryHistory getHistory(HistoryRequest request) {
            calls++;
            requests.add(request);
            return history.apply(request);
        }
    }
}
