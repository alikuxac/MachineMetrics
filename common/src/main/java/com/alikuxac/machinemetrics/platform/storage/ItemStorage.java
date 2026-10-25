package com.alikuxac.machinemetrics.platform.storage;

import net.minecraft.world.item.ItemStack;

public interface ItemStorage {
    int getSlots();

    ItemStack getStackInSlot(int slot);

    int getSlotLimit(int slot);
}