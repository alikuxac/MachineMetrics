package com.alikuxac.machinemetrics.forge.platform;

import com.alikuxac.machinemetrics.platform.storage.EnergyStorage;
import com.alikuxac.machinemetrics.platform.storage.FluidStorage;
import com.alikuxac.machinemetrics.platform.storage.ItemStorage;
import com.alikuxac.machinemetrics.platform.storage.StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public final class ForgeStorageLookup implements StorageAccess.Lookup {

    public static void install() {
        StorageAccess.setLookup(new ForgeStorageLookup());
    }

    private static BlockEntity be(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }

    @Override
    public ItemStorage item(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = be(level, pos);
        if (blockEntity == null) {
            return null;
        }
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        if (handler == null) {
            return null;
        }
        return new ItemStorage() {
            @Override public int getSlots() { return handler.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return handler.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return handler.getSlotLimit(slot); }
        };
    }

    @Override
    public FluidStorage fluid(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = be(level, pos);
        if (blockEntity == null) {
            return null;
        }
        IFluidHandler handler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        if (handler == null) {
            return null;
        }
        return new FluidStorage() {
            @Override public int getTanks() { return handler.getTanks(); }
            @Override public long getTankCapacity(int tank) { return handler.getTankCapacity(tank); }
            @Override public boolean isTankEmpty(int tank) { return handler.getFluidInTank(tank).isEmpty(); }
            @Override public long getTankAmount(int tank) { return handler.getFluidInTank(tank).getAmount(); }
        };
    }

    @Override
    public EnergyStorage energy(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = be(level, pos);
        if (blockEntity == null) {
            return null;
        }
        IEnergyStorage storage = blockEntity.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
        if (storage == null) {
            return null;
        }
        return new EnergyStorage() {
            @Override public int getEnergyStored() { return storage.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return storage.getMaxEnergyStored(); }
        };
    }
}