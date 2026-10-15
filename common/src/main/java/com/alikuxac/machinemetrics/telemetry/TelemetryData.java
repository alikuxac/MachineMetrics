package com.alikuxac.machinemetrics.telemetry;

import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector.RotationDirection;
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
        float kineticSpeed,
        float kineticStress,
        float kineticCapacity,
        float kineticBlockStress,
        boolean hasKinetic,
        RotationDirection kineticDirection,
        BottleneckState bottleneckState,
        IntArrayList allSlotCounts,
        IntArrayList allTankAmounts,
        float[] sideMetrics,
        short serverDisabledMask,
        boolean hasAe2Network,
        float aePowerRate,
        float aePowerDrain,
        float aeSubnetDrain,
        int aeSubnetCount,
        double aeStoredPower,
        double aeMaxPower,
        byte aeStabilityState,
        boolean aeIsCreative,
        int aeUsedChannels,
        int aeMaxChannels
) {
    public enum BottleneckState {
        OPTIMAL,
        STARVED,
        CLOGGED,
        OVERLOADED,
        IDLE
    }

    public static final short MASK_ITEMS = 1 << 0;
    public static final short MASK_FLUIDS = 1 << 1;
    public static final short MASK_ENERGY = 1 << 2;
    public static final short MASK_KINETIC = 1 << 3;
    public static final short MASK_CHEMICALS = 1 << 4;
    public static final short MASK_SIDES = 1 << 5;
    public static final short MASK_AE2 = 1 << 6;

    public TelemetryData(
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
            float kineticSpeed,
            float kineticStress,
            float kineticCapacity,
            float kineticBlockStress,
            boolean hasKinetic,
            RotationDirection kineticDirection,
            BottleneckState bottleneckState,
            IntArrayList allSlotCounts,
            IntArrayList allTankAmounts,
            float[] sideMetrics
    ) {
        this(
                itemThroughput, hasItems,
                fluidThroughput, hasFluids,
                energyDelta, hasEnergy,
                chemicalThroughput, hasChemicals, chemicalName, chemicalAmount, chemicalCapacity,
                kineticSpeed, kineticStress, kineticCapacity, kineticBlockStress, hasKinetic,
                kineticDirection, bottleneckState,
                allSlotCounts, allTankAmounts, sideMetrics,
                (short) 0,
                false, 0f, 0f, 0f, 0, 0.0, 0.0, (byte) 0, false, 0, 0
        );
    }

    public static final TelemetryData EMPTY = new TelemetryData(
            0.0f, false,
            0.0f, false,
            0.0f, false,
            0.0f, false, "", 0L, 0L,
            0.0f, 0.0f, 0.0f, 0.0f, false,
            RotationDirection.STATIONARY,
            BottleneckState.IDLE,
            new IntArrayList(), new IntArrayList(),
            new float[18],
            (short) 0,
            false, 0f, 0f, 0f, 0, 0.0, 0.0, (byte) 0, false, 0, 0
    );

    public static TelemetryData empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return !hasItems && !hasFluids && !hasEnergy && !hasChemicals && !hasKinetic && !hasAe2Network && serverDisabledMask == 0;
    }

    public boolean isItemsDisabledByServer() {
        return (serverDisabledMask & MASK_ITEMS) != 0;
    }

    public boolean isFluidsDisabledByServer() {
        return (serverDisabledMask & MASK_FLUIDS) != 0;
    }

    public boolean isEnergyDisabledByServer() {
        return (serverDisabledMask & MASK_ENERGY) != 0;
    }

    public boolean isKineticDisabledByServer() {
        return (serverDisabledMask & MASK_KINETIC) != 0;
    }

    public boolean isChemicalsDisabledByServer() {
        return (serverDisabledMask & MASK_CHEMICALS) != 0;
    }

    public boolean isSidesDisabledByServer() {
        return (serverDisabledMask & MASK_SIDES) != 0;
    }

    public boolean isAe2DisabledByServer() {
        return (serverDisabledMask & MASK_AE2) != 0;
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

    public boolean hasSignificantDelta(TelemetryData other) {
        if (other == null) return true;
        if (this.serverDisabledMask != other.serverDisabledMask) return true;
        if (this.bottleneckState != other.bottleneckState) return true;
        if (this.kineticDirection != other.kineticDirection) return true;
        if (this.hasItems != other.hasItems || this.hasFluids != other.hasFluids || this.hasEnergy != other.hasEnergy || this.hasChemicals != other.hasChemicals || this.hasKinetic != other.hasKinetic || this.hasAe2Network != other.hasAe2Network) return true;
        if (this.hasAe2Network) {
            if (this.aeStabilityState != other.aeStabilityState) return true;
            if (this.aeIsCreative != other.aeIsCreative) return true;
            if (this.aeUsedChannels != other.aeUsedChannels) return true;
            if (this.aeSubnetCount != other.aeSubnetCount) return true;
            if (Math.abs(this.aePowerRate - other.aePowerRate) > 1.0f) return true;
            if (Math.abs(this.aePowerDrain - other.aePowerDrain) > 1.0f) return true;
            if (Math.abs(this.aeSubnetDrain - other.aeSubnetDrain) > 1.0f) return true;
            if (Math.abs(this.aeStoredPower - other.aeStoredPower) > 10.0) return true;
            if (Math.abs(this.aeMaxPower - other.aeMaxPower) > 10.0) return true;
        }
        if (Math.abs(this.itemThroughput - other.itemThroughput) > 0.1f) return true;
        if (Math.abs(this.fluidThroughput - other.fluidThroughput) > 1.0f) return true;
        if (Math.abs(this.chemicalThroughput - other.chemicalThroughput) > 1.0f) return true;
        if (Math.abs(this.energyDelta - other.energyDelta) > 1.0f) return true;
        if (Math.abs(this.kineticSpeed - other.kineticSpeed) > 0.1f) return true;
        if (Math.abs(this.kineticStress - other.kineticStress) > 0.5f) return true;
        if (Math.abs(this.kineticCapacity - other.kineticCapacity) > 0.5f) return true;
        if (Math.abs(this.kineticBlockStress - other.kineticBlockStress) > 0.5f) return true;
        if (this.chemicalAmount != other.chemicalAmount) return true;

        if (this.sideMetrics != null && other.sideMetrics != null) {
            int len = Math.min(this.sideMetrics.length, other.sideMetrics.length);
            for (int i = 0; i < len; i++) {
                if (Math.abs(this.sideMetrics[i] - other.sideMetrics[i]) > 0.5f) {
                    return true;
                }
            }
        }
        return false;
    }
}
