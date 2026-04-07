package com.pxbt.dev.gannsquaretimeprice.Gateway;

import com.pxbt.dev.gannsquaretimeprice.dto.PricePoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Gateway to fetch real market data from the Binance public API.
 * Uses Java's built-in HttpClient — no external dependencies.
 *
 * Supports paginated fetching to retrieve data beyond the 1000-candle
 * per-request limit.
 */
@Component
public class PriceDataGateway {

    private static final String BINANCE_KLINES_URL = "https://api.binance.com/api/v3/klines";
    private static final int PAGE_SIZE = 1000; // Binance hard limit per request
    private final HttpClient httpClient;
    private final PriceDataCache cache;

    @Autowired
    public PriceDataGateway(PriceDataCache cache) {
        this.cache = cache;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetch all klines from startDate up to today, paginating automatically.
     * Each page fetches 1000 candles; continues until today is reached.
     *
     * @param symbol    Trading pair base (e.g., "BTC" → becomes "BTCUSDT")
     * @param interval  Kline interval (e.g., "1d", "4h", "1h")
     * @param startDate The earliest date to fetch data from
     * @return Fully stitched list of PricePoints sorted oldest → newest
     */
    public List<PricePoint> fetchPriceDataSince(String symbol, String interval, LocalDate startDate) {

        // ── 1. Load what we already have on disk ─────────────────────────────
        List<PricePoint> cached = cache.load(symbol, interval);
        LocalDate lastCached = cache.getLastCachedDate(symbol, interval);

        // Determine the effective fetch start:
        // - If cache is empty or older than requested startDate → use startDate
        // - If cache covers past startDate → only fetch the missing tail
        LocalDate fetchFrom;
        if (lastCached == null) {
            fetchFrom = startDate;
            System.out.println("[Cache] No cache for " + symbol + " " + interval + " – fetching from " + startDate);
        } else if (lastCached.isBefore(LocalDate.now().minusDays(1))) {
            // Cache exists but is not fully up to date – top it up
            fetchFrom = lastCached.plusDays(1);
            System.out.println("[Cache] Cache for " + symbol + " " + interval
                    + " last at " + lastCached + " – fetching delta from " + fetchFrom);
        } else {
            // Cache is fresh (last entry is yesterday or today)
            System.out.println("[Cache] Cache for " + symbol + " " + interval + " is up to date – returning "
                    + cached.size() + " candles");
            // Filter to the requested startDate window
            return filtered(cached, startDate);
        }

        // ── 2. Fetch only the missing candles from Binance ───────────────────
        List<PricePoint> newCandles = fetchFromBinance(symbol, interval, fetchFrom);

        // ── 3. Merge and persist ─────────────────────────────────────────────
        if (!newCandles.isEmpty()) {
            cache.save(symbol, interval, newCandles);
        }

        // Reload from disk to get the fully merged result
        List<PricePoint> all = cache.load(symbol, interval);
        if (all.isEmpty()) {
            // Fallback: just return what we fetched (cache write may have failed)
            all = newCandles;
        }

        return filtered(all, startDate);
    }

    /** Filter a list of candles to only those on or after startDate. */
    private List<PricePoint> filtered(List<PricePoint> points, LocalDate startDate) {
        List<PricePoint> result = new ArrayList<>();
        for (PricePoint p : points) {
            if (!p.getDate().isBefore(startDate))
                result.add(p);
        }
        return result;
    }

    /**
     * Core Binance paginated fetch — fetches all candles from fetchFrom to today.
     */
    private List<PricePoint> fetchFromBinance(String symbol, String interval, LocalDate fetchFrom) {
        String binanceSymbol = symbol.toUpperCase();
        if (!binanceSymbol.endsWith("USDT"))
            binanceSymbol += "USDT";

        List<PricePoint> allPoints = new ArrayList<>();
        long startMs = fetchFrom.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long nowMs = Instant.now().toEpochMilli();

        int maxPages = 50; // safety cap (50 000 candles max)
        int page = 0;

        while (startMs < nowMs && page < maxPages) {
            String url = String.format("%s?symbol=%s&interval=%s&limit=%d&startTime=%d",
                    BINANCE_KLINES_URL, binanceSymbol, interval, PAGE_SIZE, startMs);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.err.println("Binance API error (HTTP " + response.statusCode() + "): " + response.body());
                    break;
                }

                List<PricePoint> pagePoints = parseKlines(response.body());
                if (pagePoints.isEmpty())
                    break;

                allPoints.addAll(pagePoints);

                PricePoint last = pagePoints.get(pagePoints.size() - 1);
                long lastMs = last.getDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                startMs = lastMs + 1;
                page++;

                Thread.sleep(100); // be polite to the Binance rate limit

            } catch (Exception e) {
                System.err.println("[Binance] Failed on page " + page + ": " + e.getMessage());
                break;
            }
        }

        System.out.println(
                "[Binance] Fetched " + allPoints.size() + " candles across " + page + " pages for " + binanceSymbol);
        return allPoints;
    }

    /**
     * Fetch kline/candlestick data from Binance (most recent N candles).
     *
     * @param symbol   Trading pair base (e.g., "BTC" → becomes "BTCUSDT")
     * @param interval Kline interval (e.g., "1d", "4h", "1h")
     * @param limit    Number of candles to fetch (max 1000)
     * @return List of PricePoint with OHLCV data
     */
    public List<PricePoint> fetchPriceData(String symbol, String interval, int limit) {
        String binanceSymbol = symbol.toUpperCase();
        if (!binanceSymbol.endsWith("USDT")) {
            binanceSymbol += "USDT";
        }

        String url = String.format("%s?symbol=%s&interval=%s&limit=%d",
                BINANCE_KLINES_URL, binanceSymbol, interval, Math.min(limit, 1000));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Binance API error (HTTP " + response.statusCode() + "): " + response.body());
                return new ArrayList<>();
            }

            return parseKlines(response.body());

        } catch (Exception e) {
            System.err.println("Failed to fetch data from Binance: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Backward-compatible method using date range.
     */
    public List<PricePoint> fetchPriceData(LocalDate startDate, LocalDate endDate) {
        return fetchPriceDataSince("BTC", "1d", startDate);
    }

    /**
     * Fetches raw kline JSON string — used by the API server to proxy Binance data.
     */
    public String fetchRawKlines(String symbol, String interval, int limit) {
        String binanceSymbol = symbol.toUpperCase();
        if (!binanceSymbol.endsWith("USDT")) {
            binanceSymbol += "USDT";
        }

        String url = String.format("%s?symbol=%s&interval=%s&limit=%d",
                BINANCE_KLINES_URL, binanceSymbol, interval, Math.min(limit, 1000));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();

        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Parse Binance klines JSON response.
     * Format: [[openTime, open, high, low, close, volume, closeTime, ...], ...]
     */
    private List<PricePoint> parseKlines(String json) {
        List<PricePoint> points = new ArrayList<>();

        json = json.trim();
        if (!json.startsWith("[["))
            return points;

        int depth = 0;
        int start = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
                if (depth == 2)
                    start = i;
            } else if (c == ']') {
                depth--;
                if (depth == 1 && start >= 0) {
                    String kline = json.substring(start + 1, i);
                    PricePoint pp = parseOneKline(kline);
                    if (pp != null)
                        points.add(pp);
                    start = -1;
                }
            }
        }
        return points;
    }

    private PricePoint parseOneKline(String kline) {
        try {
            String[] parts = kline.split(",");
            if (parts.length < 6)
                return null;

            long openTimeMs = Long.parseLong(parts[0].trim());
            double open = Double.parseDouble(stripQuotes(parts[1]));
            double high = Double.parseDouble(stripQuotes(parts[2]));
            double low = Double.parseDouble(stripQuotes(parts[3]));
            double close = Double.parseDouble(stripQuotes(parts[4]));
            double volume = Double.parseDouble(stripQuotes(parts[5]));

            LocalDate date = Instant.ofEpochMilli(openTimeMs)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            return new PricePoint(date, open, high, low, close, volume);

        } catch (Exception e) {
            return null;
        }
    }

    private String stripQuotes(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
