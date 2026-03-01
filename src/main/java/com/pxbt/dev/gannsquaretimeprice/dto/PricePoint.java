package com.pxbt.dev.gannsquaretimeprice.dto;

import java.time.LocalDate;

public class PricePoint {
    private final LocalDate date;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double volume;

    public PricePoint(LocalDate date, double open, double high, double low, double close, double volume) {
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // Legacy constructor for backward compatibility
    public PricePoint(LocalDate date, double price) {
        this(date, price, price, price, price, 0);
    }

    public LocalDate getDate() {
        return date;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getPrice() {
        return close;
    } // alias for backward compatibility

    public double getVolume() {
        return volume;
    }

    public String toJson() {
        return String.format(
                "{\"date\":\"%s\",\"open\":%.2f,\"high\":%.2f,\"low\":%.2f,\"close\":%.2f,\"volume\":%.2f}",
                date.toString(), open, high, low, close, volume);
    }
}