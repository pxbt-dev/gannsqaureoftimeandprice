package com.pxbt.dev.gannsquaretimeprice.Gateway;

import com.pxbt.dev.gannsquaretimeprice.dto.PricePoint;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * File-based JSON cache for Binance OHLCV data.
 *
 * Cache files are stored in ./data/{SYMBOL}_{INTERVAL}.json next to the working
 * directory (i.e., the project root when running via Maven, or the deployment
 * directory when running as a JAR).
 *
 * File format — a flat JSON array of candle objects:
 * [{"date":"2019-01-01","open":3742.5,"high":3786.0,"low":3693.0,"close":3769.0,"volume":12345.6},
 * ...]
 *
 * Why file-based?
 * - Zero dependencies (no DB, no Redis, no in-memory limits)
 * - Survives server restarts
 * - Human-readable for debugging
 */
@Component
public class PriceDataCache {

    private static final String CACHE_DIR = "data";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the most recent date stored in the cache for a given pair,
     * or null if no cache exists.
     */
    public LocalDate getLastCachedDate(String symbol, String interval) {
        Path file = cacheFile(symbol, interval);
        if (!Files.exists(file))
            return null;

        List<PricePoint> points = loadFromFile(file);
        if (points.isEmpty())
            return null;

        return points.get(points.size() - 1).getDate();
    }

    /**
     * Loads all cached candles for the given pair.
     * Returns an empty list if no cache exists.
     */
    public List<PricePoint> load(String symbol, String interval) {
        Path file = cacheFile(symbol, interval);
        if (!Files.exists(file))
            return new ArrayList<>();
        return loadFromFile(file);
    }

    /**
     * Saves (or merges) candles to the cache file.
     * If the file already exists, the new points are appended and any
     * duplicate dates (by date string) are deduplicated, keeping the last
     * occurrence.
     */
    public void save(String symbol, String interval, List<PricePoint> newPoints) {
        if (newPoints == null || newPoints.isEmpty())
            return;

        Path file = cacheFile(symbol, interval);
        List<PricePoint> existing = loadFromFile(file);

        // Merge: existing + newPoints, then deduplicate by date (keep last)
        List<PricePoint> merged = merge(existing, newPoints);

        writeToFile(file, merged);
        System.out.println("[Cache] Saved " + merged.size() + " candles → " + file);
    }

    /**
     * Deletes the cache file for a given pair (useful for debugging).
     */
    public void clear(String symbol, String interval) {
        try {
            Files.deleteIfExists(cacheFile(symbol, interval));
        } catch (IOException e) {
            System.err.println("[Cache] Could not delete cache: " + e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Path cacheFile(String symbol, String interval) {
        String binanceSymbol = symbol.toUpperCase();
        if (!binanceSymbol.endsWith("USDT"))
            binanceSymbol += "USDT";
        String filename = binanceSymbol + "_" + interval + ".json";
        return Paths.get(CACHE_DIR, filename);
    }

    private List<PricePoint> merge(List<PricePoint> existing, List<PricePoint> incoming) {
        // Index by date string for O(1) dedup
        java.util.LinkedHashMap<String, PricePoint> map = new java.util.LinkedHashMap<>();
        for (PricePoint p : existing)
            map.put(p.getDate().toString(), p);
        for (PricePoint p : incoming)
            map.put(p.getDate().toString(), p); // newer data wins
        return new ArrayList<>(map.values());
    }

    // ── JSON serialisation (no external dependencies) ─────────────────────────

    private void writeToFile(Path file, List<PricePoint> points) {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < points.size(); i++) {
                PricePoint p = points.get(i);
                sb.append("  {\"date\":\"").append(p.getDate()).append("\"")
                        .append(",\"open\":").append(p.getOpen())
                        .append(",\"high\":").append(p.getHigh())
                        .append(",\"low\":").append(p.getLow())
                        .append(",\"close\":").append(p.getClose())
                        .append(",\"volume\":").append(p.getVolume())
                        .append("}");
                if (i < points.size() - 1)
                    sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[Cache] Write error: " + e.getMessage());
        }
    }

    private List<PricePoint> loadFromFile(Path file) {
        List<PricePoint> points = new ArrayList<>();
        if (!Files.exists(file))
            return points;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (json.isEmpty() || json.equals("[]"))
                return points;

            // Simple hand-rolled JSON array parser — avoids Jackson dependency
            // Each element is a single-line JSON object like {"date":"...","open":...}
            // Split by lines; each data line starts with " {" and ends with "}" or "},"
            String[] lines = json.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith("{"))
                    continue;
                line = line.replaceAll(",$", ""); // strip trailing comma
                PricePoint p = parseJsonObject(line);
                if (p != null)
                    points.add(p);
            }
        } catch (IOException e) {
            System.err.println("[Cache] Read error: " + e.getMessage());
        }
        return points;
    }

    private PricePoint parseJsonObject(String json) {
        try {
            // {"date":"2019-01-01","open":3742.5,"high":3786.0,"low":3693.0,"close":3769.0,"volume":12345.6}
            String date = extractString(json, "date");
            double open = extractDouble(json, "open");
            double high = extractDouble(json, "high");
            double low = extractDouble(json, "low");
            double close = extractDouble(json, "close");
            double volume = extractDouble(json, "volume");

            LocalDate d = LocalDate.parse(date);
            return new PricePoint(d, open, high, low, close, volume);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search) + search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private double extractDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search) + search.length();
        int end = start;
        while (end < json.length()
                && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        return Double.parseDouble(json.substring(start, end));
    }
}
