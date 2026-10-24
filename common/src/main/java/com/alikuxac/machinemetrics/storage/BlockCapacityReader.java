package com.alikuxac.machinemetrics.storage;

import com.alikuxac.machinemetrics.compat.CompatRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class BlockCapacityReader {

    public enum StorageType {
        NONE,
        ITEM,
        FLUID,
        ENERGY
    }

    public record StorageCapacity(
            StorageType type,
            long current,
            long max,
            boolean isInfinite,
            boolean hasStorage
    ) {
        public static final StorageCapacity EMPTY = new StorageCapacity(StorageType.NONE, 0L, 0L, false, false);
    }

    private static final Direction[] ALL_SIDES = new Direction[] {
            null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final long INFINITE_THRESHOLD = Long.MAX_VALUE / 2;

    public static StorageCapacity readCapacity(Level level, BlockPos pos) {
        return readCapacity(level, pos, null);
    }

    public static StorageCapacity readCapacity(Level level, BlockPos pos, Direction hitFace) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return StorageCapacity.EMPTY;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return StorageCapacity.EMPTY;
        }

        boolean hitFaceHasItems = false;
        boolean hitFaceHasFluids = false;
        boolean hitFaceHasEnergy = false;
        if (hitFace != null) {
            try {
                ResourceHandler<ItemResource> faceItem = safeItem(level, pos, hitFace);
                if (faceItem != null && faceItem.size() > 0) {
                    hitFaceHasItems = true;
                }
            } catch (Throwable ignored) {
            }
            try {
                ResourceHandler<FluidResource> faceFluid = safeFluid(level, pos, hitFace);
                if (faceFluid != null && faceFluid.size() > 0) {
                    hitFaceHasFluids = true;
                }
            } catch (Throwable ignored) {
            }
            try {
                EnergyHandler faceEnergy = safeEnergy(level, pos, hitFace);
                if (faceEnergy != null && faceEnergy.getCapacityAsLong() > 0) {
                    hitFaceHasEnergy = true;
                }
            } catch (Throwable ignored) {
            }
        }

        long totalItemCurrent = 0L;
        long totalItemMax = 0L;
        boolean isItemInfinite = false;
        boolean hasItemCapability = false;

        ResourceHandler<ItemResource> itemHandler = resolveItemHandler(level, pos, hitFace);

        if (itemHandler != null && itemHandler.size() > 0) {
            hasItemCapability = true;
            int slots = itemHandler.size();
            for (int i = 0; i < slots; i++) {
                totalItemCurrent += itemHandler.getAmountAsLong(i);
                long limit = itemHandler.getCapacityAsLong(i, itemHandler.getResource(i));
                if (limit >= Integer.MAX_VALUE || limit >= INFINITE_THRESHOLD) {
                    isItemInfinite = true;
                    limit = INFINITE_THRESHOLD;
                }
                totalItemMax += limit;
            }
        }

        if (!hasItemCapability && be instanceof Container container) {
            try {
                int size = container.getContainerSize();
                if (size > 0) {
                    hasItemCapability = true;
                    for (int i = 0; i < size; i++) {
                        ItemStack stack = container.getItem(i);
                        if (!stack.isEmpty()) {
                            totalItemCurrent += stack.getCount();
                        }
                        int slotMax = stack.isEmpty() ? container.getMaxStackSize() : Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                        totalItemMax += slotMax;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        long totalFluidCurrent = 0L;
        long totalFluidMax = 0L;
        boolean isFluidInfinite = false;
        boolean hasFluidCapability = false;

        ResourceHandler<FluidResource> fluidHandler = resolveFluidHandler(level, pos, hitFace);

        if (fluidHandler != null && fluidHandler.size() > 0) {
            hasFluidCapability = true;
            int tanks = fluidHandler.size();
            for (int i = 0; i < tanks; i++) {
                long cap = fluidHandler.getCapacityAsLong(i, fluidHandler.getResource(i));
                if (cap >= Integer.MAX_VALUE || cap >= INFINITE_THRESHOLD) {
                    isFluidInfinite = true;
                    cap = INFINITE_THRESHOLD;
                }
                totalFluidMax += cap;
                totalFluidCurrent += fluidHandler.getAmountAsLong(i);
            }
        }

        long totalEnergyCurrent = 0L;
        long totalEnergyMax = 0L;
        boolean isEnergyInfinite = false;
        boolean hasEnergyCapability = false;

        EnergyHandler energyStorage = resolveEnergyStorage(level, pos, hitFace);

        if (energyStorage != null && energyStorage.getCapacityAsLong() > 0) {
            hasEnergyCapability = true;
            long maxEnergy = energyStorage.getCapacityAsLong();
            if (maxEnergy >= Integer.MAX_VALUE || maxEnergy >= INFINITE_THRESHOLD) {
                isEnergyInfinite = true;
                maxEnergy = INFINITE_THRESHOLD;
            }
            totalEnergyMax += maxEnergy;
            totalEnergyCurrent += energyStorage.getAmountAsLong();
        }

        StorageType selectedType = StorageType.NONE;
        long selectedCurrent = 0L;
        long selectedMax = 0L;
        boolean selectedInfinite = false;

        if (hitFace != null) {
            if (hitFaceHasFluids && !hitFaceHasItems && !hitFaceHasEnergy && totalFluidMax > 0) {
                selectedType = StorageType.FLUID;
            } else if (hitFaceHasEnergy && !hitFaceHasItems && !hitFaceHasFluids && totalEnergyMax > 0) {
                selectedType = StorageType.ENERGY;
            } else if (hitFaceHasItems && !hitFaceHasFluids && !hitFaceHasEnergy && totalItemMax > 0) {
                selectedType = StorageType.ITEM;
            }
        }

        if (selectedType == StorageType.NONE) {
            boolean hasOnlyItems = totalItemMax > 0 && totalFluidMax == 0 && totalEnergyMax == 0;
            boolean hasOnlyFluids = totalFluidMax > 0 && totalItemMax == 0 && totalEnergyMax == 0;
            boolean hasOnlyEnergy = totalEnergyMax > 0 && totalItemMax == 0 && totalFluidMax == 0;

            if (hasOnlyItems) {
                selectedType = StorageType.ITEM;
            } else if (hasOnlyFluids) {
                selectedType = StorageType.FLUID;
            } else if (hasOnlyEnergy) {
                selectedType = StorageType.ENERGY;
            } else if (CompatRegistry.isFluidTankBlock(level, pos) && totalFluidMax > 0) {
                selectedType = StorageType.FLUID;
            } else if (totalFluidMax > 0 && (totalItemMax <= 512 || totalFluidMax >= totalItemMax)) {
                selectedType = StorageType.FLUID;
            } else if (totalEnergyMax > 0 && totalItemMax <= 512 && totalFluidMax == 0) {
                selectedType = StorageType.ENERGY;
            } else if (totalItemMax > 0 && totalItemMax > totalFluidMax) {
                selectedType = StorageType.ITEM;
            } else if (totalFluidMax > 0) {
                selectedType = StorageType.FLUID;
            } else if (totalEnergyMax > 0) {
                selectedType = StorageType.ENERGY;
            }
        }

        switch (selectedType) {
            case ITEM -> {
                selectedCurrent = totalItemCurrent;
                selectedMax = totalItemMax;
                if (isItemInfinite) selectedInfinite = true;
            }
            case FLUID -> {
                selectedCurrent = totalFluidCurrent;
                selectedMax = totalFluidMax;
                if (isFluidInfinite) selectedInfinite = true;
            }
            case ENERGY -> {
                selectedCurrent = totalEnergyCurrent;
                selectedMax = totalEnergyMax;
                if (isEnergyInfinite) selectedInfinite = true;
            }
            default -> {
                return StorageCapacity.EMPTY;
            }
        }

        if (selectedMax == 0 && !selectedInfinite) {
            return StorageCapacity.EMPTY;
        }

        if (selectedInfinite || selectedMax >= INFINITE_THRESHOLD) {
            selectedInfinite = true;
            selectedMax = INFINITE_THRESHOLD;
        }

        return new StorageCapacity(selectedType, selectedCurrent, selectedMax, selectedInfinite, true);
    }

    private static ResourceHandler<ItemResource> resolveItemHandler(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            ResourceHandler<ItemResource> h = safeItem(level, pos, hitFace);
            if (h != null && h.size() > 0) return h;
        }
        ResourceHandler<ItemResource> h = safeItem(level, pos, null);
        if (h != null && h.size() > 0) return h;
        for (Direction side : Direction.values()) {
            h = safeItem(level, pos, side);
            if (h != null && h.size() > 0) return h;
        }
        return null;
    }

    private static ResourceHandler<ItemResource> safeItem(Level level, BlockPos pos, Direction side) {
        try {
            return level.getCapability(Capabilities.Item.BLOCK, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ResourceHandler<FluidResource> resolveFluidHandler(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            ResourceHandler<FluidResource> h = safeFluid(level, pos, hitFace);
            if (h != null && h.size() > 0) return h;
        }
        ResourceHandler<FluidResource> h = safeFluid(level, pos, null);
        if (h != null && h.size() > 0) return h;
        for (Direction side : Direction.values()) {
            h = safeFluid(level, pos, side);
            if (h != null && h.size() > 0) return h;
        }
        return null;
    }

    private static ResourceHandler<FluidResource> safeFluid(Level level, BlockPos pos, Direction side) {
        try {
            return level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static EnergyHandler resolveEnergyStorage(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            EnergyHandler s = safeEnergy(level, pos, hitFace);
            if (s != null && s.getCapacityAsLong() > 0) return s;
        }
        EnergyHandler s = safeEnergy(level, pos, null);
        if (s != null && s.getCapacityAsLong() > 0) return s;
        for (Direction side : Direction.values()) {
            s = safeEnergy(level, pos, side);
            if (s != null && s.getCapacityAsLong() > 0) return s;
        }
        return null;
    }

    private static EnergyHandler safeEnergy(Level level, BlockPos pos, Direction side) {
        try {
            return level.getCapability(Capabilities.Energy.BLOCK, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
