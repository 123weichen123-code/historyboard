package com.historyboard.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class HistoryDatasetBuilder {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,，。；;、/]+");

    public static void main(String[] args) throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
        if (projectRoot == null) {
            throw new IllegalStateException("Cannot resolve project root.");
        }

        Path rawDir = projectRoot.resolve("backend/data/raw");
        Path targetDir = projectRoot.resolve("backend/app/data");
        Files.createDirectories(targetDir);

        Path chinaRaw = rawDir.resolve("china_history_seed.json");
        Path worldRaw = rawDir.resolve("world_history_seed.json");

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> china = mapper.readValue(Files.readString(chinaRaw), new TypeReference<>() {});
        List<Map<String, Object>> world = mapper.readValue(Files.readString(worldRaw), new TypeReference<>() {});

        List<Map<String, Object>> chinaOut = new ArrayList<>();
        for (Map<String, Object> item : china) {
            chinaOut.add(normalizeChina(item));
        }
        chinaOut.sort(Comparator.comparingInt(item -> toInt(item.get("start_year"))));

        List<Map<String, Object>> worldOut = new ArrayList<>();
        for (Map<String, Object> item : world) {
            worldOut.add(normalizeWorld(item));
        }
        worldOut.sort(Comparator.comparingInt(item -> toInt(item.get("start_year"))));

        ObjectMapper output = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        output.writeValue(targetDir.resolve("china_history_events.json").toFile(), chinaOut);
        output.writeValue(targetDir.resolve("world_history_events.json").toFile(), worldOut);

        System.out.println("Generated " + chinaOut.size() + " China events.");
        System.out.println("Generated " + worldOut.size() + " World events.");
    }

    private static Map<String, Object> normalizeChina(Map<String, Object> event) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", toString(event.get("id")));
        out.put("title", toString(event.get("title")));
        out.put("aliases", toStringList(event.get("aliases")));
        out.put("dynasty", toString(event.get("dynasty")));
        out.put("start_year", toInt(event.get("start_year")));
        out.put("end_year", toIntOrDefault(event.get("end_year"), toInt(event.get("start_year"))));
        out.put("location", toString(event.get("location")));
        out.put("figures", toStringList(event.get("figures")));
        out.put("summary", toString(event.get("summary")));
        out.put("impact", toString(event.get("impact")));
        out.put("category", toString(event.get("category")));
        out.put("tags", toStringList(event.get("tags")));
        out.put("importance", toDouble(event.get("importance"), 0.5));
        out.put("audit_status", defaultIfBlank(toString(event.get("audit_status")), "UNVERIFIED"));
        out.put("last_verified_at", toString(event.get("last_verified_at")));
        out.put("sources", toMapList(event.get("sources")));
        out.put("keywords", buildKeywords(out));
        return out;
    }

    private static Map<String, Object> normalizeWorld(Map<String, Object> event) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", toString(event.get("id")));
        out.put("title", toString(event.get("title")));
        out.put("region", toString(event.get("region")));
        out.put("start_year", toInt(event.get("start_year")));
        out.put("end_year", toIntOrDefault(event.get("end_year"), toInt(event.get("start_year"))));
        out.put("summary", toString(event.get("summary")));
        out.put("audit_status", defaultIfBlank(toString(event.get("audit_status")), "UNVERIFIED"));
        out.put("last_verified_at", toString(event.get("last_verified_at")));
        out.put("sources", toMapList(event.get("sources")));
        return out;
    }

    private static List<String> buildKeywords(Map<String, Object> event) {
        Set<String> words = new HashSet<>();
        for (String key : List.of("title", "dynasty", "category", "location", "summary", "impact")) {
            String value = toString(event.get(key));
            if (!value.isBlank()) {
                words.add(value);
                for (String token : splitToken(value)) {
                    words.add(token);
                }
            }
        }

        for (String key : List.of("aliases", "figures", "tags")) {
            for (String item : toStringList(event.get(key))) {
                words.add(item);
                words.addAll(splitToken(item));
            }
        }

        return words.stream()
            .filter(item -> item.length() >= 2)
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(item -> item))
            .limit(40)
            .toList();
    }

    private static List<String> splitToken(String text) {
        List<String> tokens = new ArrayList<>();
        for (String item : TOKEN_SPLIT.split(text)) {
            String clean = item.trim();
            if (clean.length() >= 2) {
                tokens.add(clean);
            }
        }
        return tokens;
    }

    private static String toString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(toString(value));
    }

    private static int toIntOrDefault(Object value, int defaultValue) {
        try {
            return toInt(value);
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        String text = toString(value).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(text);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String text = toString(item);
                if (!text.isBlank()) {
                    out.add(text);
                }
            }
            return out;
        }

        String single = toString(value);
        if (single.isBlank()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        out.add(single);
        return out;
    }
}
