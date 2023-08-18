package br.com.gradechecker.model;

public class Group {

    private long id;
    private String name;
    private String repositoryUrl;
    private String owner;
    private String repository;

    public Group() {

    }

    public Group(String name, String repositoryUrl, String owner, String repository) {
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.owner = owner;
        this.repository = repository;
    }

    public Group(long id, String name, String repositoryUrl, String owner, String repository) {
        this.id = id;
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.owner = owner;
        this.repository = repository;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepository() {
        return repository;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

}
