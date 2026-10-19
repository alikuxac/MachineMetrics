package com.alikuxac.machinemetrics.forge.telemetry;

import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector;
import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector.AE2StabilityState;
import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class NeoForgeTelemetrySampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeTelemetrySampler.class);
    private static final Map<BlockPos, SampleHistory> CACHE = new ConcurrentHashMap<>();
    private static final long CLEANUP_EXPIRE_MS = 10000L;

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

    public static TelemetryData sampleTargetBlock(BlockPos pos) {
        return sampleTargetBlock(pos, null);
    }

    public static TelemetryData sampleTargetBlock(BlockPos pos, Direction hitFace) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || pos == null) {
            return TelemetryData.empty();
        }

        long now = System.currentTimeMillis();
        cleanupStaleEntries(now);

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return TelemetryData.empty();
        }

        String beClassName = be.getClass().getName().toLowerCase();
        boolean isAe2Block = beClassName.contains("appeng");
        if ((isAe2Block && !AE2NetworkInspector.isAE2Loaded() && (beClassName.contains("cable") || beClassName.contains("multipart") || beClassName.contains("bus")))
                || beClassName.contains("waystone")) {
            return TelemetryData.empty();
        }

        SampleHistory existingHistory = CACHE.get(pos);

        int currentItems = 0;
        int maxItems = 0;
        int currentFluid = 0;
        int maxFluid = 0;
        int currentEnergy = 0;
        int maxEnergy = 0;

        boolean hasAnyCapability = false;

        Direction[] sidesToQuery = new Direction[] { null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
        float[] sideMetrics = new float[18];
        IntArrayList allSlotCounts = new IntArrayList();
        Set<IItemHandler> processedItemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Direction side : sidesToQuery) {
            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            if (itemHandler == null || itemHandler.getSlots() <= 0) {
                continue;
            }

            hasAnyCapability = true;

            // 1. Calculate side-specific metrics (0..5: Item)
            if (side != null) {
                float sideItemCount = 0.0f;
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    var stack = itemHandler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        sideItemCount += stack.getCount();
                    }
                }
                // Assign to side slot: DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
                sideMetrics[side.ordinal()] = sideItemCount;
            }

            // 2. Calculate global capacity - deduplicate handlers
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
        int[] itemSlots = allSlotCounts.toIntArray();

        IntArrayList allTankAmounts = new IntArrayList();
        Set<IFluidHandler> processedFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Direction side : sidesToQuery) {
            IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
            if (fluidHandler == null || fluidHandler.getTanks() <= 0) {
                continue;
            }

            hasAnyCapability = true;

            if (side != null) {
                float sideAmount = 0.0f;
                for (int i = 0; i < fluidHandler.getTanks(); i++) {
                    sideAmount += fluidHandler.getFluidInTank(i).getAmount();
                }
                // Assign to side fluid slot in 12-element array (offset + 6)
                sideMetrics[6 + side.ordinal()] = sideAmount;
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
        int[] fluidTanks = allTankAmounts.toIntArray();

        Set<IEnergyStorage> processedEnergyStorages = Collections.newSetFromMap(new IdentityHashMap<>());

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

        if (!hasAnyCapability || (maxItems == 0 && maxFluid == 0 && maxEnergy == 0 && !hasAe2Network)) {
            return TelemetryData.empty();
        }

        Sample currentSample = new Sample(now, itemSlots, fluidTanks, currentEnergy, currentItems, currentFluid, sideMetrics);
        SampleHistory history = CACHE.computeIfAbsent(pos, p -> new SampleHistory(currentSample));

        boolean addedNewSample = false;
        if (now - history.lastSample.timestampMs() >= 100L) {
            history.addSample(currentSample);
            addedNewSample = true;
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

                // Compute Side-by-side throughput (delta / time)
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

        // Active Burst Detection & Hold Time Window (1.5 seconds)
        boolean hasImmediateActivity = itemThroughput > 0.001f || fluidThroughput > 0.001f || Math.abs(energyDelta) > 0.1f;
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

        boolean hasItems = maxItems > 0;
        boolean hasFluids = maxFluid > 0;
        boolean hasEnergy = maxEnergy > 0;

        TelemetryData.BottleneckState state;
        if (hasAe2Network && aeStabilityState == (byte) AE2StabilityState.BROWNOUT.ordinal()) {
            state = TelemetryData.BottleneckState.STARVED;
        } else if (isActive) {
            state = TelemetryData.BottleneckState.OPTIMAL;
        } else if (hasAe2Network) {
            if (aeStabilityState == (byte) AE2StabilityState.STABLE.ordinal()
                    || aeStabilityState == (byte) AE2StabilityState.CREATIVE.ordinal()) {
                state = TelemetryData.BottleneckState.OPTIMAL;
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

        if (addedNewSample && ServerConfig.DEBUG_LOGGING.get()) {
            LOGGER.info("[TelemetryDebug] Block: {} | Slots: {} | Tanks: {} | itemTp: {}/s | fluidTp: {} mB/s | energyDelta: {} FE/t | state: {}",
                    be.getClass().getSimpleName(), itemSlots.length, Arrays.toString(fluidTanks), itemThroughput, fluidThroughput, energyDelta, state);
        }

        return new TelemetryData(
                itemThroughput, hasItems,
                fluidThroughput, hasFluids,
                energyDelta, hasEnergy,
                0f, false, "", 0L, 0L,
                0f, 0f, 0f, 0f, false, null,
                state, allSlotCounts, allTankAmounts, sideRates,
                (short) 0,
                hasAe2Network, aePowerRate, aePowerDrain, aeSubnetDrain, aeSubnetCount, aeStoredPower, aeMaxPower, aeStabilityState, aeIsCreative, aeUsedChannels, aeMaxChannels,
                storageCurrent, storageMax, primaryNetRate, logisticsFlags
        );
    }

    private static void cleanupStaleEntries(long now) {
        CACHE.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMs > CLEANUP_EXPIRE_MS);
    }
}
