package com.alikuxac.machinemetrics.platform.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public final class StorageAccess {

    public interface Lookup {
        @Nullable
        ItemStorage item(Level level, BlockPos pos, @Nullable Direction side);

        @Nullable
        FluidStorage fluid(Level level, BlockPos pos, @Nullable Direction side);

        @Nullable
        EnergyStorage energy(Level level, BlockPos pos, @Nullable Direction side);
    }

    private static Lookup lookup = new Lookup() {
        @Override public ItemStorage item(Level level, BlockPos pos, Direction side) { return null; }
        @Override public FluidStorage fluid(Level level, BlockPos pos, Direction side) { return null; }
        @Override public EnergyStorage energy(Level level, BlockPos pos, Direction side) { return null; }
    };

    private StorageAccess() {}

    public static void setLookup(Lookup impl) {
        lookup = impl;
    }

    public static ItemStorage item(Level level, BlockPos pos, @Nullable Direction side) {
        try {
            return lookup.item(level, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static FluidStorage fluid(Level level, BlockPos pos, @Nullable Direction side) {
        try {
            return lookup.fluid(level, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static EnergyStorage energy(Level level, BlockPos pos, @Nullable Direction side) {
        try {
            return lookup.energy(level, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean hasAdjacentItemTarget(Level level, BlockPos pos, Direction side) {
        if (level == null || pos == null || side == null) return false;
        try {
            BlockPos adjPos = pos.relative(side);
            if (!level.isLoaded(adjPos) || level.isEmptyBlock(adjPos)) return false;
            ItemStorage adjStorage = item(level, adjPos, side.getOpposite());
            return adjStorage != null && adjStorage.getSlots() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasAdjacentFluidTarget(Level level, BlockPos pos, Direction side) {
        if (level == null || pos == null || side == null) return false;
        try {
            BlockPos adjPos = pos.relative(side);
            if (!level.isLoaded(adjPos) || level.isEmptyBlock(adjPos)) return false;
            FluidStorage adjStorage = fluid(level, adjPos, side.getOpposite());
            return adjStorage != null && adjStorage.getTanks() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
