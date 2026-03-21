package com.historyboard.controller;

import com.historyboard.dto.DiscoverResponse;
import com.historyboard.dto.EventView;
import com.historyboard.dto.SearchResponse;
import com.historyboard.dto.SuggestResponse;
import com.historyboard.dto.TimelineResponse;
import com.historyboard.service.HistorySearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api")
@Validated
public class HistoryController {

    private final HistorySearchService historySearchService;

    public HistoryController(HistorySearchService historySearchService) {
        this.historySearchService = historySearchService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/history/search")
    public SearchResponse searchHistory(
        @RequestParam(value = "q", defaultValue = "") String q,
        @RequestParam(value = "limit", defaultValue = "12") @Min(1) @Max(30) int limit
    ) {
        return historySearchService.search(q, limit);
    }

    @GetMapping("/history/suggest")
    public SuggestResponse suggestHistory(
        @RequestParam(value = "q", defaultValue = "") String q,
        @RequestParam(value = "limit", defaultValue = "12") @Min(1) @Max(30) int limit
    ) {
        SuggestResponse response = new SuggestResponse();
        response.setQuery(q);
        response.setSuggestions(historySearchService.suggest(q, limit));
        return response;
    }

    @GetMapping("/history/events/{eventId}")
    public EventView eventDetail(@PathVariable String eventId) {
        EventView detail = historySearchService.eventDetail(eventId);
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "事件不存在: " + eventId);
        }
        return detail;
    }

    @GetMapping("/history/timeline")
    public TimelineResponse timeline() {
        return historySearchService.timeline();
    }

    @GetMapping("/history/discover")
    public DiscoverResponse discover() {
        return historySearchService.discover();
    }
}
