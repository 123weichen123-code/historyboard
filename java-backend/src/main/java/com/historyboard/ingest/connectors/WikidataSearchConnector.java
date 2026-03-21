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

public class WikidataSearchConnector implements CitationConnector {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String language;

    public WikidataSearchConnector(HttpClient httpClient, ObjectMapper objectMapper, String language) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.language = language == null || language.isBlank() ? "zh" : language;
    }

    @Override
    public String name() {
        return "wikidata-search";
    }

    @Override
    public Optional<SourceCitation> fetchCitation(String keyword) throws IOException, InterruptedException {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        String encoded = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
        String uri = "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&limit=1&language="
            + language + "&search=" + encoded;

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
            .header("Accept", "application/json")
            .header("User-Agent", "HistoryBoardBot/1.0 (history data enrichment)")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode first = root.path("search").isArray() && root.path("search").size() > 0
            ? root.path("search").get(0)
            : null;

        if (first == null || first.isNull()) {
            return Optional.empty();
        }

        String label = first.path("label").asText("").trim();
        String description = first.path("description").asText("").trim();
        String conceptUri = first.path("concepturi").asText("").trim();

        if (conceptUri.isBlank()) {
            return Optional.empty();
        }

        SourceCitation citation = new SourceCitation();
        citation.setSourceType("knowledge_base");
        citation.setSourceName("Wikidata");
        citation.setTitle(label.isBlank() ? keyword.trim() : label);
        citation.setUrl(conceptUri);
        citation.setPublisher("Wikimedia Foundation");
        citation.setAccessedAt(LocalDate.now().toString());
        citation.setConfidence(0.65);
        citation.setNote(description);
        return Optional.of(citation);
    }
}
