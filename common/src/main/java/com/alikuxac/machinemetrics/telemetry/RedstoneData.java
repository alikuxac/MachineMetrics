package com.alikuxac.machinemetrics.telemetry;

public record RedstoneData(
        RedstoneType type,
        int power,
        int delay,
        boolean locked,
        boolean powered,
        boolean lit,
        boolean crafting,
        byte comparatorMode,
        int expectedOutput,
        int signalSource,
        int maxSideSignal,
        int disabledSlotsMask,
        int occupiedSlotsMask
) {
    public enum RedstoneType {
        WIRE,
        REPEATER,
        COMPARATOR,
        CRAFTER,
        COPPER_BULB
    }

    public static final byte MODE_NONE = 0;
    public static final byte MODE_COMPARE = 1;
    public static final byte MODE_SUBTRACT = 2;

    public int countDisabledSlots() {
        return Integer.bitCount(disabledSlotsMask & 0x1FF);
    }

    public int countOccupiedSlots() {
        return Integer.bitCount(occupiedSlotsMask & 0x1FF);
    }

    public boolean isSlotDisabled(int slot) {
        if (slot < 0 || slot > 8) return false;
        return (disabledSlotsMask & (1 << slot)) != 0;
    }

    public boolean isSlotOccupied(int slot) {
        if (slot < 0 || slot > 8) return false;
        return (occupiedSlotsMask & (1 << slot)) != 0;
    }
}

