package com.historyboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class ChinaHistoryEvent {
    private String id;
    private String title;
    private List<String> aliases = new ArrayList<>();
    private String dynasty;

    @JsonProperty("start_year")
    private int startYear;

    @JsonProperty("end_year")
    private int endYear;

    private String location;
    private List<String> figures = new ArrayList<>();
    private String summary;
    private String impact;
    private String category;
    private List<String> tags = new ArrayList<>();
    private double importance;
    private List<String> keywords = new ArrayList<>();
    private String auditStatus;
    private String lastVerifiedAt;
    private List<SourceCitation> sources = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases == null ? new ArrayList<>() : aliases;
    }

    public String getDynasty() {
        return dynasty;
    }

    public void setDynasty(String dynasty) {
        this.dynasty = dynasty;
    }

    public int getStartYear() {
        return startYear;
    }

    public void setStartYear(int startYear) {
        this.startYear = startYear;
    }

    public int getEndYear() {
        return endYear;
    }

    public void setEndYear(int endYear) {
        this.endYear = endYear;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<String> getFigures() {
        return figures;
    }

    public void setFigures(List<String> figures) {
        this.figures = figures == null ? new ArrayList<>() : figures;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public double getImportance() {
        return importance;
    }

    public void setImportance(double importance) {
        this.importance = importance;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? new ArrayList<>() : keywords;
    }

    public String getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(String auditStatus) {
        this.auditStatus = auditStatus;
    }

    public String getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(String lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public List<SourceCitation> getSources() {
        return sources;
    }

    public void setSources(List<SourceCitation> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }
}
