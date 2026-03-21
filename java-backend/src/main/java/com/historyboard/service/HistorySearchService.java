package com.historyboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historyboard.dto.DiscoverResponse;
import com.historyboard.dto.EventView;
import com.historyboard.dto.SearchResponse;
import com.historyboard.dto.TimelineResponse;
import com.historyboard.dto.WorldContextView;
import com.historyboard.model.ChinaHistoryEvent;
import com.historyboard.model.WorldHistoryEvent;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HistorySearchService {

    private static final Pattern PUNCT_PATTERN = Pattern.compile("[\\s\\-_,，。！？!?:：；;()（）【】\\[\\]《》'\\\"`]+");
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,，、/]+");
    private static final Pattern YEAR_PATTERN = Pattern.compile("-?\\d{2,4}");

    @Value("${historyboard.data-dir:../backend/app/data}")
    private String dataDir;

    private final ObjectMapper objectMapper;

    private final List<ChinaHistoryEvent> chinaEvents = new ArrayList<>();
    private final List<WorldHistoryEvent> worldEvents = new ArrayList<>();
    private final List<IndexedEvent> indexedEvents = new ArrayList<>();
    private final Map<String, ChinaHistoryEvent> eventsById = new HashMap<>();
    private final List<String> suggestions = new ArrayList<>();

    public HistorySearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadData();
        buildIndex();
    }

    public SearchResponse search(String query, int limit) {
        String cleanQuery = query == null ? "" : query.trim();
        String normalizedQuery = normalize(cleanQuery);

        List<ScoreRow> scored = new ArrayList<>();
        for (IndexedEvent indexed : indexedEvents) {
            double score = score(indexed, cleanQuery, normalizedQuery);
            if (score >= 14) {
                scored.add(new ScoreRow(score, indexed.event));
            }
        }

        scored.sort(
            Comparator.comparingDouble(ScoreRow::score).reversed()
                .thenComparingInt(row -> row.event().getStartYear())
        );

        boolean fallbackUsed = false;
        if (scored.isEmpty()) {
            fallbackUsed = true;
            chinaEvents.stream()
                .sorted(
                    Comparator.comparingDouble(ChinaHistoryEvent::getImportance).reversed()
                        .thenComparingInt(event -> Math.abs(event.getStartYear()))
                )
                .limit(limit)
                .forEach(event -> scored.add(new ScoreRow(event.getImportance() * 10, event)));
        }

        List<EventView> views = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            ScoreRow row = scored.get(i);
            views.add(toEventView(row.event(), row.score(), false));
        }

        for (int i = 0; i < Math.min(3, views.size()); i++) {
            EventView view = views.get(i);
            view.setWorldContext(getWorldContext(view.getStartYear(), view.getEndYear(), 6, 90));
        }

        SearchResponse response = new SearchResponse();
        response.setQuery(cleanQuery);
        response.setCount(views.size());
        response.setFallbackUsed(fallbackUsed);
        response.setResults(views);
        response.setSuggestions(suggest(cleanQuery, 10));
        return response;
    }

    public List<String> suggest(String query, int limit) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) {
            return featuredSuggestions(limit);
        }

        String normalizedQuery = normalize(cleanQuery);
        List<String> matched = new ArrayList<>();
        for (String candidate : suggestions) {
            if (normalize(candidate).contains(normalizedQuery)) {
                matched.add(candidate);
                if (matched.size() >= limit) {
                    break;
                }
            }
        }
        return matched;
    }

    public EventView eventDetail(String eventId) {
        ChinaHistoryEvent event = eventsById.get(eventId);
        if (event == null) {
            return null;
        }

        EventView view = toEventView(event, event.getImportance() * 100, true);
        view.setWorldContext(getWorldContext(view.getStartYear(), view.getEndYear(), 8, 90));
        return view;
    }

    public TimelineResponse timeline() {
        TimelineResponse response = new TimelineResponse();
        List<EventView> events = new ArrayList<>();
        for (ChinaHistoryEvent event : chinaEvents) {
            EventView view = new EventView();
            view.setId(event.getId());
            view.setTitle(event.getTitle());
            view.setDynasty(defaultString(event.getDynasty()));
            view.setCategory(defaultString(event.getCategory()));
            view.setStartYear(event.getStartYear());
            view.setEndYear(event.getEndYear());
            view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
            events.add(view);
        }
        response.setCount(events.size());
        response.setEvents(events);
        return response;
    }

    public DiscoverResponse discover() {
        DiscoverResponse response = new DiscoverResponse();
        response.setQuickQueries(featuredSuggestions(12));
        response.setDefaultResults(search("", 8));
        return response;
    }

    private void loadData() {
        Path base = Path.of(dataDir).normalize();
        Path chinaPath = base.resolve("china_history_events.json");
        Path worldPath = base.resolve("world_history_events.json");

        if (!Files.exists(chinaPath) || !Files.exists(worldPath)) {
            throw new IllegalStateException(
                "History data files not found. Expected: " + chinaPath + " and " + worldPath
            );
        }

        try {
            List<ChinaHistoryEvent> china = objectMapper.readValue(
                Files.readString(chinaPath),
                new TypeReference<>() {}
            );
            List<WorldHistoryEvent> world = objectMapper.readValue(
                Files.readString(worldPath),
                new TypeReference<>() {}
            );

            chinaEvents.clear();
            worldEvents.clear();
            chinaEvents.addAll(china);
            worldEvents.addAll(world);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load history dataset", e);
        }
    }

    private void buildIndex() {
        indexedEvents.clear();
        eventsById.clear();
        suggestions.clear();

        Set<String> candidateSet = new HashSet<>();
        for (ChinaHistoryEvent event : chinaEvents) {
            eventsById.put(event.getId(), event);
            indexedEvents.add(new IndexedEvent(event, buildSearchBlob(event)));

            candidateSet.add(defaultString(event.getTitle()));
            candidateSet.add(defaultString(event.getDynasty()));
            candidateSet.add(defaultString(event.getCategory()));
            candidateSet.addAll(event.getAliases());
            candidateSet.addAll(event.getFigures());
            candidateSet.addAll(event.getTags());
            candidateSet.addAll(event.getKeywords());
        }

        candidateSet.stream()
            .filter(item -> !item.isBlank() && item.length() >= 2)
            .sorted(Comparator.comparingInt(String::length).thenComparing(item -> item))
            .limit(500)
            .forEach(suggestions::add);

        chinaEvents.sort(Comparator.comparingInt(ChinaHistoryEvent::getStartYear).thenComparing(ChinaHistoryEvent::getId));
        worldEvents.sort(Comparator.comparingInt(WorldHistoryEvent::getStartYear).thenComparing(WorldHistoryEvent::getId));
    }

    private String buildSearchBlob(ChinaHistoryEvent event) {
        StringBuilder sb = new StringBuilder();
        appendNormalized(sb, event.getTitle());
        appendNormalized(sb, event.getDynasty());
        appendNormalized(sb, event.getSummary());
        appendNormalized(sb, event.getImpact());
        appendNormalized(sb, event.getLocation());
        appendNormalized(sb, event.getCategory());
        appendNormalized(sb, String.join(" ", event.getAliases()));
        appendNormalized(sb, String.join(" ", event.getFigures()));
        appendNormalized(sb, String.join(" ", event.getTags()));
        appendNormalized(sb, String.join(" ", event.getKeywords()));
        return sb.toString();
    }

    private void appendNormalized(StringBuilder sb, String text) {
        String value = normalize(defaultString(text));
        if (!value.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(value);
        }
    }

    private double score(IndexedEvent indexed, String rawQuery, String normalizedQuery) {
        ChinaHistoryEvent event = indexed.event;
        double score = event.getImportance() * 14;

        if (normalizedQuery.isBlank()) {
            return score;
        }

        String title = normalize(event.getTitle());
        String aliases = normalize(String.join(" ", event.getAliases()));
        String keywords = normalize(String.join(" ", event.getKeywords()));
        String figures = normalize(String.join(" ", event.getFigures()));
        String dynasty = normalize(event.getDynasty());
        String blob = indexed.searchable;

        if (title.contains(normalizedQuery)) {
            score += 80;
        }
        if (aliases.contains(normalizedQuery)) {
            score += 60;
        }
        if (keywords.contains(normalizedQuery)) {
            score += 55;
        }
        if (figures.contains(normalizedQuery)) {
            score += 52;
        }
        if (dynasty.contains(normalizedQuery)) {
            score += 48;
        }
        if (blob.contains(normalizedQuery)) {
            score += 30;
        }

        for (String token : SPLIT_PATTERN.split(rawQuery.toLowerCase(Locale.ROOT))) {
            String piece = normalize(token);
            if (piece.length() < 2) {
                continue;
            }
            if (title.contains(piece)) {
                score += 18;
            } else if (blob.contains(piece)) {
                score += 9;
            }
        }

        score += overlapRatio(normalizedQuery, blob) * 20;

        List<Integer> years = extractYears(rawQuery);
        for (Integer year : years) {
            int low = Math.min(event.getStartYear(), event.getEndYear());
            int high = Math.max(event.getStartYear(), event.getEndYear());
            if (year >= low && year <= high) {
                score += 70;
                continue;
            }

            int distance = Math.min(Math.abs(year - low), Math.abs(year - high));
            score += Math.max(0, 36 - distance / 10.0);
        }

        return Math.round(score * 1000.0) / 1000.0;
    }

    private List<WorldContextView> getWorldContext(int startYear, int endYear, int limit, int window) {
        int low = Math.min(startYear, endYear);
        int high = Math.max(startYear, endYear);
        double center = (low + high) / 2.0;

        List<WorldRankRow> scored = new ArrayList<>();
        for (WorldHistoryEvent event : worldEvents) {
            int worldLow = Math.min(event.getStartYear(), event.getEndYear());
            int worldHigh = Math.max(event.getStartYear(), event.getEndYear());

            boolean intersects = !(worldHigh < (low - window) || worldLow > (high + window));
            if (!intersects) {
                continue;
            }

            double worldCenter = (worldLow + worldHigh) / 2.0;
            double distance = Math.abs(center - worldCenter);
            double score = Math.max(0.1, 120 - distance);
            scored.add(new WorldRankRow(score, event));
        }

        scored.sort(
            Comparator.comparingDouble(WorldRankRow::score).reversed()
                .thenComparingInt(row -> row.event().getStartYear())
        );

        List<WorldContextView> views = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            WorldHistoryEvent event = scored.get(i).event();
            WorldContextView view = new WorldContextView();
            view.setId(event.getId());
            view.setTitle(event.getTitle());
            view.setRegion(defaultString(event.getRegion()));
            view.setStartYear(event.getStartYear());
            view.setEndYear(event.getEndYear());
            view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
            view.setSummary(defaultString(event.getSummary()));
            views.add(view);
        }
        return views;
    }

    private EventView toEventView(ChinaHistoryEvent event, double relevance, boolean includeKeywords) {
        EventView view = new EventView();
        view.setId(event.getId());
        view.setTitle(event.getTitle());
        view.setDynasty(defaultString(event.getDynasty()));
        view.setCategory(defaultString(event.getCategory()));
        view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
        view.setStartYear(event.getStartYear());
        view.setEndYear(event.getEndYear());
        view.setLocation(defaultString(event.getLocation()));
        view.setSummary(defaultString(event.getSummary()));
        view.setImpact(defaultString(event.getImpact()));
        view.setFigures(event.getFigures());
        view.setTags(event.getTags());
        view.setAliases(event.getAliases());
        view.setAuditStatus(defaultString(event.getAuditStatus()));
        view.setLastVerifiedAt(defaultString(event.getLastVerifiedAt()));
        view.setSources(event.getSources());
        view.setRelevance(relevance);
        if (includeKeywords) {
            view.setKeywords(event.getKeywords());
        }
        return view;
    }

    private List<String> featuredSuggestions(int limit) {
        List<String> featured = List.of(
            "秦始皇", "丝绸之路", "安史之乱", "靖康之变", "甲午战争", "辛亥革命",
            "改革开放", "香港回归", "WTO", "五四运动", "鸦片战争", "郑和下西洋"
        );

        List<String> merged = new ArrayList<>(featured);
        for (String item : suggestions) {
            if (!merged.contains(item)) {
                merged.add(item);
            }
            if (merged.size() >= limit) {
                break;
            }
        }
        if (merged.size() > limit) {
            return merged.subList(0, limit);
        }
        return merged;
    }

    private List<Integer> extractYears(String text) {
        List<Integer> years = new ArrayList<>();
        Matcher matcher = YEAR_PATTERN.matcher(defaultString(text));
        while (matcher.find()) {
            try {
                years.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignore) {
                // ignore malformed token
            }
        }
        return years;
    }

    private double overlapRatio(String queryText, String targetText) {
        if (queryText == null || queryText.isBlank() || targetText == null || targetText.isBlank()) {
            return 0;
        }

        Set<Character> queryChars = new HashSet<>();
        for (char c : queryText.toCharArray()) {
            queryChars.add(c);
        }

        Set<Character> targetChars = new HashSet<>();
        for (char c : targetText.toCharArray()) {
            targetChars.add(c);
        }

        if (queryChars.isEmpty()) {
            return 0;
        }

        int overlap = 0;
        for (Character c : queryChars) {
            if (targetChars.contains(c)) {
                overlap++;
            }
        }
        return overlap / (double) queryChars.size();
    }

    private String normalize(String text) {
        String value = defaultString(text).toLowerCase(Locale.ROOT).trim();
        return PUNCT_PATTERN.matcher(value).replaceAll("");
    }

    private String periodLabel(int startYear, int endYear) {
        if (startYear == endYear) {
            return yearLabel(startYear);
        }
        return yearLabel(startYear) + " - " + yearLabel(endYear);
    }

    private String yearLabel(int year) {
        if (year < 0) {
            return "公元前" + Math.abs(year);
        }
        return "公元" + year;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record IndexedEvent(ChinaHistoryEvent event, String searchable) {}

    private record ScoreRow(double score, ChinaHistoryEvent event) {}

    private record WorldRankRow(double score, WorldHistoryEvent event) {}
}
