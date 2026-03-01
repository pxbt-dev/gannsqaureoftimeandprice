package Gateway;

import dto.PricePoint;

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
 */
public class PriceDataGateway {

    private static final String BINANCE_KLINES_URL = "https://api.binance.com/api/v3/klines";
    private final HttpClient httpClient;

    public PriceDataGateway() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetch kline/candlestick data from Binance.
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
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        return fetchPriceData("BTC", "1d", (int) Math.min(days, 1000));
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

        // Strip outer brackets
        json = json.trim();
        if (!json.startsWith("[["))
            return points;

        // Split into individual kline arrays
        // Each kline: [timestamp,"open","high","low","close","volume", ...]
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