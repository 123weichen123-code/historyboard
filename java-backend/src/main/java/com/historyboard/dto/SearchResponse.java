package com.historyboard.dto;

import java.util.ArrayList;
import java.util.List;

public class SearchResponse {
    private String query;
    private int count;
    private boolean fallbackUsed;
    private List<EventView> results = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public List<EventView> getResults() {
        return results;
    }

    public void setResults(List<EventView> results) {
        this.results = results == null ? new ArrayList<>() : results;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions == null ? new ArrayList<>() : suggestions;
    }
}
