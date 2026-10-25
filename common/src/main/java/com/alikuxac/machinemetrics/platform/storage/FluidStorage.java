package com.alikuxac.machinemetrics.platform.storage;

public interface FluidStorage {
    int getTanks();

    long getTankCapacity(int tank);

    boolean isTankEmpty(int tank);

    long getTankAmount(int tank);
}