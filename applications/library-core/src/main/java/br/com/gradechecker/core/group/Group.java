package br.com.gradechecker.core.group;

import java.time.Instant;

/** A project group: its name and the GitHub repository whose commits are graded. */
public class Group {

    private long id;
    private String name;
    private String repositoryUrl;
    private String owner;
    private String repository;
    private int memberCount;
    private boolean repositoryPrivate;
    private Instant archivedAt;  // null = active (openspec capability group-management)

    public Group(String name, String repositoryUrl, String owner, String repository) {
        this(0, name, repositoryUrl, owner, repository);
    }

    public Group(long id, String name, String repositoryUrl, String owner, String repository) {
        this.id = id;
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.owner = owner;
        this.repository = repository;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getOwner() { return owner; }
    public String getRepository() { return repository; }
    /** Number of members, filled by the repositories in the lists of groups. */
    public int getMemberCount() { return memberCount; }
    public boolean isRepositoryPrivate() { return repositoryPrivate; }
    public Instant getArchivedAt() { return archivedAt; }
    public boolean isArchived() { return archivedAt != null; }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setRepository(String repository) { this.repository = repository; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public void setRepositoryPrivate(boolean repositoryPrivate) { this.repositoryPrivate = repositoryPrivate; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
