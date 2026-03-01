package com.pxbt.dev.gannsquaretimeprice.Service;

import com.pxbt.dev.gannsquaretimeprice.Gateway.PriceDataGateway;
import com.pxbt.dev.gannsquaretimeprice.dto.GannSquareGrid;
import com.pxbt.dev.gannsquaretimeprice.dto.PricePoint;
import com.pxbt.dev.gannsquaretimeprice.dto.Projection;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Complete Gann Square of Time & Price service.
 * Calculates time projections, price projections, Gann angles,
 * and Square of Nine support/resistance levels.
 */
public class GannSquareService {

    // Gann static ratios (divisions of the circle/square)
    private static final double[] STATIC_RATIOS = {
            0.125, 0.25, 0.333, 0.5, 0.667, 0.75, 1.0
    };

    // Fibonacci ratios
    private static final double[] FIBONACCI_RATIOS = {
            0.146, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0
    };

    // Gann angles: name, price units per time unit
    private static final double[][] GANN_ANGLES = {
            { 8, 1 }, // 8×1 — steepest bullish
            { 4, 1 }, // 4×1
            { 2, 1 }, // 2×1
            { 1, 1 }, // 1×1 — the 45-degree angle
            { 1, 2 }, // 1×2
            { 1, 4 }, // 1×4
            { 1, 8 }, // 1×8 — flattest
    };

    private final PriceDataGateway priceDataGateway;

    public GannSquareService(PriceDataGateway priceDataGateway) {
        this.priceDataGateway = priceDataGateway;
    }

    // ── Data fetching ──────────────────────────────────────────────

    public List<PricePoint> getHistoricalData(String symbol, String interval, int limit) {
        return priceDataGateway.fetchPriceData(symbol, interval, limit);
    }

    public List<PricePoint> getHistoricalData(LocalDate startDate, LocalDate endDate) {
        return priceDataGateway.fetchPriceData(startDate, endDate);
    }

    public String getRawKlines(String symbol, String interval, int limit) {
        return priceDataGateway.fetchRawKlines(symbol, interval, limit);
    }

    // ── Time Projections ───────────────────────────────────────────

    public List<Projection> calculateTimeProjections(PricePoint low, PricePoint high) {
        List<Projection> projections = new ArrayList<>();
        long daysBetween = ChronoUnit.DAYS.between(low.getDate(), high.getDate());

        for (double ratio : STATIC_RATIOS) {
            long projectedDays = Math.round(Math.abs(daysBetween) * ratio);
            LocalDate projectedDate = high.getDate().plusDays(projectedDays);
            projections.add(new Projection(
                    projectedDate,
                    String.format("Gann %.3f of %d-day range", ratio, Math.abs(daysBetween)),
                    0, "Gann", ratio, Projection.Kind.TIME));
        }

        for (double ratio : FIBONACCI_RATIOS) {
            long projectedDays = Math.round(Math.abs(daysBetween) * ratio);
            LocalDate projectedDate = high.getDate().plusDays(projectedDays);
            projections.add(new Projection(
                    projectedDate,
                    String.format("Fib %.3f of %d-day range", ratio, Math.abs(daysBetween)),
                    0, "Fibonacci", ratio, Projection.Kind.TIME));
        }

        projections.sort(Comparator.comparing(Projection::getDate));
        return projections;
    }

    // ── Price Projections ──────────────────────────────────────────

    public List<Projection> calculatePriceProjections(PricePoint low, PricePoint high) {
        List<Projection> projections = new ArrayList<>();
        double priceRange = high.getPrice() - low.getPrice();

        // Retracement levels (from high, measuring down)
        for (double ratio : STATIC_RATIOS) {
            double level = high.getPrice() - priceRange * ratio;
            projections.add(new Projection(
                    null,
                    String.format("Gann %.1f%% retracement", ratio * 100),
                    level, "Gann", ratio, Projection.Kind.PRICE));
        }
        for (double ratio : FIBONACCI_RATIOS) {
            double level = high.getPrice() - priceRange * ratio;
            projections.add(new Projection(
                    null,
                    String.format("Fib %.1f%% retracement", ratio * 100),
                    level, "Fibonacci", ratio, Projection.Kind.PRICE));
        }

        // Extension levels (from high, projecting up)
        double[] extensionRatios = { 1.272, 1.618, 2.0, 2.618 };
        for (double ratio : extensionRatios) {
            double level = low.getPrice() + priceRange * ratio;
            projections.add(new Projection(
                    null,
                    String.format("Fib %.1f%% extension", ratio * 100),
                    level, "Extension", ratio, Projection.Kind.PRICE));
        }

        projections.sort(Comparator.comparingDouble(Projection::getProjectedPrice));
        return projections;
    }

    // ── Gann Angle Projections ─────────────────────────────────────

    public List<Projection> calculateAngleProjections(PricePoint pivot, int futureDays) {
        List<Projection> projections = new ArrayList<>();

        for (double[] angle : GANN_ANGLES) {
            double pricePerDay = angle[0] / angle[1];
            String angleName = String.format("%.0f×%.0f", angle[0], angle[1]);

            // Project for specific future time points
            int[] dayPoints = { 30, 60, 90, 120, 180, 360 };
            for (int days : dayPoints) {
                if (days > futureDays)
                    break;

                // Ascending from low
                double priceUp = pivot.getPrice() + (pricePerDay * days);
                LocalDate futureDate = pivot.getDate().plusDays(days);

                projections.add(new Projection(
                        futureDate,
                        String.format("%s ascending from %.2f (+%d days)", angleName, pivot.getPrice(), days),
                        priceUp, "Angle " + angleName, pricePerDay, Projection.Kind.ANGLE));
            }
        }

        return projections;
    }

    // ── Square of Nine ─────────────────────────────────────────────

    public List<Projection> calculateSquareOfNine(double basePrice) {
        List<Projection> projections = new ArrayList<>();
        List<double[]> levels = GannSquareGrid.getSquareOfNineLevels(basePrice);

        for (double[] level : levels) {
            double price = level[0];
            double increment = level[1];
            double degrees = level[2];

            String direction = increment > 0 ? "Resistance" : "Support";
            String desc = String.format("%s @ %.0f° (√price %+.2f)", direction, Math.abs(degrees), increment);

            projections.add(new Projection(
                    null, desc, price, direction,
                    Math.abs(increment), Projection.Kind.SQUARE_OF_NINE));
        }

        return projections;
    }

    // ── Full Analysis ──────────────────────────────────────────────

    /**
     * Runs the complete Gann analysis and returns all projections as JSON.
     */
    public String runFullAnalysis(String symbol, String interval, int limit) {
        List<PricePoint> data = getHistoricalData(symbol, interval, limit);
        if (data.size() < 2) {
            return "{\"error\":\"Not enough data\"}";
        }

        PricePoint low = data.stream().min(Comparator.comparing(PricePoint::getLow))
                .orElseThrow();
        PricePoint high = data.stream().max(Comparator.comparing(PricePoint::getHigh))
                .orElseThrow();
        PricePoint latest = data.get(data.size() - 1);

        double midPrice = (low.getLow() + high.getHigh()) / 2.0;

        List<Projection> timeProj = calculateTimeProjections(low, high);
        List<Projection> priceProj = calculatePriceProjections(
                new PricePoint(low.getDate(), low.getLow()),
                new PricePoint(high.getDate(), high.getHigh()));
        List<Projection> angleProj = calculateAngleProjections(
                new PricePoint(low.getDate(), low.getLow()), 360);
        List<Projection> sq9Proj = calculateSquareOfNine(midPrice);

        // Build JSON response
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"symbol\":\"").append(symbol).append("\",");
        json.append("\"interval\":\"").append(interval).append("\",");
        json.append(String.format("\"low\":{\"date\":\"%s\",\"price\":%.2f},", low.getDate(), low.getLow()));
        json.append(String.format("\"high\":{\"date\":\"%s\",\"price\":%.2f},", high.getDate(), high.getHigh()));
        json.append(String.format("\"latest\":{\"date\":\"%s\",\"price\":%.2f},", latest.getDate(), latest.getClose()));

        // Klines
        json.append("\"klines\":[");
        for (int i = 0; i < data.size(); i++) {
            if (i > 0)
                json.append(",");
            json.append(data.get(i).toJson());
        }
        json.append("],");

        // Projections
        json.append("\"timeProjections\":").append(toJsonArray(timeProj)).append(",");
        json.append("\"priceProjections\":").append(toJsonArray(priceProj)).append(",");
        json.append("\"angleProjections\":").append(toJsonArray(angleProj)).append(",");
        json.append("\"squareOfNine\":").append(toJsonArray(sq9Proj)).append(",");

        // Square of Nine grid
        GannSquareGrid grid = new GannSquareGrid(9);
        json.append("\"grid\":").append(grid.toJson());

        json.append("}");
        return json.toString();
    }

    private String toJsonArray(List<Projection> projections) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < projections.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(projections.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }
}