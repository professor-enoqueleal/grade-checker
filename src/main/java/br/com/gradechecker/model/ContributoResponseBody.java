package br.com.gradechecker.model;

import java.util.List;

public class ContributoResponseBody {

    private List<Contributor> contributors;

    public List<Contributor> getContributors() {
        return contributors;
    }

    public void setContributors(List<Contributor> contributors) {
        this.contributors = contributors;
    }

}
