package com.alikuxac.machinemetrics.storage;

import com.alikuxac.machinemetrics.compat.CompatRegistry;
import com.alikuxac.machinemetrics.platform.storage.EnergyStorage;
import com.alikuxac.machinemetrics.platform.storage.FluidStorage;
import com.alikuxac.machinemetrics.platform.storage.ItemStorage;
import com.alikuxac.machinemetrics.platform.storage.StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

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
                ItemStorage faceItem = StorageAccess.item(level, pos, hitFace);
                if (faceItem != null && faceItem.getSlots() > 0) {
                    hitFaceHasItems = true;
                }
            } catch (Throwable ignored) {
            }
            try {
                FluidStorage faceFluid = StorageAccess.fluid(level, pos, hitFace);
                if (faceFluid != null && faceFluid.getTanks() > 0) {
                    hitFaceHasFluids = true;
                }
            } catch (Throwable ignored) {
            }
            try {
                EnergyStorage faceEnergy = StorageAccess.energy(level, pos, hitFace);
                if (faceEnergy != null && faceEnergy.getMaxEnergyStored() > 0) {
                    hitFaceHasEnergy = true;
                }
            } catch (Throwable ignored) {
            }
        }

        long totalItemCurrent = 0L;
        long totalItemMax = 0L;
        boolean isItemInfinite = false;
        boolean hasItemCapability = false;

        ItemStorage itemHandler = resolveItemHandler(level, pos, hitFace);

        if (itemHandler != null && itemHandler.getSlots() > 0) {
            hasItemCapability = true;
            int slots = itemHandler.getSlots();
            for (int i = 0; i < slots; i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    totalItemCurrent += stack.getCount();
                }
                long limit = itemHandler.getSlotLimit(i);
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

        FluidStorage fluidHandler = resolveFluidHandler(level, pos, hitFace);

        if (fluidHandler != null && fluidHandler.getTanks() > 0) {
            hasFluidCapability = true;
            int tanks = fluidHandler.getTanks();
            for (int i = 0; i < tanks; i++) {
                long cap = fluidHandler.getTankCapacity(i);
                if (cap >= Integer.MAX_VALUE || cap >= INFINITE_THRESHOLD) {
                    isFluidInfinite = true;
                    cap = INFINITE_THRESHOLD;
                }
                totalFluidMax += cap;
                if (!fluidHandler.isTankEmpty(i)) {
                    totalFluidCurrent += fluidHandler.getTankAmount(i);
                }
            }
        }

        long totalEnergyCurrent = 0L;
        long totalEnergyMax = 0L;
        boolean isEnergyInfinite = false;
        boolean hasEnergyCapability = false;

        EnergyStorage energyStorage = resolveEnergyStorage(level, pos, hitFace);

        if (energyStorage != null && energyStorage.getMaxEnergyStored() > 0) {
            hasEnergyCapability = true;
            long maxEnergy = energyStorage.getMaxEnergyStored();
            if (maxEnergy >= Integer.MAX_VALUE || maxEnergy >= INFINITE_THRESHOLD) {
                isEnergyInfinite = true;
                maxEnergy = INFINITE_THRESHOLD;
            }
            totalEnergyMax += maxEnergy;
            totalEnergyCurrent += energyStorage.getEnergyStored();
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

    private static ItemStorage resolveItemHandler(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            ItemStorage h = StorageAccess.item(level, pos, hitFace);
            if (h != null && h.getSlots() > 0) return h;
        }
        ItemStorage h = StorageAccess.item(level, pos, null);
        if (h != null && h.getSlots() > 0) return h;
        for (Direction side : Direction.values()) {
            h = StorageAccess.item(level, pos, side);
            if (h != null && h.getSlots() > 0) return h;
        }
        return null;
    }

    private static FluidStorage resolveFluidHandler(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            FluidStorage h = StorageAccess.fluid(level, pos, hitFace);
            if (h != null && h.getTanks() > 0) return h;
        }
        FluidStorage h = StorageAccess.fluid(level, pos, null);
        if (h != null && h.getTanks() > 0) return h;
        for (Direction side : Direction.values()) {
            h = StorageAccess.fluid(level, pos, side);
            if (h != null && h.getTanks() > 0) return h;
        }
        return null;
    }

    private static EnergyStorage resolveEnergyStorage(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            EnergyStorage s = StorageAccess.energy(level, pos, hitFace);
            if (s != null && s.getMaxEnergyStored() > 0) return s;
        }
        EnergyStorage s = StorageAccess.energy(level, pos, null);
        if (s != null && s.getMaxEnergyStored() > 0) return s;
        for (Direction side : Direction.values()) {
            s = StorageAccess.energy(level, pos, side);
            if (s != null && s.getMaxEnergyStored() > 0) return s;
        }
        return null;
    }
}
