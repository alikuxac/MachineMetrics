package com.alikuxac.machinemetrics.neoforge.platform;

import com.alikuxac.machinemetrics.platform.storage.EnergyStorage;
import com.alikuxac.machinemetrics.platform.storage.FluidStorage;
import com.alikuxac.machinemetrics.platform.storage.ItemStorage;
import com.alikuxac.machinemetrics.platform.storage.StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class NeoForgeStorageLookup implements StorageAccess.Lookup {

    public static void install() {
        StorageAccess.setLookup(new NeoForgeStorageLookup());
    }

    @Override
    public ItemStorage item(Level level, BlockPos pos, Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
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
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
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
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        if (storage == null) {
            return null;
        }
        return new EnergyStorage() {
            @Override public int getEnergyStored() { return storage.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return storage.getMaxEnergyStored(); }
        };
    }
}