package com.historyboard.dto;

import java.util.ArrayList;
import java.util.List;

public class DiscoverResponse {
    private List<String> quickQueries = new ArrayList<>();
    private SearchResponse defaultResults;

    public List<String> getQuickQueries() {
        return quickQueries;
    }

    public void setQuickQueries(List<String> quickQueries) {
        this.quickQueries = quickQueries == null ? new ArrayList<>() : quickQueries;
    }

    public SearchResponse getDefaultResults() {
        return defaultResults;
    }

    public void setDefaultResults(SearchResponse defaultResults) {
        this.defaultResults = defaultResults;
    }
}
