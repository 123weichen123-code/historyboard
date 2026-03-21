package com.historyboard.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.historyboard.ingest.connectors.CitationConnector;
import com.historyboard.ingest.connectors.WikidataSearchConnector;
import com.historyboard.ingest.connectors.WikipediaSummaryConnector;
import com.historyboard.model.SourceCitation;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HistoryCitationEnricher {

    public static void main(String[] args) throws IOException {
        CliArgs cli = CliArgs.parse(args);

        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
        if (projectRoot == null) {
            throw new IllegalStateException("Cannot resolve project root.");
        }

        Path input = cli.input != null
            ? Path.of(cli.input)
            : projectRoot.resolve("backend/data/raw/china_history_seed.json");
        Path output = cli.output != null
            ? Path.of(cli.output)
            : projectRoot.resolve("backend/data/raw/china_history_seed.with_sources.json");

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> events = mapper.readValue(Files.readString(input), new TypeReference<>() {});

        HttpClient httpClient = HttpClient.newHttpClient();
        List<CitationConnector> connectors = List.of(
            new WikipediaSummaryConnector(httpClient, mapper, "zh"),
            new WikidataSearchConnector(httpClient, mapper, "zh")
        );

        int enriched = 0;
        int skipped = 0;
        int maxToEnrich = cli.maxToEnrich <= 0 ? Integer.MAX_VALUE : cli.maxToEnrich;

        for (Map<String, Object> event : events) {
            List<Map<String, Object>> existingSources = readMapList(event.get("sources"));
            if (!existingSources.isEmpty() && !cli.force) {
                skipped++;
                continue;
            }
            if (enriched >= maxToEnrich) {
                break;
            }

            Set<String> queries = new LinkedHashSet<>();
            String title = str(event.get("title"));
            if (!title.isBlank()) {
                queries.add(title);
            }
            for (String alias : readStringList(event.get("aliases"))) {
                queries.add(alias);
            }

            List<SourceCitation> citations = new ArrayList<>();
            for (String query : queries) {
                for (CitationConnector connector : connectors) {
                    try {
                        connector.fetchCitation(query).ifPresent(citations::add);
                    } catch (Exception ignore) {
                        // keep enrichment resilient; continue with other connectors
                    }
                }
                if (!citations.isEmpty()) {
                    break;
                }
            }

            if (!citations.isEmpty()) {
                event.put("sources", mapper.convertValue(citations, new TypeReference<List<Map<String, Object>>>() {}));
                event.put("audit_status", "AUTO_COLLECTED");
                event.put("last_verified_at", LocalDate.now().toString());
                enriched++;
            } else {
                if (str(event.get("audit_status")).isBlank()) {
                    event.put("audit_status", "UNVERIFIED");
                }
            }
        }

        ObjectMapper out = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        out.writeValue(output.toFile(), events);

        System.out.println("Input: " + input);
        System.out.println("Output: " + output);
        System.out.println("Enriched: " + enriched + ", Skipped existing: " + skipped);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readMapList(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String text = str(item);
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static class CliArgs {
        private String input;
        private String output;
        private int maxToEnrich = 30;
        private boolean force;

        static CliArgs parse(String[] args) {
            CliArgs cli = new CliArgs();
            for (String arg : args) {
                if (arg.startsWith("--input=")) {
                    cli.input = arg.substring("--input=".length());
                } else if (arg.startsWith("--output=")) {
                    cli.output = arg.substring("--output=".length());
                } else if (arg.startsWith("--max=")) {
                    cli.maxToEnrich = Integer.parseInt(arg.substring("--max=".length()));
                } else if (arg.equals("--force")) {
                    cli.force = true;
                }
            }
            return cli;
        }
    }
}
