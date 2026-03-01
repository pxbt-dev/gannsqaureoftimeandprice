package com.pxbt.dev.gannsquaretimeprice.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Gann Square of Nine - a spiral grid starting from 1 at the center,
 * spiralling clockwise. Used to find support/resistance levels by
 * locating cardinal and ordinal cross values relative to a base price.
 */
public class GannSquareGrid {
    private final int[][] grid;
    private final int size;

    public GannSquareGrid(int size) {
        this.size = size % 2 == 0 ? size + 1 : size; // ensure odd
        this.grid = new int[this.size][this.size];
        buildSpiral();
    }

    private void buildSpiral() {
        int center = size / 2;
        int x = center, y = center;
        int num = 1;
        grid[y][x] = num++;

        for (int layer = 1; layer <= center; layer++) {
            // Move right one step
            x++;
            grid[y][x] = num++;

            // Move down (2*layer - 1) steps
            for (int i = 0; i < 2 * layer - 1; i++) {
                y++;
                grid[y][x] = num++;
            }
            // Move left (2*layer) steps
            for (int i = 0; i < 2 * layer; i++) {
                x--;
                grid[y][x] = num++;
            }
            // Move up (2*layer) steps
            for (int i = 0; i < 2 * layer; i++) {
                y--;
                grid[y][x] = num++;
            }
            // Move right (2*layer) steps
            for (int i = 0; i < 2 * layer; i++) {
                x++;
                grid[y][x] = num++;
            }
        }
    }

    /**
     * Get cardinal cross values (N, S, E, W from center) - strong support/resistance.
     */
    public List<Integer> getCardinalCrossValues() {
        List<Integer> values = new ArrayList<>();
        int center = size / 2;
        for (int i = 0; i < size; i++) {
            if (i != center) {
                values.add(grid[center][i]); // horizontal
                values.add(grid[i][center]); // vertical
            }
        }
        values.sort(Integer::compareTo);
        return values;
    }

    /**
     * Get ordinal cross values (diagonals from center) - secondary support/resistance.
     */
    public List<Integer> getOrdinalCrossValues() {
        List<Integer> values = new ArrayList<>();
        int center = size / 2;
        for (int i = 0; i < size; i++) {
            if (i != center) {
                values.add(grid[i][i]);                      // main diagonal
                values.add(grid[i][size - 1 - i]);           // anti-diagonal
            }
        }
        values.sort(Integer::compareTo);
        return values;
    }

    /**
     * Find Square of Nine price levels around a base price.
     * Uses the Gann method: sqrt(price), add/subtract increments, square the result.
     */
    public static List<double[]> getSquareOfNineLevels(double basePrice) {
        List<double[]> levels = new ArrayList<>();
        double sqrtPrice = Math.sqrt(basePrice);

        // Generate levels at 45-degree increments (0.25 of a full rotation)
        // and 90-degree increments (0.5)
        double[] increments = {-2.0, -1.75, -1.5, -1.25, -1.0, -0.75, -0.5, -0.25,
                                0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0};

        for (double inc : increments) {
            double newSqrt = sqrtPrice + inc;
            if (newSqrt > 0) {
                double level = newSqrt * newSqrt;
                // [level, increment, degrees (each 0.25 = 45 degrees)]
                levels.add(new double[]{level, inc, inc * 180.0});
            }
        }
        return levels;
    }

    public int[][] getGrid() { return grid; }
    public int getSize() { return size; }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"size\":").append(size).append(",\"grid\":[");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(",");
            sb.append("[");
            for (int j = 0; j < size; j++) {
                if (j > 0) sb.append(",");
                sb.append(grid[i][j]);
            }
            sb.append("]");
        }
        sb.append("]}");
        return sb.toString();
    }
}
