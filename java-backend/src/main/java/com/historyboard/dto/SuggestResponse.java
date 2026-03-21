package com.historyboard.dto;

import java.util.ArrayList;
import java.util.List;

public class SuggestResponse {
    private String query;
    private List<String> suggestions = new ArrayList<>();

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions == null ? new ArrayList<>() : suggestions;
    }
}
