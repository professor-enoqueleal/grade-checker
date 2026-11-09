package br.com.gradechecker.core.port;

import br.com.gradechecker.core.group.Group;

import java.util.List;

/**
 * Persistence of the groups (capability core-ports), implemented by each application (DynamoDB in the serverless
 * version, JDBC in the Jetty version). A repository URL belongs to one group, active or archived: the implementation
 * reserves it atomically when a group is created or edited.
 */
public interface GroupRepository {

    List<Group> findActive();

    List<Group> findArchived();

    /** The group, active or archived; null when missing. */
    Group find(long id);

    /** The group when it exists and is active; null otherwise. */
    Group findActive(long id);

    /** Another group (active or archived) with this repository; null when free or used only by {@code exceptGroupId}. */
    Group findByRepository(String repositoryUrl, Long exceptGroupId);

    /**
     * Creates the group (its id is set) and reserves its repository.
     *
     * @throws RepositoryTakenException when the repository belongs to another group
     */
    long create(Group group);

    /**
     * Edits name and repository of an active group; a new repository is reserved and the old one released.
     *
     * @return false when the group is missing or archived
     * @throws RepositoryTakenException when the new repository belongs to another group
     */
    boolean update(Group group);

    /** @return false when the group is missing or already archived */
    boolean archive(long id);

    /** @return false when the group is missing or not archived */
    boolean restore(long id);
}
