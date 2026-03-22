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
    private final Map<String, WorldHistoryEvent> worldEventsById = new HashMap<>();
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

    public EventView eventDetail(String eventId, String source) {
        if ("world".equalsIgnoreCase(source)) {
            WorldHistoryEvent event = worldEventsById.get(eventId);
            if (event == null) {
                return null;
            }
            return toWorldEventView(event);
        }

        ChinaHistoryEvent event = eventsById.get(eventId);
        if (event == null) {
            return null;
        }

        EventView view = toEventView(event, event.getImportance() * 100, true);
        view.setWorldContext(getWorldContext(view.getStartYear(), view.getEndYear(), 8, 90));
        return view;
    }

    public TimelineResponse timeline(String scope) {
        String normalizedScope = normalizeScope(scope);
        TimelineResponse response = new TimelineResponse();
        List<EventView> events = new ArrayList<>();

        if (!"world".equals(normalizedScope)) {
            for (ChinaHistoryEvent event : chinaEvents) {
                events.add(toTimelineEventView(event));
            }
        }
        if (!"china".equals(normalizedScope)) {
            for (WorldHistoryEvent event : worldEvents) {
                events.add(toTimelineEventView(event));
            }
        }

        events.sort(Comparator.comparingInt(EventView::getStartYear).thenComparing(EventView::getId));
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
        worldEventsById.clear();
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

        for (WorldHistoryEvent event : worldEvents) {
            worldEventsById.put(event.getId(), event);
        }

        candidateSet.stream()
            .filter(item -> !item.isBlank() && item.length() >= 2)
            .sorted(Comparator.comparingInt(String::length).thenComparing(item -> item))
            .limit(500)
            .forEach(suggestions::add);

        chinaEvents.sort(Comparator.comparingInt(ChinaHistoryEvent::getStartYear).thenComparing(ChinaHistoryEvent::getId));
        worldEvents.sort(Comparator.comparingInt(WorldHistoryEvent::getStartYear).thenComparing(WorldHistoryEvent::getId));
    }

    private EventView toTimelineEventView(ChinaHistoryEvent event) {
        EventView view = new EventView();
        view.setId(event.getId());
        view.setTitle(event.getTitle());
        view.setDynasty(defaultString(event.getDynasty()));
        view.setCategory(defaultString(event.getCategory()));
        view.setStartYear(event.getStartYear());
        view.setEndYear(event.getEndYear());
        view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
        view.setTimelineScope("china");
        return view;
    }

    private EventView toTimelineEventView(WorldHistoryEvent event) {
        EventView view = new EventView();
        view.setId(event.getId());
        view.setTitle(event.getTitle());
        view.setStartYear(event.getStartYear());
        view.setEndYear(event.getEndYear());
        view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
        view.setRegion(defaultString(event.getRegion()));
        view.setTimelineScope("world");
        return view;
    }

    private EventView toWorldEventView(WorldHistoryEvent event) {
        EventView view = new EventView();
        view.setId(event.getId());
        view.setTitle(event.getTitle());
        view.setStartYear(event.getStartYear());
        view.setEndYear(event.getEndYear());
        view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
        view.setRegion(defaultString(event.getRegion()));
        view.setSummary(defaultString(event.getSummary()));
        view.setAuditStatus(defaultString(event.getAuditStatus()));
        view.setLastVerifiedAt(defaultString(event.getLastVerifiedAt()));
        view.setSources(event.getSources());
        view.setTimelineScope("world");
        view.setCategory("世界历史");
        return view;
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
        if (normalizedQuery.isBlank()) {
            return indexed.event.getImportance() * 10;
        }

        double score = 0;
        ChinaHistoryEvent event = indexed.event;
        String blob = indexed.blob;

        if (normalize(defaultString(event.getTitle())).contains(normalizedQuery)) {
            score += 60;
        }
        if (normalize(defaultString(event.getDynasty())).contains(normalizedQuery)) {
            score += 18;
        }
        if (normalize(defaultString(event.getCategory())).contains(normalizedQuery)) {
            score += 14;
        }

        for (String alias : event.getAliases()) {
            if (normalize(alias).contains(normalizedQuery)) {
                score += 16;
                break;
            }
        }
        for (String figure : event.getFigures()) {
            if (normalize(figure).contains(normalizedQuery)) {
                score += 12;
                break;
            }
        }
        for (String tag : event.getTags()) {
            if (normalize(tag).contains(normalizedQuery)) {
                score += 8;
                break;
            }
        }

        String[] tokens = SPLIT_PATTERN.split(rawQuery.trim());
        for (String token : tokens) {
            String normalizedToken = normalize(token);
            if (normalizedToken.isBlank()) {
                continue;
            }
            if (blob.contains(normalizedToken)) {
                score += 6;
            }
        }

        Integer year = extractYear(rawQuery);
        if (year != null && event.getStartYear() <= year && event.getEndYear() >= year) {
            score += 28;
        }

        score += event.getImportance() * 10;
        return score;
    }

    private Integer extractYear(String query) {
        Matcher matcher = YEAR_PATTERN.matcher(defaultString(query));
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return null;
    }

    private List<WorldContextView> getWorldContext(int startYear, int endYear, int limit, int tolerance) {
        List<WorldContextView> context = new ArrayList<>();
        int rangeStart = startYear - tolerance;
        int rangeEnd = endYear + tolerance;
        for (WorldHistoryEvent event : worldEvents) {
            boolean overlap = event.getStartYear() <= rangeEnd && event.getEndYear() >= rangeStart;
            if (overlap) {
                WorldContextView view = new WorldContextView();
                view.setId(event.getId());
                view.setTitle(event.getTitle());
                view.setRegion(event.getRegion());
                view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
                view.setStartYear(event.getStartYear());
                view.setEndYear(event.getEndYear());
                view.setSummary(defaultString(event.getSummary()));
                context.add(view);
            }
        }

        context.sort(Comparator.comparingInt(WorldContextView::getStartYear).thenComparing(WorldContextView::getId));
        if (context.size() > limit) {
            return new ArrayList<>(context.subList(0, limit));
        }
        return context;
    }

    private EventView toEventView(ChinaHistoryEvent event, double relevance, boolean includeFullFields) {
        EventView view = new EventView();
        view.setId(event.getId());
        view.setTitle(event.getTitle());
        view.setDynasty(defaultString(event.getDynasty()));
        view.setCategory(defaultString(event.getCategory()));
        view.setPeriod(periodLabel(event.getStartYear(), event.getEndYear()));
        view.setStartYear(event.getStartYear());
        view.setEndYear(event.getEndYear());
        view.setSummary(defaultString(event.getSummary()));
        view.setImpact(defaultString(event.getImpact()));
        view.setLocation(defaultString(event.getLocation()));
        view.setFigures(event.getFigures());
        view.setTags(event.getTags());
        view.setAliases(event.getAliases());
        view.setKeywords(event.getKeywords());
        view.setAuditStatus(defaultString(event.getAuditStatus()));
        view.setLastVerifiedAt(defaultString(event.getLastVerifiedAt()));
        view.setSources(event.getSources());
        view.setRelevance(relevance);
        view.setTimelineScope("china");

        if (!includeFullFields) {
            view.setAliases(List.of());
            view.setKeywords(List.of());
            view.setSources(List.of());
        }
        return view;
    }

    private String normalize(String text) {
        String lower = defaultString(text).toLowerCase(Locale.ROOT).trim();
        return PUNCT_PATTERN.matcher(lower).replaceAll("");
    }

    private String normalizeScope(String scope) {
        String value = defaultString(scope).trim().toLowerCase(Locale.ROOT);
        if (value.equals("world") || value.equals("all")) {
            return value;
        }
        return "china";
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private String periodLabel(int startYear, int endYear) {
        if (startYear == endYear) {
            return yearLabel(startYear);
        }
        return yearLabel(startYear) + " - " + yearLabel(endYear);
    }

    private String yearLabel(int year) {
        if (year < 0) {
            return Math.abs(year) + " BCE";
        }
        if (year == 0) {
            return "0";
        }
        return String.valueOf(year);
    }

    private List<String> featuredSuggestions(int limit) {
        List<String> featured = List.of(
            "秦始皇",
            "丝绸之路",
            "安史之乱",
            "靖康之变",
            "甲午战争",
            "辛亥革命",
            "改革开放",
            "香港回归",
            "WTO",
            "五四运动",
            "鸦片战争",
            "郑和下西洋"
        );
        List<String> mixed = new ArrayList<>(featured);
        for (String item : suggestions) {
            if (!mixed.contains(item)) {
                mixed.add(item);
            }
            if (mixed.size() >= limit) {
                break;
            }
        }
        if (mixed.size() > limit) {
            return new ArrayList<>(mixed.subList(0, limit));
        }
        return mixed;
    }

    private record IndexedEvent(ChinaHistoryEvent event, String blob) {}

    private record ScoreRow(double score, ChinaHistoryEvent event) {}
}
