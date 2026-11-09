package com.alikuxac.machinemetrics.telemetry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.lang.reflect.Method;

public final class DirectionalCapabilityScanner {

    public record TargetedResult(
            Direction face,
            DirectionalIo io,
            TelemetryData.BottleneckState state,
            float throughput
    ) {
        public static final TargetedResult NONE = new TargetedResult(null, DirectionalIo.NONE, TelemetryData.BottleneckState.IDLE, 0.0f);
    }

    private DirectionalCapabilityScanner() {}

    public static TargetedResult scan(
            Level level,
            BlockPos pos,
            Direction hitFace,
            BlockEntity be,
            float itemRate,
            float fluidRate,
            float chemicalRate,
            long currentItems,
            long maxItems,
            long currentFluid,
            long maxFluid,
            long currentEnergy,
            long maxEnergy
    ) {
        if (level == null || pos == null || hitFace == null) {
            return TargetedResult.NONE;
        }

        TargetedResult mekanismResult = inspectMekanismSideConfig(be, hitFace, itemRate, fluidRate, chemicalRate, currentItems, maxItems, currentFluid, maxFluid);
        if (mekanismResult != null) {
            return mekanismResult;
        }

        ResourceHandler<ItemResource> itemHandler = null;
        try {
            itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, hitFace);
        } catch (Throwable ignored) {}

        ResourceHandler<FluidResource> fluidHandler = null;
        try {
            fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, hitFace);
        } catch (Throwable ignored) {}

        EnergyHandler energyHandler = null;
        try {
            energyHandler = level.getCapability(Capabilities.Energy.BLOCK, pos, hitFace);
        } catch (Throwable ignored) {}

        boolean hasItem = itemHandler != null && itemHandler.size() > 0;
        boolean hasFluid = fluidHandler != null && fluidHandler.size() > 0;
        boolean hasEnergy = energyHandler != null && energyHandler.getCapacityAsLong() > 0;

        if (!hasItem && !hasFluid && !hasEnergy) {
            return TargetedResult.NONE;
        }

        boolean canIn = false;
        boolean canOut = false;

        if (be instanceof WorldlyContainer worldly) {
            int[] slots = worldly.getSlotsForFace(hitFace);
            if (slots != null && slots.length > 0) {
                for (int slot : slots) {
                    if (worldly.canTakeItemThroughFace(slot, ItemStack.EMPTY, hitFace)) {
                        canOut = true;
                    }
                    if (worldly.canPlaceItemThroughFace(slot, ItemStack.EMPTY, hitFace)) {
                        canIn = true;
                    }
                }
            }
        } else if (hasItem) {
            canIn = true;
            canOut = true;
        }

        if (hasFluid) {
            canIn = true;
            canOut = true;
        }

        if (hasEnergy) {
            canIn = true;
            canOut = true;
        }

        float totalFaceThroughput = itemRate + fluidRate + chemicalRate;

        DirectionalIo io;
        if (canIn && canOut) {
            io = DirectionalIo.BIDIRECTIONAL;
        } else if (canIn) {
            io = DirectionalIo.INPUT;
        } else if (canOut) {
            io = DirectionalIo.OUTPUT;
        } else {
            return TargetedResult.NONE;
        }

        TelemetryData.BottleneckState state;
        if (totalFaceThroughput > 0.001f) {
            state = TelemetryData.BottleneckState.OPTIMAL;
        } else {
            switch (io) {
                case INPUT -> {
                    boolean isEmpty = (hasItem && currentItems == 0)
                            || (hasFluid && currentFluid == 0)
                            || (hasEnergy && currentEnergy == 0);
                    state = isEmpty ? TelemetryData.BottleneckState.STARVED : TelemetryData.BottleneckState.IDLE;
                }
                case OUTPUT -> {
                    boolean isClogged = (hasItem && maxItems > 0 && ((float) currentItems / maxItems) > 0.90f)
                            || (hasFluid && maxFluid > 0 && ((float) currentFluid / maxFluid) > 0.90f);
                    state = isClogged ? TelemetryData.BottleneckState.CLOGGED : TelemetryData.BottleneckState.IDLE;
                }
                case BIDIRECTIONAL -> {
                    boolean isClogged = (hasItem && maxItems > 0 && ((float) currentItems / maxItems) > 0.90f)
                            || (hasFluid && maxFluid > 0 && ((float) currentFluid / maxFluid) > 0.90f);
                    if (isClogged) {
                        state = TelemetryData.BottleneckState.CLOGGED;
                    } else {
                        state = TelemetryData.BottleneckState.IDLE;
                    }
                }
                default -> state = TelemetryData.BottleneckState.IDLE;
            }
        }

        return new TargetedResult(hitFace, io, state, totalFaceThroughput);
    }

    private static TargetedResult inspectMekanismSideConfig(
            BlockEntity be,
            Direction hitFace,
            float itemRate,
            float fluidRate,
            float chemicalRate,
            long currentItems,
            long maxItems,
            long currentFluid,
            long maxFluid
    ) {
        if (be == null || hitFace == null) return null;
        try {
            boolean hasMekInput = false;
            boolean hasMekOutput = false;
            boolean inspectedAnyConfig = false;

            Class<?> beClass = be.getClass();
            Method getConfigMethod = null;
            for (Method m : beClass.getMethods()) {
                if (m.getName().equals("getConfig") && m.getParameterCount() == 0) {
                    getConfigMethod = m;
                    break;
                }
            }

            if (getConfigMethod != null) {
                Object configComponent = getConfigMethod.invoke(be);
                if (configComponent != null) {
                    ClassLoader cl = beClass.getClassLoader();
                    Class<?> tileConfigCls = Class.forName("mekanism.common.tile.component.TileComponentConfig", false, cl);
                    Class<?> transTypeCls = Class.forName("mekanism.common.lib.transmitter.TransmissionType", false, cl);
                    Class<?> relSideCls = Class.forName("mekanism.api.RelativeSide", false, cl);

                    Direction facing = Direction.NORTH;
                    try {
                        var state = be.getBlockState();
                        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                        } else if (state.hasProperty(BlockStateProperties.FACING)) {
                            facing = state.getValue(BlockStateProperties.FACING);
                        }
                    } catch (Throwable ignored) {}

                    Method byDirections = relSideCls.getMethod("byDirections", Direction.class, Direction.class);
                    Object relativeSide = byDirections.invoke(null, facing, hitFace);

                    Method getConfigForTrans = tileConfigCls.getMethod("getConfig", transTypeCls);
                    Object[] transConstants = transTypeCls.getEnumConstants();

                    if (transConstants != null && relativeSide != null) {
                        for (Object trans : transConstants) {
                            String tName = trans.toString();
                            if (tName.equals("ITEM") || tName.equals("FLUID") || tName.equals("GAS")
                                    || tName.equals("INFUSION") || tName.equals("PIGMENT") || tName.equals("SLURRY")
                                    || tName.equals("CHEMICAL") || tName.equals("ENERGY")) {
                                try {
                                    Object configInfo = getConfigForTrans.invoke(configComponent, trans);
                                    if (configInfo != null) {
                                        Method getDataType = configInfo.getClass().getMethod("getDataType", relSideCls);
                                        Object dataType = getDataType.invoke(configInfo, relativeSide);
                                        if (dataType != null) {
                                            inspectedAnyConfig = true;
                                            String name = dataType.toString().toUpperCase();
                                            if (!name.contains("NONE") && !name.contains("EMPTY") && !name.contains("DISABLED")) {
                                                if (name.contains("INPUT_OUTPUT") || name.contains("BIDIRECTIONAL") || name.contains("BOTH")) {
                                                    hasMekInput = true;
                                                    hasMekOutput = true;
                                                } else if (name.contains("INPUT") || name.contains("PULL")) {
                                                    hasMekInput = true;
                                                } else if (name.contains("OUTPUT") || name.contains("PUSH") || name.contains("EJECT")) {
                                                    hasMekOutput = true;
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }

            if (inspectedAnyConfig && !hasMekInput && !hasMekOutput) {
                return TargetedResult.NONE;
            }

            if (!hasMekInput && !hasMekOutput) {
                return null;
            }

            DirectionalIo io;
            if (hasMekInput && hasMekOutput) {
                io = DirectionalIo.BIDIRECTIONAL;
            } else if (hasMekInput) {
                io = DirectionalIo.INPUT;
            } else {
                io = DirectionalIo.OUTPUT;
            }

            float totalRate = itemRate + fluidRate + chemicalRate;
            TelemetryData.BottleneckState state;
            if (totalRate > 0.001f) {
                state = TelemetryData.BottleneckState.OPTIMAL;
            } else if (io == DirectionalIo.INPUT && currentItems == 0 && currentFluid == 0) {
                state = TelemetryData.BottleneckState.STARVED;
            } else if (io == DirectionalIo.OUTPUT && ((maxItems > 0 && (float) currentItems / maxItems > 0.90f) || (maxFluid > 0 && (float) currentFluid / maxFluid > 0.90f))) {
                state = TelemetryData.BottleneckState.CLOGGED;
            } else {
                state = TelemetryData.BottleneckState.IDLE;
            }

            return new TargetedResult(hitFace, io, state, totalRate);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

