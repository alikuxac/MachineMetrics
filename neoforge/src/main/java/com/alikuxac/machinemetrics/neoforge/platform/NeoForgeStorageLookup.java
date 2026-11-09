package com.alikuxac.machinemetrics.neoforge.platform;

import com.alikuxac.machinemetrics.platform.storage.EnergyStorage;
import com.alikuxac.machinemetrics.platform.storage.FluidStorage;
import com.alikuxac.machinemetrics.platform.storage.ItemStorage;
import com.alikuxac.machinemetrics.platform.storage.StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;

public final class NeoForgeStorageLookup implements StorageAccess.Lookup {

    public static void install() {
        StorageAccess.setLookup(new NeoForgeStorageLookup());
    }

    private static int getSidedSlots(Object handler, Direction side) {
        if (handler == null || side == null) return -1;
        try {
            Method m = handler.getClass().getMethod("getSlots", Direction.class);
            return (Integer) m.invoke(handler, side);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int getSidedTanks(Object handler, Direction side) {
        if (handler == null || side == null) return -1;
        try {
            Method m = handler.getClass().getMethod("getTanks", Direction.class);
            return (Integer) m.invoke(handler, side);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @Override
    public ItemStorage item(Level level, BlockPos pos, Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) {
            return null;
        }

        int sidedSlots = getSidedSlots(handler, side);
        if (sidedSlots == 0) {
            return null;
        }

        return new ItemStorage() {
            @Override public int getSlots() {
                return sidedSlots > 0 ? sidedSlots : handler.getSlots();
            }
            @Override public ItemStack getStackInSlot(int slot) { return handler.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return handler.getSlotLimit(slot); }
            @Override public boolean canExtract() {
                for (int i = 0; i < getSlots(); i++) {
                    ItemStack inSlot = handler.getStackInSlot(i);
                    if (!inSlot.isEmpty() && !handler.extractItem(i, 1, true).isEmpty()) {
                        return true;
                    }
                }
                return false;
            }
            @Override public boolean canInsert() {
                for (int i = 0; i < getSlots(); i++) {
                    ItemStack inSlot = handler.getStackInSlot(i);
                    if (!inSlot.isEmpty()) {
                        ItemStack testStack = inSlot.copyWithCount(1);
                        if (handler.insertItem(i, testStack, true).getCount() < 1) {
                            return true;
                        }
                    } else {
                        ItemStack test = new ItemStack(Items.DIRT);
                        if (handler.isItemValid(i, test) && handler.insertItem(i, test, true).getCount() < 1) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    @Override
    public FluidStorage fluid(Level level, BlockPos pos, Direction side) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null) {
            return null;
        }

        int sidedTanks = getSidedTanks(handler, side);
        if (sidedTanks == 0) {
            return null;
        }

        return new FluidStorage() {
            @Override public int getTanks() {
                return sidedTanks > 0 ? sidedTanks : handler.getTanks();
            }
            @Override public long getTankCapacity(int tank) { return handler.getTankCapacity(tank); }
            @Override public boolean isTankEmpty(int tank) { return handler.getFluidInTank(tank).isEmpty(); }
            @Override public long getTankAmount(int tank) { return handler.getFluidInTank(tank).getAmount(); }
            @Override public boolean canDrain() {
                return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
            }
            @Override public boolean canFill() {
                for (int i = 0; i < getTanks(); i++) {
                    var tank = handler.getFluidInTank(i);
                    if (!tank.isEmpty()) {
                        var testStack = tank.copyWithAmount(1);
                        if (handler.fill(testStack, IFluidHandler.FluidAction.SIMULATE) > 0) {
                            return true;
                        }
                    } else {
                        var testFluid = new FluidStack(Fluids.WATER, 1);
                        if (handler.isFluidValid(i, testFluid) && handler.fill(testFluid, IFluidHandler.FluidAction.SIMULATE) > 0) {
                            return true;
                        }
                    }
                }
                return false;
            }
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
            @Override public boolean canReceive() { return storage.canReceive(); }
            @Override public boolean canExtract() { return storage.canExtract(); }
        };
    }
}
