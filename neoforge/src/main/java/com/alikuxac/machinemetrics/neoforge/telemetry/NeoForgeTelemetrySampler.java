package com.alikuxac.machinemetrics.neoforge.telemetry;

import com.alikuxac.machinemetrics.compat.CompatRegistry;
import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector;
import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector.AE2StabilityState;
import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.compat.mekanism.MekanismChemicalInspector;
import com.alikuxac.machinemetrics.compat.mekanism.MekanismTransporterInspector;
import com.alikuxac.machinemetrics.compat.storage.DrawerStorageAdapter;
import com.alikuxac.machinemetrics.compat.storage.SophisticatedStorageAdapter;
import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.storage.BlockCapacityReader;
import com.alikuxac.machinemetrics.storage.BlockCapacityReader.StorageCapacity;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
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

    private static final String WAYSTONE_NAMESPACE = "waystones";

    private record Sample(long timestampMs, int[] itemSlots, int[] fluidTanks, long energyAmount, int totalItems, long totalFluid,
    float[] sideMetrics) {}

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

        boolean isAe2Block = CompatRegistry.isAe2Block(level, pos);
        boolean isAe2ConnectionBlock = isAe2Block
                && (CompatRegistry.blockPathContains(level, pos, "cable")
                        || CompatRegistry.blockPathContains(level, pos, "multipart")
                        || CompatRegistry.blockPathContains(level, pos, "bus"));
        if ((isAe2ConnectionBlock && !AE2NetworkInspector.isAE2Loaded())
                || CompatRegistry.blockIsInNamespace(level, pos, WAYSTONE_NAMESPACE)) {
            return TelemetryData.empty();
        }

        SampleHistory existingHistory = CACHE.get(pos);

        int currentItems = 0;
        int maxItems = 0;
        long currentFluid = 0L;
        long maxFluid = 0L;
        long currentEnergy = 0L;
        long maxEnergy = 0L;

        boolean hasAnyCapability = false;

        float[] sideMetrics = new float[18];
        IntArrayList allSlotCounts = new IntArrayList();
        Set<ResourceHandler<ItemResource>> processedItemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        var transporterOpt = MekanismTransporterInspector.inspect(be);
        boolean isTransporter = transporterOpt.isPresent() && transporterOpt.get().isTransporter();

        if (isTransporter) {
            var tData = transporterOpt.get();
            hasAnyCapability = true;
            currentItems = tData.currentItems();
            maxItems = tData.maxCapacity();
            for (int count : tData.slotCounts()) {
                allSlotCounts.add(count);
            }
            float[] sideCounts = tData.sideItemCounts();
            for (int i = 0; i < 6; i++) {
                sideMetrics[i] = sideCounts[i];
            }
        } else {
            for (Direction side : Direction.values()) {
                    try {
                        ResourceHandler<ItemResource> itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
                        if (itemHandler != null && itemHandler.size() > 0) {
                            int sideCount = 0;
                            for (int i = 0; i < itemHandler.size(); i++) {
                                var stack = itemHandler.getResource(i).toStack();
                                if (!stack.isEmpty()) {
                                    sideCount += stack.getCount();
                                }
                            }
                            sideMetrics[side.ordinal()] = (float) sideCount;
                        }
                    } catch (Throwable ignored) {
                    }
                }

                ResourceHandler<ItemResource> itemHandler = resolveItemHandler(level, pos, hitFace);

                if (itemHandler != null && itemHandler.size() > 0) {
                    hasAnyCapability = true;
                    processedItemHandlers.add(itemHandler);
                    int slotCount = itemHandler.size();
                    boolean useCachedLimit = existingHistory != null && existingHistory.cachedItemSlotCount == slotCount && existingHistory.cachedMaxItemCapacity > 0;
                    int handlerMaxCap = 0;

                    if (useCachedLimit) {
                        handlerMaxCap = existingHistory.cachedMaxItemCapacity;
                        for (int i = 0; i < slotCount; i++) {
                            var stack = itemHandler.getResource(i).toStack();
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
                            handlerMaxCap += itemHandler.getCapacityAsLong(i, itemHandler.getResource(i));
                            var stack = itemHandler.getResource(i).toStack();
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
        Set<ResourceHandler<FluidResource>> processedFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        String fluidName = "";

        for (Direction side : Direction.values()) {
            try {
                ResourceHandler<FluidResource> fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
                if (fluidHandler != null && fluidHandler.size() > 0) {
                    float sideAmount = 0.0f;
                    for (int i = 0; i < fluidHandler.size(); i++) {
                        sideAmount += fluidHandler.getAmountAsLong(i);
                    }
                    sideMetrics[6 + side.ordinal()] = sideAmount;
                }
            } catch (Throwable ignored) {
            }
        }

        ResourceHandler<FluidResource> fluidHandler = resolveFluidHandler(level, pos, hitFace);

        if (fluidHandler != null && fluidHandler.size() > 0) {
            hasAnyCapability = true;
            processedFluidHandlers.add(fluidHandler);
            for (int i = 0; i < fluidHandler.size(); i++) {
                var fluidResource = fluidHandler.getResource(i);
                long tankCap = fluidHandler.getCapacityAsLong(i, fluidResource);
                if (!fluidResource.isEmpty()) {
                    long amount = fluidHandler.getAmountAsLong(i);
                    currentFluid += amount;
                    allTankAmounts.add((int) Math.min(amount, Integer.MAX_VALUE));
                    if (fluidName.isEmpty()) {
                        fluidName = fluidResource.toStack(1).getHoverName().getString();
                    }
                } else {
                    allTankAmounts.add(0);
                }
                maxFluid += tankCap;
            }
        }
        int[] fluidTanks = allTankAmounts.toIntArray();

        EnergyHandler energyStorage = resolveEnergyStorage(level, pos, hitFace);
        if (energyStorage != null) {
            long maxCap = energyStorage.getCapacityAsLong();
            if (maxCap > 0) {
                hasAnyCapability = true;
                currentEnergy += energyStorage.getAmountAsLong();
                maxEnergy += maxCap;
            }
        }

        boolean hasChemicals = false;
        String chemicalName = "";
        long chemicalAmount = 0L;
        long chemicalCapacity = 0L;
        if (MekanismChemicalInspector.isMekanismLoaded()
                && ServerConfig.ALLOW_CHEMICALS != null && ServerConfig.ALLOW_CHEMICALS.get()) {
            try {
                var chemOpt = MekanismChemicalInspector.inspectChemicals(level, pos, hitFace);
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


        float kineticSpeed = 0f;
        float kineticStress = 0f;
        float kineticCapacity = 0f;
        float kineticBlockStress = 0f;
        boolean isOverstressed = false;
        boolean hasKinetic = false;
        CreateKineticInspector.RotationDirection kineticDirection = CreateKineticInspector.RotationDirection.STATIONARY;

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

        boolean isAe2Storage = AE2NetworkInspector.isAE2Loaded() && isAe2Block;
        boolean hasStorageCapacity = BlockCapacityReader.readCapacity(level, pos, hitFace).hasStorage()
                || CompatRegistry.isDrawerBlock(level, pos)
                || CompatRegistry.isSophisticatedStorageBlock(level, pos);

        boolean hasChemicalStorage = hasChemicals && chemicalCapacity > 0;

        if (!hasAnyCapability && !hasKinetic && !hasAe2Network && !isAe2Storage && !hasStorageCapacity && !hasChemicalStorage) {
            return TelemetryData.empty();
        }
        if ((maxItems == 0 && maxFluid == 0 && maxEnergy == 0 && !hasKinetic && !hasAe2Network && !isAe2Storage && !hasStorageCapacity && !hasChemicalStorage)) {
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
        float chemicalThroughput = 0f;

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

                if (isTransporter) {
                    float transporterRate = transporterOpt.get().throughputRate();
                    if (transporterRate > 0.001f) {
                        itemThroughput = Math.max(itemThroughput, transporterRate);
                    }
                }

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

        if (isTransporter && itemThroughput <= 0.001f) {
            itemThroughput = transporterOpt.get().throughputRate();
        }

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



        boolean isFluidStorage = maxFluid > 0
                && (CompatRegistry.isFluidTankBlock(level, pos) || maxFluid >= maxItems);

        boolean isChemicalStorage = hasChemicals && chemicalCapacity > 0
                && maxFluid == 0 && maxEnergy == 0;

        boolean suppressItems = (isFluidStorage || isChemicalStorage) && itemThroughput <= 0.001f;

        boolean hasItems = maxItems > 0 && !suppressItems;
        boolean hasFluids = maxFluid > 0;
        boolean hasEnergy = maxEnergy > 0;

        TelemetryData.BottleneckState state;
        if (isOverstressed) {
            state = TelemetryData.BottleneckState.OVERLOADED;
        } else if (hasAe2Network && aeStabilityState == (byte) AE2StabilityState.BROWNOUT.ordinal()) {
            state = TelemetryData.BottleneckState.STARVED;
        } else if (hasKinetic && Math.abs(kineticSpeed) > 0.001f) {
            state = TelemetryData.BottleneckState.OPTIMAL;
        } else if (hasKinetic && Math.abs(kineticSpeed) <= 0.001f) {
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

        if (addedNewSample && ServerConfig.DEBUG_LOGGING.get()) {
            LOGGER.info("[TelemetryDebug] Block: {} | Slots: {} | Tanks: {} | itemTp: {}/s | fluidTp: {} mB/s | energyDelta: {} FE/t | state: {}",
                    CompatRegistry.friendlyNameOf(level, pos), itemSlots.length, Arrays.toString(fluidTanks), itemThroughput, fluidThroughput, energyDelta, state);
        }

        long storageCurrent = 0L;
        long storageMax = 0L;
        float primaryNetRate = 0f;
        boolean hasStorage = false;
        boolean isInfiniteStorage = false;

        if (CompatRegistry.isDrawerBlock(level, pos)) {
            try {
                var drawerOpt = DrawerStorageAdapter.inspectDrawer(level, pos, hitFace);
                if (drawerOpt.isPresent() && !drawerOpt.get().isEmpty()) {
                    var drawer = drawerOpt.get();
                    storageCurrent = drawer.storedItems();
                    storageMax = drawer.maxCapacity();
                    primaryNetRate = itemNetRate;
                    hasStorage = true;
                    if (drawer.isInfinite()) {
                        isInfiniteStorage = true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (!hasStorage && CompatRegistry.isSophisticatedStorageBlock(level, pos)) {
            try {
                var ssOpt = SophisticatedStorageAdapter.inspectStorage(level, pos, hitFace);
                if (ssOpt.isPresent() && !ssOpt.get().isEmpty()) {
                    var ss = ssOpt.get();
                    storageCurrent = ss.storedItems();
                    storageMax = ss.maxCapacity();
                    primaryNetRate = itemNetRate;
                    hasStorage = true;
                    if (ss.isInfinite()) {
                        isInfiniteStorage = true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            var aeCellOpt = AE2NetworkInspector.inspectCellStorage(level, pos);
            if (aeCellOpt.isPresent() && aeCellOpt.get().isCellStorage()) {
                var aeCell = aeCellOpt.get();
                storageCurrent = aeCell.usedBytes();
                storageMax = aeCell.totalBytes();
                primaryNetRate = itemNetRate;
                hasStorage = true;
                if (aeCell.isInfinite()) {
                    isInfiniteStorage = true;
                }
            }
        } catch (Throwable ignored) {
        }

        if (!hasStorage && isChemicalStorage) {
            storageCurrent = chemicalAmount;
            storageMax = chemicalCapacity;
            primaryNetRate = 0f;
            hasStorage = true;
            if (chemicalCapacity >= Integer.MAX_VALUE) {
                isInfiniteStorage = true;
            }
        }

        if (!hasStorage && isFluidStorage && maxFluid > 0) {
            storageCurrent = currentFluid;
            storageMax = maxFluid;
            primaryNetRate = fluidNetRate;
            hasStorage = true;
            if (maxFluid >= Integer.MAX_VALUE) {
                isInfiniteStorage = true;
            }
        }

        if (!hasStorage) {
            StorageCapacity cap = BlockCapacityReader.readCapacity(level, pos, hitFace);
            if (cap.hasStorage()) {
                storageCurrent = cap.current();
                storageMax = cap.max();
                hasStorage = true;
                if (cap.isInfinite()) {
                    isInfiniteStorage = true;
                }
                primaryNetRate = switch (cap.type()) {
                    case FLUID -> fluidNetRate;
                    case ENERGY -> energyNetRate;
                    default -> isTransporter ? 0f : itemNetRate;
                };
            }
        }

        boolean isInfinite = isInfiniteStorage
                || (storageMax == Integer.MAX_VALUE)
                || (storageMax >= (Long.MAX_VALUE / 2))
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
                fluidThroughput, hasFluids, fluidName, currentFluid, maxFluid,
                energyDelta, hasEnergy,
                chemicalThroughput, hasChemicals, chemicalName, chemicalAmount, chemicalCapacity,
                kineticSpeed, kineticStress, kineticCapacity, kineticBlockStress, hasKinetic, kineticDirection,
                state, allSlotCounts, allTankAmounts, sideRates,
                (short) 0,
                hasAe2Network, aePowerRate, aePowerDrain, aeSubnetDrain, aeSubnetCount, aeStoredPower, aeMaxPower, aeStabilityState, aeIsCreative, aeUsedChannels, aeMaxChannels,
                storageCurrent, storageMax, primaryNetRate, logisticsFlags
        );
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

    private static void cleanupStaleEntries(long now) {
        CACHE.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMs > CLEANUP_EXPIRE_MS);
    }
}
