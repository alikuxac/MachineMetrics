package com.alikuxac.machinemetrics.telemetry;

import com.alikuxac.machinemetrics.platform.storage.EnergyStorage;
import com.alikuxac.machinemetrics.platform.storage.FluidStorage;
import com.alikuxac.machinemetrics.platform.storage.ItemStorage;
import com.alikuxac.machinemetrics.platform.storage.StorageAccess;
import mekanism.api.Action;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ISidedChemicalHandler;
import mekanism.api.fluid.ISidedFluidHandler;
import mekanism.api.inventory.ISidedItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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

        ItemStorage itemStorage = StorageAccess.item(level, pos, hitFace);
        FluidStorage fluidStorage = StorageAccess.fluid(level, pos, hitFace);
        EnergyStorage energyStorage = StorageAccess.energy(level, pos, hitFace);

        boolean hasItem = itemStorage != null && itemStorage.getSlots() > 0;
        boolean hasFluid = fluidStorage != null && fluidStorage.getTanks() > 0;
        boolean hasEnergy = energyStorage != null && energyStorage.getMaxEnergyStored() > 0;

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
            if (itemStorage.canInsert()) canIn = true;
            if (itemStorage.canExtract()) canOut = true;
        }

        if (hasFluid) {
            if (fluidStorage.canFill()) canIn = true;
            if (fluidStorage.canDrain()) canOut = true;
        }

        if (hasEnergy) {
            if (energyStorage.canReceive()) canIn = true;
            if (energyStorage.canExtract()) canOut = true;
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
        if (be == null) return null;
        try {
            Class<?> tileMekanismClass = Class.forName("mekanism.common.tile.base.TileEntityMekanism", false, be.getClass().getClassLoader());
            if (!tileMekanismClass.isInstance(be)) {
                return null;
            }

            Direction facing = Direction.NORTH;
            try {
                Method getDirectionMethod = be.getClass().getMethod("getDirection");
                Object dir = getDirectionMethod.invoke(be);
                if (dir instanceof Direction d) {
                    facing = d;
                }
            } catch (Throwable ignored) {
                var state = be.getBlockState();
                if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                } else if (state.hasProperty(BlockStateProperties.FACING)) {
                    facing = state.getValue(BlockStateProperties.FACING);
                }
            }

            RelativeSide relativeSide = RelativeSide.fromDirections(facing, hitFace);

            boolean hasMekInput = false;
            boolean hasMekOutput = false;
            boolean inspectedAnyConfig = false;

            try {
                Method getConfigMethod = be.getClass().getMethod("getConfig");
                Object configComponent = getConfigMethod.invoke(be);
                if (configComponent != null) {
                    Class<?> transmissionTypeEnum = Class.forName("mekanism.common.lib.transmitter.TransmissionType", false, be.getClass().getClassLoader());
                    Object[] transmissionConstants = transmissionTypeEnum.getEnumConstants();

                    for (Method m : configComponent.getClass().getMethods()) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 2 && params[0] == transmissionTypeEnum) {
                            if (params[1] == RelativeSide.class || params[1] == Direction.class) {
                                Object secondArg = params[1] == RelativeSide.class ? relativeSide : hitFace;
                                for (Object transmission : transmissionConstants) {
                                    try {
                                        Object result = m.invoke(configComponent, transmission, secondArg);
                                        if (result != null) {
                                            inspectedAnyConfig = true;
                                            String name = result.toString().toUpperCase();
                                            if (name.contains("INPUT") && name.contains("OUTPUT")) {
                                                hasMekInput = true;
                                                hasMekOutput = true;
                                            } else if (name.contains("INPUT") || name.contains("PULL")) {
                                                hasMekInput = true;
                                            } else if (name.contains("OUTPUT") || name.contains("PUSH") || name.contains("EJECT")) {
                                                hasMekOutput = true;
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        } else if (params.length == 1 && params[0] == transmissionTypeEnum) {
                            // Example: getConfig(transmission) -> ConfigInfo/ISideConfiguration
                            for (Object transmission : transmissionConstants) {
                                try {
                                    Object sideConfig = m.invoke(configComponent, transmission);
                                    if (sideConfig != null) {
                                        for (Method sm : sideConfig.getClass().getMethods()) {
                                            Class<?>[] sParams = sm.getParameterTypes();
                                            if (sParams.length == 1 && (sParams[0] == RelativeSide.class || sParams[0] == Direction.class)) {
                                                Object sArg = sParams[0] == RelativeSide.class ? relativeSide : hitFace;
                                                Object result = sm.invoke(sideConfig, sArg);
                                                if (result != null) {
                                                    inspectedAnyConfig = true;
                                                    String name = result.toString().toUpperCase();
                                                    if (name.contains("INPUT") && name.contains("OUTPUT")) {
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
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            if (be instanceof ISidedItemHandler sidedItem) {
                int slots = sidedItem.getSlots(hitFace);
                if (slots > 0) {
                    inspectedAnyConfig = true;
                    for (int i = 0; i < slots; i++) {
                        if (!sidedItem.extractItem(i, 1, hitFace, Action.SIMULATE).isEmpty()) {
                            hasMekOutput = true;
                        }
                        ItemStack inSlot = sidedItem.getStackInSlot(i, hitFace);
                        if (!inSlot.isEmpty()) {
                            if (sidedItem.insertItem(i, inSlot.copyWithCount(1), hitFace, Action.SIMULATE).getCount() < 1) {
                                hasMekInput = true;
                            }
                        }
                    }
                }
            }

            if (be instanceof ISidedChemicalHandler sidedChem) {
                int tanks = sidedChem.getCountChemicalTanks(hitFace);
                if (tanks > 0) {
                    inspectedAnyConfig = true;
                    for (int i = 0; i < tanks; i++) {
                        var chemInTank = sidedChem.getChemicalInTank(i, hitFace);
                        if (!chemInTank.isEmpty()) {
                            if (sidedChem.extractChemical(i, 1, hitFace, Action.SIMULATE).getAmount() > 0) {
                                hasMekOutput = true;
                            }
                            if (sidedChem.insertChemical(i, chemInTank.copyWithAmount(1), hitFace, Action.SIMULATE).getAmount() < 1) {
                                hasMekInput = true;
                            }
                        }
                    }
                }
            }

            if (be instanceof ISidedFluidHandler sidedFluid) {
                int tanks = sidedFluid.getTanks(hitFace);
                if (tanks > 0) {
                    inspectedAnyConfig = true;
                    for (int i = 0; i < tanks; i++) {
                        var fluidInTank = sidedFluid.getFluidInTank(i, hitFace);
                        if (!fluidInTank.isEmpty()) {
                            if (sidedFluid.extractFluid(i, 1, hitFace, Action.SIMULATE).getAmount() > 0) {
                                hasMekOutput = true;
                            }
                            if (sidedFluid.insertFluid(i, fluidInTank.copyWithAmount(1), hitFace, Action.SIMULATE).getAmount() < 1) {
                                hasMekInput = true;
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
