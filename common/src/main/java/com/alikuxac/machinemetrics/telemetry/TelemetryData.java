package com.alikuxac.machinemetrics.telemetry;

import net.minecraft.core.Direction;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public record TelemetryData(
        float itemThroughput,
        boolean hasItems,
        float fluidThroughput,
        boolean hasFluids,
        float energyDelta,
        boolean hasEnergy,
        float chemicalThroughput,
        boolean hasChemicals,
        String chemicalName,
        long chemicalAmount,
        long chemicalCapacity,
        BottleneckState bottleneckState,
        IntArrayList allSlotCounts,
        IntArrayList allTankAmounts,
        float[] sideMetrics
) {
    public enum BottleneckState {
        OPTIMAL,
        STARVED,
        CLOGGED,
        IDLE
    }

    public static final TelemetryData EMPTY = new TelemetryData(
            0.0f, false,
            0.0f, false,
            0.0f, false,
            0.0f, false, "", 0L, 0L,
            BottleneckState.IDLE,
            new IntArrayList(), new IntArrayList(),
            new float[18]
    );

    public static TelemetryData empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return !hasItems && !hasFluids && !hasEnergy && !hasChemicals;
    }

    public float getItemSideRate(Direction dir) {
        if (sideMetrics == null || dir == null) return 0.0f;
        return sideMetrics[dir.ordinal()];
    }

    public float getFluidSideRate(Direction dir) {
        if (sideMetrics == null || dir == null) return 0.0f;
        return sideMetrics[6 + dir.ordinal()];
    }

    public float getChemicalSideRate(Direction dir) {
        if (sideMetrics == null || dir == null || sideMetrics.length < 18) return 0.0f;
        return sideMetrics[12 + dir.ordinal()];
    }

    public boolean hasActiveSides() {
        if (sideMetrics == null) return false;
        for (float val : sideMetrics) {
            if (val > 0.01f) return true;
        }
        return false;
    }

}


