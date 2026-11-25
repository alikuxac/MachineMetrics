package com.alikuxac.machinemetrics.forge.telemetry;

import com.alikuxac.machinemetrics.platform.storage.EnergyStorage;
import com.alikuxac.machinemetrics.platform.storage.FluidStorage;
import com.alikuxac.machinemetrics.platform.storage.ItemStorage;
import com.alikuxac.machinemetrics.platform.storage.StorageAccess;
import com.alikuxac.machinemetrics.telemetry.AbstractTelemetrySampler;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class ForgeServerTelemetrySampler extends AbstractTelemetrySampler {

    private static final ForgeServerTelemetrySampler INSTANCE = new ForgeServerTelemetrySampler();

    private ForgeServerTelemetrySampler() {}

    public static TelemetryData sampleTargetBlock(ServerLevel level, BlockPos pos) {
        return INSTANCE.sample(level, pos);
    }

    public static TelemetryData sampleTargetBlock(ServerLevel level, BlockPos pos, Direction hitFace) {
        return INSTANCE.sample(level, pos, hitFace);
    }

    @Override
    protected ItemStorage lookupItemStorage(Level level, BlockPos pos, Direction side) {
        return StorageAccess.item(level, pos, side);
    }

    @Override
    protected FluidStorage lookupFluidStorage(Level level, BlockPos pos, Direction side) {
        return StorageAccess.fluid(level, pos, side);
    }

    @Override
    protected EnergyStorage lookupEnergyStorage(Level level, BlockPos pos, Direction side) {
        return StorageAccess.energy(level, pos, side);
    }
}
