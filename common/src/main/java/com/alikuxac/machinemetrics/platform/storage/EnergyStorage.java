package com.alikuxac.machinemetrics.platform.storage;

public interface EnergyStorage {
    int getEnergyStored();

    int getMaxEnergyStored();

    default boolean canReceive() {
        return false;
    }

    default boolean canExtract() {
        return false;
    }
}
