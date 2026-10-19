package com.alikuxac.machinemetrics.neoforge.telemetry;

import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector;
import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.compat.mekanism.MekanismChemicalInspector;
import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerTelemetrySampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTelemetrySampler.class);
    private static final Map<BlockPos, SampleHistory> CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> INERT_BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final long CLEANUP_EXPIRE_MS = 10000L;
    private static final long INERT_EXPIRE_MS = 5000L;

    private static final Direction[] SIDES_TO_QUERY = new Direction[] { null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

    private record Sample(long timestampMs, int[] itemSlots, int[] fluidTanks, int energyAmount, int totalItems, int totalFluid, float[] sideMetrics) {}

    private static class SampleHistory {
        private Sample previousSample;
        private Sample lastSample;
        private long lastAccessMs;
        private long lastActiveTimeMs;
        private float lastItemTp;
        private float lastFluidTp;
        private float lastEnergyDelta;
        private float[] lastSideRates = new float[18];
        private int cachedItemSlotCount = -1;
        private int cachedMaxItemCapacity = 0;

        public SampleHistory(Sample initialSample) {
            this.lastSample = initialSample;
            this.lastAccessMs = System.currentTimeMillis();
            this.lastActiveTimeMs = 0L;
        }

        public void addSample(Sample sample) {
            this.previousSample = this.lastSample;
            this.lastSample = sample;
            this.lastAccessMs = System.currentTimeMillis();
        }
    }


    public static TelemetryData sampleTargetBlock(ServerLevel level, BlockPos pos) {
        return sampleTargetBlock(level, pos, null);
    }

    public static TelemetryData sampleTargetBlock(ServerLevel level, BlockPos pos, Direction hitFace) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return TelemetryData.empty();
        }

        long now = System.currentTimeMillis();
        cleanupStaleEntries(now);

        Long inertTimestamp = INERT_BLOCK_CACHE.get(pos);
        if (inertTimestamp != null) {
            if (now - inertTimestamp < INERT_EXPIRE_MS) {
                return TelemetryData.empty();
            } else {
                INERT_BLOCK_CACHE.remove(pos);
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            INERT_BLOCK_CACHE.put(pos, now);
            return TelemetryData.empty();
        }

        String beClassName = be.getClass().getName().toLowerCase();
        boolean allowAe2 = ServerConfig.ALLOW_AE2 == null || ServerConfig.ALLOW_AE2.get();
        boolean isAe2Block = beClassName.contains("appeng");

        if ((isAe2Block && !allowAe2 && (beClassName.contains("cable") || beClassName.contains("multipart") || beClassName.contains("bus")))
                || beClassName.contains("waystone")) {
            INERT_BLOCK_CACHE.put(pos, now);
            return TelemetryData.empty();
        }

        SampleHistory existingHistory = CACHE.get(pos);

        boolean allowItems = ServerConfig.ALLOW_ITEMS == null || ServerConfig.ALLOW_ITEMS.get();
        boolean allowFluids = ServerConfig.ALLOW_FLUIDS == null || ServerConfig.ALLOW_FLUIDS.get();
        boolean allowEnergy = ServerConfig.ALLOW_ENERGY == null || ServerConfig.ALLOW_ENERGY.get();
        boolean allowKinetic = ServerConfig.ALLOW_KINETIC == null || ServerConfig.ALLOW_KINETIC.get();
        boolean allowChemicals = ServerConfig.ALLOW_CHEMICALS == null || ServerConfig.ALLOW_CHEMICALS.get();
        boolean allowSides = ServerConfig.ALLOW_SIDE_METRICS == null || ServerConfig.ALLOW_SIDE_METRICS.get();

        int currentItems = 0;
        int maxItems = 0;
        int currentFluid = 0;
        int maxFluid = 0;
        int currentEnergy = 0;
        int maxEnergy = 0;

        boolean hasAnyCapability = false;

        Direction[] sidesToQuery = SIDES_TO_QUERY;
        float[] sideMetrics = new float[18];

        IntArrayList allSlotCounts = new IntArrayList();
        Set<IItemHandler> processedItemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        if (allowItems) {
            for (Direction side : sidesToQuery) {
                IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                if (itemHandler == null || itemHandler.getSlots() <= 0) {
                    continue;
                }

                hasAnyCapability = true;

                if (side != null && allowSides) {
                    int sideCount = 0;
                    for (int i = 0; i < itemHandler.getSlots(); i++) {
                        var stack = itemHandler.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            sideCount += stack.getCount();
                        }
                    }
                    sideMetrics[side.ordinal()] = (float) sideCount;
                }
                
                if (processedItemHandlers.add(itemHandler)) {
                    int slotCount = itemHandler.getSlots();
                    boolean useCachedLimit = existingHistory != null && existingHistory.cachedItemSlotCount == slotCount && existingHistory.cachedMaxItemCapacity > 0;
                    int handlerMaxCap = 0;

                    if (useCachedLimit) {
                        handlerMaxCap = existingHistory.cachedMaxItemCapacity;
                        for (int i = 0; i < slotCount; i++) {
                            var stack = itemHandler.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                int count = stack.getCount();
                                currentItems += count;
                                allSlotCounts.add(count);
                            } else {
                                allSlotCounts.add(0);
                            }
                        }
                    } else {
                        for (int i = 0; i < slotCount; i++) {
                            handlerMaxCap += itemHandler.getSlotLimit(i);
                            var stack = itemHandler.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                int count = stack.getCount();
                                currentItems += count;
                                allSlotCounts.add(count);
                            } else {
                                allSlotCounts.add(0);
                            }
                        }
                        if (existingHistory != null) {
                            existingHistory.cachedItemSlotCount = slotCount;
                            existingHistory.cachedMaxItemCapacity = handlerMaxCap;
                        }
                    }
                    maxItems += handlerMaxCap;
                }
            }
        }

        int[] itemSlots = allSlotCounts.toIntArray();

        IntArrayList allTankAmounts = new IntArrayList();
        Set<IFluidHandler> processedFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        if (allowFluids) {
            for (Direction side : sidesToQuery) {
                IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
                if (fluidHandler == null || fluidHandler.getTanks() <= 0) {
                    continue;
                }

                hasAnyCapability = true;

                if (side != null && allowSides) {
                    int sideFluidAmount = 0;
                    for (int i = 0; i < fluidHandler.getTanks(); i++) {
                        var stack = fluidHandler.getFluidInTank(i);
                        if (!stack.isEmpty()) {
                            sideFluidAmount += stack.getAmount();
                        }
                    }

                    sideMetrics[6 + side.ordinal()] = (float) sideFluidAmount;
                }

                if (processedFluidHandlers.add(fluidHandler)) {
                    for (int i = 0; i < fluidHandler.getTanks(); i++) {
                        var fluidStack = fluidHandler.getFluidInTank(i);
                        int tankCap = fluidHandler.getTankCapacity(i);
                        if (!fluidStack.isEmpty()) {
                            currentFluid += fluidStack.getAmount();
                            allTankAmounts.add(fluidStack.getAmount());
                        } else {
                            allTankAmounts.add(0);
                        }
                        maxFluid += tankCap;
                    }
                }
            }
        }
        int[] fluidTanks = allTankAmounts.toIntArray();

        if (allowChemicals && allowSides) {
            for (Direction side : sidesToQuery) {
                try {
                    if (side != null) {
                        var chemOpt = MekanismChemicalInspector.inspectChemicals(level, pos, side);
                        if (chemOpt.isPresent()) {
                            sideMetrics[12 + side.ordinal()] = (float) chemOpt.get().amount();
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        Set<IEnergyStorage> processedEnergyStorages = Collections.newSetFromMap(new IdentityHashMap<>());

        if (allowEnergy) {
            for (Direction side : sidesToQuery) {
                try {
                    IEnergyStorage energyStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
                    if (energyStorage != null && processedEnergyStorages.add(energyStorage)) {
                        int maxCap = energyStorage.getMaxEnergyStored();
                        if (maxCap > 0) {
                            hasAnyCapability = true;
                            currentEnergy += energyStorage.getEnergyStored();
                            maxEnergy += maxCap;
                        }
                    }
                } catch (Throwable ignored) {
                    // Ignore capability query exception
                }
            }
        }

        float kineticSpeed = 0f;
        float kineticStress = 0f;
        float kineticCapacity = 0f;
        float kineticBlockStress = 0f;
        boolean isOverstressed = false;
        boolean hasKinetic = false;
        CreateKineticInspector.RotationDirection kineticDirection = CreateKineticInspector.RotationDirection.STATIONARY;

        if (allowKinetic) {
            try {
                var kineticOpt = CreateKineticInspector.inspectKinetic(level, pos, hitFace);
                if (kineticOpt.isPresent()) {
                    var kData = kineticOpt.get();
                    hasKinetic = true;
                    kineticSpeed = kData.speed();
                    kineticStress = kData.stress();
                    kineticCapacity = kData.capacity();
                    kineticBlockStress = kData.blockStress();
                    isOverstressed = kData.isOverstressed();
                    kineticDirection = kData.direction();
                    hasAnyCapability = true;
                }
            } catch (Throwable ignored) {
            }
        }

        if (!hasAnyCapability || (maxItems == 0 && maxFluid == 0 && maxEnergy == 0 && !hasKinetic)) {
            INERT_BLOCK_CACHE.put(pos, now);
            return TelemetryData.empty();
        }

        INERT_BLOCK_CACHE.remove(pos);

        Sample currentSample = new Sample(now, itemSlots, fluidTanks, currentEnergy, currentItems, currentFluid, sideMetrics);
        SampleHistory history = CACHE.computeIfAbsent(pos, p -> new SampleHistory(currentSample));

        if (now - history.lastSample.timestampMs() >= 100L) {
            history.addSample(currentSample);
        }

        float itemThroughput = 0f;
        float fluidThroughput = 0f;
        float energyDelta = 0f;
        float[] sideRates = new float[18];
        float itemNetRate = 0f;
        float fluidNetRate = 0f;
        float energyNetRate = 0f;

        if (history.previousSample != null) {
            float timeDiffSec = (history.lastSample.timestampMs() - history.previousSample.timestampMs()) / 1000f;
            if (timeDiffSec > 0) {
                int[] prevSlots = history.previousSample.itemSlots();
                int[] currSlots = history.lastSample.itemSlots();
                int slotDeltaSum = 0;
                int maxLen = Math.max(prevSlots.length, currSlots.length);
                for (int i = 0; i < maxLen; i++) {
                    int p = i < prevSlots.length ? prevSlots[i] : 0;
                    int c = i < currSlots.length ? currSlots[i] : 0;
                    slotDeltaSum += Math.abs(c - p);
                }
                itemThroughput = (slotDeltaSum / 2.0f) / timeDiffSec;
                itemNetRate = (history.lastSample.totalItems() - history.previousSample.totalItems()) / timeDiffSec;

                int[] prevTanks = history.previousSample.fluidTanks();
                int[] currTanks = history.lastSample.fluidTanks();
                int tankDeltaSum = 0;
                int maxTankLen = Math.max(prevTanks.length, currTanks.length);
                for (int i = 0; i < maxTankLen; i++) {
                    int p = i < prevTanks.length ? prevTanks[i] : 0;
                    int c = i < currTanks.length ? currTanks[i] : 0;
                    tankDeltaSum += Math.abs(c - p);
                }
                fluidThroughput = (tankDeltaSum / 2.0f) / timeDiffSec;
                fluidNetRate = (history.lastSample.totalFluid() - history.previousSample.totalFluid()) / timeDiffSec;

                float timeDiffTicks = timeDiffSec * 20f;
                energyDelta = (history.lastSample.energyAmount() - history.previousSample.energyAmount()) / timeDiffTicks;
                energyNetRate = energyDelta;

                float[] prevSideMetrics = history.previousSample.sideMetrics();
                float[] currSideMetrics = history.lastSample.sideMetrics();
                if (prevSideMetrics != null && currSideMetrics != null) {
                    for (int i = 0; i < 18; i++) {
                        float p = i < prevSideMetrics.length ? prevSideMetrics[i] : 0.0f;
                        float c = i < currSideMetrics.length ? currSideMetrics[i] : 0.0f;
                        sideRates[i] = Math.abs(c - p) / timeDiffSec;
                    }
                }
            }
        }

        boolean hasImmediateActivity = itemThroughput > 0.001f || fluidThroughput > 0.001f || Math.abs(energyDelta) > 0.1f || Math.abs(kineticSpeed) > 0.001f;
        if (hasImmediateActivity) {
            history.lastActiveTimeMs = now;
            history.lastItemTp = Math.max(history.lastItemTp, itemThroughput);
            history.lastFluidTp = Math.max(history.lastFluidTp, fluidThroughput);
            if (Math.abs(energyDelta) > 0.1f) {
                history.lastEnergyDelta = energyDelta;
            }
            for (int i = 0; i < 18; i++) {
                if (sideRates[i] > 0.001f) {
                    history.lastSideRates[i] = Math.max(history.lastSideRates[i], sideRates[i]);
                }
            }
        }

        boolean isActive = (now - history.lastActiveTimeMs) < 1500L;
        if (isActive) {
            if (itemThroughput == 0f) itemThroughput = history.lastItemTp;
            if (fluidThroughput == 0f) fluidThroughput = history.lastFluidTp;
            if (energyDelta == 0f) energyDelta = history.lastEnergyDelta;
            for (int i = 0; i < 18; i++) {
                if (sideRates[i] == 0f && history.lastSideRates[i] > 0f) {
                    sideRates[i] = history.lastSideRates[i];
                }
            }
        } else {
            history.lastItemTp = 0f;
            history.lastFluidTp = 0f;
            history.lastEnergyDelta = 0f;
            Arrays.fill(history.lastSideRates, 0f);
            Arrays.fill(sideRates, 0f);
        }

        float chemicalThroughput = 0f;
        for (int i = 12; i < 18; i++) {
            chemicalThroughput += sideRates[i];
        }

        boolean hasChemicals = false;
        String chemicalName = "";
        long chemicalAmount = 0L;
        long chemicalCapacity = 0L;

        if (allowChemicals) {
            try {
                var chemOpt = MekanismChemicalInspector.inspectChemicals(level, pos, null);
                if (chemOpt.isPresent()) {
                    var chemData = chemOpt.get();
                    hasChemicals = true;
                    chemicalName = chemData.chemicalName();
                    chemicalAmount = chemData.amount();
                    chemicalCapacity = chemData.capacity();
                    hasAnyCapability = true;
                }
            } catch (Throwable ignored) {
            }
        }


        boolean hasAe2Network = false;
        float aePowerRate = 0f;
        float aePowerDrain = 0f;
        float aeSubnetDrain = 0f;
        int aeSubnetCount = 0;
        double aeStoredPower = 0.0;
        double aeMaxPower = 0.0;
        byte aeStabilityState = 0;
        boolean aeIsCreative = false;
        int aeUsedChannels = 0;
        int aeMaxChannels = 0;

        if (allowAe2) {
            try {
                var aeOpt = AE2NetworkInspector.inspectNetwork(level, pos, hitFace);
                if (aeOpt.isPresent()) {
                    var aeData = aeOpt.get();
                    hasAe2Network = true;
                    aePowerRate = aeData.powerRate();
                    aePowerDrain = aeData.powerDrain();
                    aeSubnetDrain = aeData.subnetDrain();
                    aeSubnetCount = aeData.subnetCount();
                    aeStoredPower = aeData.storedPower();
                    aeMaxPower = aeData.maxPower();
                    aeStabilityState = aeData.stabilityState();
                    aeIsCreative = aeData.isCreative();
                    aeUsedChannels = aeData.usedChannels();
                    aeMaxChannels = aeData.maxChannels();
                    hasAnyCapability = true;
                }
            } catch (Throwable ignored) {
            }
        }

        short disabledMask = 0;
        if (!allowItems) disabledMask |= TelemetryData.MASK_ITEMS;
        if (!allowFluids) disabledMask |= TelemetryData.MASK_FLUIDS;
        if (!allowEnergy) disabledMask |= TelemetryData.MASK_ENERGY;
        if (!allowKinetic) disabledMask |= TelemetryData.MASK_KINETIC;
        if (!allowChemicals) disabledMask |= TelemetryData.MASK_CHEMICALS;
        if (!allowSides) disabledMask |= TelemetryData.MASK_SIDES;
        if (!allowAe2) disabledMask |= TelemetryData.MASK_AE2;

        boolean hasItems = maxItems > 0;
        boolean hasFluids = maxFluid > 0;
        boolean hasEnergy = maxEnergy > 0;

        TelemetryData.BottleneckState state;
        if (isOverstressed) {
            state = TelemetryData.BottleneckState.OVERLOADED;
        } else if (hasAe2Network && aeStabilityState == (byte) AE2NetworkInspector.AE2StabilityState.BROWNOUT.ordinal()) {
            state = TelemetryData.BottleneckState.STARVED;
        } else if (hasKinetic && Math.abs(kineticSpeed) > 0.001f) {
            // Kinetic blocks with speed are always OPTIMAL
            state = TelemetryData.BottleneckState.OPTIMAL;
        } else if (hasKinetic && Math.abs(kineticSpeed) <= 0.001f) {
            // Kinetic block with no speed = STARVED (no power source)
            state = TelemetryData.BottleneckState.STARVED;
        } else if (isActive) {
            state = TelemetryData.BottleneckState.OPTIMAL;
        } else if (hasAe2Network) {
            // AE2 devices draw network power or convert FE; FE buffer is normally 0
            if (aeStabilityState == (byte) AE2NetworkInspector.AE2StabilityState.STABLE.ordinal()
                    || aeStabilityState == (byte) AE2NetworkInspector.AE2StabilityState.CREATIVE.ordinal()) {
                state = TelemetryData.BottleneckState.OPTIMAL;
            } else if (aeStabilityState == (byte) AE2NetworkInspector.AE2StabilityState.DISCHARGING.ordinal()
                    || aeStabilityState == (byte) AE2NetworkInspector.AE2StabilityState.BOOTING.ordinal()) {
                state = TelemetryData.BottleneckState.IDLE;
            } else {
                state = TelemetryData.BottleneckState.IDLE;
            }
        } else {
            if (hasEnergy && maxEnergy > 0 && currentEnergy == 0) {
                state = TelemetryData.BottleneckState.STARVED;
            } else {
                float itemFillRatio = maxItems > 0 ? (float) currentItems / maxItems : 0.0f;
                float fluidFillRatio = maxFluid > 0 ? (float) currentFluid / maxFluid : 0.0f;

                boolean isOutputClogged = (hasItems || hasFluids) && (itemFillRatio > 0.90f || fluidFillRatio > 0.90f);

                if (isOutputClogged) {
                    state = TelemetryData.BottleneckState.CLOGGED;
                } else {
                    state = TelemetryData.BottleneckState.IDLE;
                }
            }
        }

        long storageCurrent = 0L;
        long storageMax = 0L;
        float primaryNetRate = 0f;
        boolean hasStorage = false;

        boolean isFluidBlock = beClassName.contains("tank") || beClassName.contains("fluid") || (maxFluid > 0 && maxItems <= 128 && maxFluid > maxItems);
        boolean isEnergyBlock = beClassName.contains("energy") || beClassName.contains("battery") || beClassName.contains("capacitor") || beClassName.contains("cell") || (maxEnergy > 0 && maxItems == 0 && maxFluid == 0);

        if (isFluidBlock && maxFluid > 0) {
            storageCurrent = currentFluid;
            storageMax = maxFluid;
            primaryNetRate = fluidNetRate;
            hasStorage = true;
        } else if (isEnergyBlock && maxEnergy > 0) {
            storageCurrent = currentEnergy;
            storageMax = maxEnergy;
            primaryNetRate = energyNetRate;
            hasStorage = true;
        } else if (maxItems > 0) {
            storageCurrent = currentItems;
            storageMax = maxItems;
            primaryNetRate = itemNetRate;
            hasStorage = true;
        } else if (maxFluid > 0) {
            storageCurrent = currentFluid;
            storageMax = maxFluid;
            primaryNetRate = fluidNetRate;
            hasStorage = true;
        } else if (maxEnergy > 0) {
            storageCurrent = currentEnergy;
            storageMax = maxEnergy;
            primaryNetRate = energyNetRate;
            hasStorage = true;
        }

        boolean isInfinite = beClassName.contains("creative")
                || (storageMax == Integer.MAX_VALUE)
                || (hasAe2Network && aeIsCreative);

        boolean isDraining = primaryNetRate < -0.001f;

        byte logisticsFlags = 0;
        if (hasStorage) {
            logisticsFlags |= TelemetryData.FLAG_LOGISTICS_HAS_STORAGE;
        }
        if (isInfinite) {
            logisticsFlags |= TelemetryData.FLAG_LOGISTICS_IS_INFINITE;
        }
        if (isDraining) {
            logisticsFlags |= TelemetryData.FLAG_LOGISTICS_IS_DRAINING;
        }

        return new TelemetryData(
                itemThroughput, hasItems,
                fluidThroughput, hasFluids,
                energyDelta, hasEnergy,
                chemicalThroughput, hasChemicals, chemicalName, chemicalAmount, chemicalCapacity,
                kineticSpeed, kineticStress, kineticCapacity, kineticBlockStress, hasKinetic, kineticDirection,
                state, allSlotCounts, allTankAmounts, sideRates,
                disabledMask,
                hasAe2Network, aePowerRate, aePowerDrain, aeSubnetDrain, aeSubnetCount, aeStoredPower, aeMaxPower, aeStabilityState, aeIsCreative, aeUsedChannels, aeMaxChannels,
                storageCurrent, storageMax, primaryNetRate, logisticsFlags
        );
    }




    private static void cleanupStaleEntries(long now) {
        CACHE.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMs > CLEANUP_EXPIRE_MS);
    }
}

