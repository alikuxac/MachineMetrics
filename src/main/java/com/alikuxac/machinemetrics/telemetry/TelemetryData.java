package com.alikuxac.machinemetrics.telemetry;

public record TelemetryData(
        float itemThroughput,
        boolean hasItems,
        float fluidThroughput,
        boolean hasFluids,
        float energyDelta,
        boolean hasEnergy,
        BottleneckState bottleneckState
) {
    public enum BottleneckState {
        OPTIMAL,
        STARVED,
        CLOGGED,
        IDLE
    }

    public static TelemetryData empty() {
        return new TelemetryData(0f, false, 0f, false, 0f, false, BottleneckState.OPTIMAL);
    }

    public boolean isEmpty() {
        return !hasItems && !hasFluids && !hasEnergy;
    }
}
