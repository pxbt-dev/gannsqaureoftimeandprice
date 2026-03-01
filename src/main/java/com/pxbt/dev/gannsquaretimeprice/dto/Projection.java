package com.pxbt.dev.gannsquaretimeprice.dto;

import java.time.LocalDate;

public class Projection {
    public enum Kind { TIME, PRICE, ANGLE, SQUARE_OF_NINE }

    private final LocalDate date;
    private final String description;
    private final double projectedPrice;
    private final String ratioType;
    private final double ratioValue;
    private final Kind kind;

    public Projection(LocalDate date, String description, double projectedPrice, String ratioType, double ratioValue, Kind kind) {
        this.date = date;
        this.description = description;
        this.projectedPrice = projectedPrice;
        this.ratioType = ratioType;
        this.ratioValue = ratioValue;
        this.kind = kind;
    }

    public LocalDate getDate() { return date; }
    public String getDescription() { return description; }
    public double getProjectedPrice() { return projectedPrice; }
    public String getRatioType() { return ratioType; }
    public double getRatioValue() { return ratioValue; }
    public Kind getKind() { return kind; }

    public String toJson() {
        return String.format(
            "{\"date\":\"%s\",\"description\":\"%s\",\"projectedPrice\":%.2f,\"ratioType\":\"%s\",\"ratioValue\":%.4f,\"kind\":\"%s\"}",
            date != null ? date.toString() : "null",
            escapeJson(description),
            projectedPrice,
            escapeJson(ratioType),
            ratioValue,
            kind.name()
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        if (kind == Kind.PRICE || kind == Kind.SQUARE_OF_NINE) {
            return String.format("%-18s | %-10s | Ratio %-6.3f | Price: $%,.2f | %s",
                    kind, ratioType, ratioValue, projectedPrice, description);
        }
        return String.format("%-18s | %-10s | Ratio %-6.3f | Date: %s | %s",
                kind, ratioType, ratioValue, date, description);
    }
}