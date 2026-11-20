package com.alikuxac.machinemetrics.compat.kubejs;

public record MetricLine(String label, String value, int color) {

    public static MetricLine of(String label, String value) {
        return new MetricLine(label, value, 0xFFFFFF);
    }

    public static MetricLine of(String label, String value, int color) {
        return new MetricLine(label, value, color);
    }
}
