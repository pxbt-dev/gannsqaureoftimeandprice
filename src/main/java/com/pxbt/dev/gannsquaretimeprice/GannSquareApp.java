package com.pxbt.dev.gannsquaretimeprice;

import com.pxbt.dev.gannsquaretimeprice.Gateway.PriceDataGateway;
import com.pxbt.dev.gannsquaretimeprice.Service.GannSquareService;
import com.pxbt.dev.gannsquaretimeprice.dto.PricePoint;
import com.pxbt.dev.gannsquaretimeprice.dto.Projection;

import java.util.Comparator;
import java.util.List;

/**
 * Console-mode Gann analysis (fallback for non-web usage).
 */
public class GannSquareApp {
    private final GannSquareService service;

    public GannSquareApp() {
        this.service = new GannSquareService(new PriceDataGateway());
    }

    public void run(String symbol, String interval, int limit) {
        System.out.println("\n═══════════════════════════════════════════════");
        System.out.println("  GANN SQUARE OF TIME & PRICE ANALYSIS");
        System.out.println("  Symbol: " + symbol + " | Interval: " + interval);
        System.out.println("═══════════════════════════════════════════════\n");

        List<PricePoint> data = service.getHistoricalData(symbol, interval, limit);

        if (data.size() < 2) {
            System.out.println("Not enough historical data to calculate projections.");
            return;
        }

        PricePoint low = data.stream().min(Comparator.comparing(PricePoint::getLow)).orElseThrow();
        PricePoint high = data.stream().max(Comparator.comparing(PricePoint::getHigh)).orElseThrow();
        PricePoint latest = data.get(data.size() - 1);

        System.out.printf("  Period Low:  $%,.2f on %s%n", low.getLow(), low.getDate());
        System.out.printf("  Period High: $%,.2f on %s%n", high.getHigh(), high.getDate());
        System.out.printf("  Latest:      $%,.2f on %s%n%n", latest.getClose(), latest.getDate());

        // Time Projections
        System.out.println("── TIME PROJECTIONS ──────────────────────────");
        for (Projection p : service.calculateTimeProjections(low, high)) {
            System.out.println("  " + p);
        }

        // Price Projections
        System.out.println("\n── PRICE PROJECTIONS ─────────────────────────");
        PricePoint lowPP = new PricePoint(low.getDate(), low.getLow());
        PricePoint highPP = new PricePoint(high.getDate(), high.getHigh());
        for (Projection p : service.calculatePriceProjections(lowPP, highPP)) {
            System.out.println("  " + p);
        }

        // Gann Angles
        System.out.println("\n── GANN ANGLE PROJECTIONS ────────────────────");
        for (Projection p : service.calculateAngleProjections(lowPP, 180)) {
            System.out.println("  " + p);
        }

        // Square of Nine
        double midPrice = (low.getLow() + high.getHigh()) / 2.0;
        System.out.println("\n── SQUARE OF NINE (base: $" + String.format("%,.2f", midPrice) + ") ──");
        for (Projection p : service.calculateSquareOfNine(midPrice)) {
            System.out.println("  " + p);
        }

        System.out.println("\n═══════════════════════════════════════════════\n");
    }
}