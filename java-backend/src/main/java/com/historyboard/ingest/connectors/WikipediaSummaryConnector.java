package com.historyboard.ingest.connectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historyboard.model.SourceCitation;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

public class WikipediaSummaryConnector implements CitationConnector {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String language;

    public WikipediaSummaryConnector(HttpClient httpClient, ObjectMapper objectMapper, String language) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.language = language == null || language.isBlank() ? "zh" : language;
    }

    @Override
    public String name() {
        return "wikipedia-summary";
    }

    @Override
    public Optional<SourceCitation> fetchCitation(String keyword) throws IOException, InterruptedException {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        String encoded = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://" + language + ".wikipedia.org/api/rest_v1/page/summary/" + encoded);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Accept", "application/json")
            .header("User-Agent", "HistoryBoardBot/1.0 (history data enrichment)")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return Optional.empty();
        }

        JsonNode node = objectMapper.readTree(response.body());
        String title = text(node, "title");
        String pageUrl = text(node.at("/content_urls/desktop/page"));
        String description = text(node, "description");

        if (pageUrl.isBlank()) {
            return Optional.empty();
        }

        SourceCitation citation = new SourceCitation();
        citation.setSourceType("encyclopedia");
        citation.setSourceName("Wikipedia");
        citation.setTitle(title.isBlank() ? keyword.trim() : title);
        citation.setUrl(pageUrl);
        citation.setPublisher("Wikimedia Foundation");
        citation.setAccessedAt(LocalDate.now().toString());
        citation.setConfidence(0.72);
        citation.setNote(description);
        return Optional.of(citation);
    }

    private String text(JsonNode node, String field) {
        return text(node.get(field));
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }
}
