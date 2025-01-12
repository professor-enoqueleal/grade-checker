package br.com.gradechecker.model;


import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ContributorStatsCommand {

    @SerializedName("consolidatedStats")
    private List<ContributorStatsResponseBody> consolidatedStats;

    public ContributorStatsCommand() {

    }

    public ContributorStatsCommand(List<ContributorStatsResponseBody> consolidatedStats) {
        this.consolidatedStats = consolidatedStats;
    }


    public List<ContributorStatsResponseBody> getConsolidatedStats() {
        return consolidatedStats;
    }

    public void setConsolidatedStats(List<ContributorStatsResponseBody> consolidatedStats) {
        this.consolidatedStats = consolidatedStats;
    }

}
