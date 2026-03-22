package com.historyboard.dto;

import com.historyboard.model.SourceCitation;
import java.util.ArrayList;
import java.util.List;

public class EventView {
    private String id;
    private String title;
    private String dynasty;
    private String category;
    private String period;
    private int startYear;
    private int endYear;
    private String location;
    private String summary;
    private String impact;
    private List<String> figures = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<String> aliases = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();
    private String auditStatus;
    private String lastVerifiedAt;
    private List<SourceCitation> sources = new ArrayList<>();
    private double relevance;
    private List<WorldContextView> worldContext = new ArrayList<>();
    private String timelineScope;
    private String region;

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

    public String getDynasty() {
        return dynasty;
    }

    public void setDynasty(String dynasty) {
        this.dynasty = dynasty;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
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

    public List<String> getFigures() {
        return figures;
    }

    public void setFigures(List<String> figures) {
        this.figures = figures == null ? new ArrayList<>() : figures;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases == null ? new ArrayList<>() : aliases;
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

    public double getRelevance() {
        return relevance;
    }

    public void setRelevance(double relevance) {
        this.relevance = relevance;
    }

    public List<WorldContextView> getWorldContext() {
        return worldContext;
    }

    public void setWorldContext(List<WorldContextView> worldContext) {
        this.worldContext = worldContext == null ? new ArrayList<>() : worldContext;
    }

    public String getTimelineScope() {
        return timelineScope;
    }

    public void setTimelineScope(String timelineScope) {
        this.timelineScope = timelineScope;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
