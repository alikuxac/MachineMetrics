package com.alikuxac.machinemetrics.forge.telemetry;

import com.alikuxac.machinemetrics.compat.CompatRegistry;
import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector;
import com.alikuxac.machinemetrics.compat.storage.DrawerStorageAdapter;
import com.alikuxac.machinemetrics.compat.storage.SophisticatedStorageAdapter;
import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.storage.BlockCapacityReader;
import com.alikuxac.machinemetrics.storage.BlockCapacityReader.StorageCapacity;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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

    private static final String WAYSTONE_NAMESPACE = "waystones";

    private static final Set<String> COMPAT_NAMESPACES = Set.of(
            CompatRegistry.NAMESPACE_AE2,
            CompatRegistry.NAMESPACE_MEKANISM,
            CompatRegistry.NAMESPACE_CREATE,
            CompatRegistry.NAMESPACE_FUNCTIONAL_STORAGE,
            CompatRegistry.NAMESPACE_STORAGE_DRAWERS,
            CompatRegistry.NAMESPACE_SOPHISTICATED_STORAGE,
            CompatRegistry.NAMESPACE_SOPHISTICATED_CORE
    );

    private static boolean isCompatModBlock(Level level, BlockPos pos) {
        return COMPAT_NAMESPACES.contains(CompatRegistry.namespaceOf(level, pos));
    }

    private static void markInert(Level level, BlockPos pos, long now) {
        if (!isCompatModBlock(level, pos)) {
            INERT_BLOCK_CACHE.put(pos, now);
        }
    }

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
            if (now - inertTimestamp < INERT_EXPIRE_MS && !isCompatModBlock(level, pos)) {
                return TelemetryData.empty();
            } else {
                INERT_BLOCK_CACHE.remove(pos);
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            markInert(level, pos, now);
            return TelemetryData.empty();
        }

        boolean allowAe2 = ServerConfig.ALLOW_AE2 == null || ServerConfig.ALLOW_AE2.get();
        boolean isAe2Block = CompatRegistry.isAe2Block(level, pos);
        boolean isAe2ConnectionBlock = isAe2Block
                && (CompatRegistry.blockPathContains(level, pos, "cable")
                        || CompatRegistry.blockPathContains(level, pos, "multipart")
                        || CompatRegistry.blockPathContains(level, pos, "bus"));

        if ((isAe2ConnectionBlock && !allowAe2)
                || CompatRegistry.blockIsInNamespace(level, pos, WAYSTONE_NAMESPACE)) {
            markInert(level, pos, now);
            return TelemetryData.empty();
        }

        SampleHistory existingHistory = CACHE.get(pos);

        boolean allowItems = ServerConfig.ALLOW_ITEMS == null || ServerConfig.ALLOW_ITEMS.get();
        boolean allowFluids = ServerConfig.ALLOW_FLUIDS == null || ServerConfig.ALLOW_FLUIDS.get();
        boolean allowEnergy = ServerConfig.ALLOW_ENERGY == null || ServerConfig.ALLOW_ENERGY.get();
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
            if (allowSides) {
                for (Direction side : Direction.values()) {
                    try {
                        IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                        if (itemHandler != null && itemHandler.getSlots() > 0) {
                            int sideCount = 0;
                            for (int i = 0; i < itemHandler.getSlots(); i++) {
                                var stack = itemHandler.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    sideCount += stack.getCount();
                                }
                            }
                            sideMetrics[side.ordinal()] = (float) sideCount;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            IItemHandler itemHandler = resolveItemHandler(level, pos, hitFace);

            if (itemHandler != null && itemHandler.getSlots() > 0) {
                hasAnyCapability = true;
                processedItemHandlers.add(itemHandler);
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

        String fluidName = "";
        IntArrayList allTankAmounts = new IntArrayList();
        Set<IFluidHandler> processedFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        if (allowFluids) {
            if (allowSides) {
                for (Direction side : Direction.values()) {
                    try {
                        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
                        if (fluidHandler != null && fluidHandler.getTanks() > 0) {
                            int sideFluidAmount = 0;
                            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                                var stack = fluidHandler.getFluidInTank(i);
                                if (!stack.isEmpty()) {
                                    sideFluidAmount += stack.getAmount();
                                }
                            }
                            sideMetrics[6 + side.ordinal()] = (float) sideFluidAmount;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            IFluidHandler fluidHandler = resolveFluidHandler(level, pos, hitFace);

            if (fluidHandler != null && fluidHandler.getTanks() > 0) {
                hasAnyCapability = true;
                processedFluidHandlers.add(fluidHandler);
                for (int i = 0; i < fluidHandler.getTanks(); i++) {
                    var fluidStack = fluidHandler.getFluidInTank(i);
                    int tankCap = fluidHandler.getTankCapacity(i);
                    if (!fluidStack.isEmpty()) {
                        currentFluid += fluidStack.getAmount();
                        allTankAmounts.add(fluidStack.getAmount());
                        if (fluidName.isEmpty()) {
                            fluidName = fluidStack.getHoverName().getString();
                        }
                    } else {
                        allTankAmounts.add(0);
                    }
                    maxFluid += tankCap;
                }
            }
        }
        int[] fluidTanks = allTankAmounts.toIntArray();

        if (allowEnergy) {
            IEnergyStorage energyStorage = resolveEnergyStorage(level, pos, hitFace);
            if (energyStorage != null) {
                int maxCap = energyStorage.getMaxEnergyStored();
                if (maxCap > 0) {
                    hasAnyCapability = true;
                    currentEnergy += energyStorage.getEnergyStored();
                    maxEnergy += maxCap;
                }
            }
        }

        boolean isAe2Storage = allowAe2 && isAe2Block;
        boolean hasStorageCapacity = BlockCapacityReader.readCapacity(level, pos, hitFace).hasStorage()
                || CompatRegistry.isDrawerBlock(level, pos)
                || CompatRegistry.isSophisticatedStorageBlock(level, pos);

        if (!hasAnyCapability && !isAe2Storage && !hasStorageCapacity) {
            markInert(level, pos, now);
            return TelemetryData.empty();
        }
        if ((maxItems == 0 && maxFluid == 0 && maxEnergy == 0 && !isAe2Storage && !hasStorageCapacity)) {
            markInert(level, pos, now);
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

        boolean suppressItems = isFluidStorage && itemThroughput <= 0.001f;

        boolean hasItems = maxItems > 0 && !suppressItems;
        boolean hasFluids = maxFluid > 0;
        boolean hasEnergy = maxEnergy > 0;

        TelemetryData.BottleneckState state;
        if (isActive) {
            state = TelemetryData.BottleneckState.OPTIMAL;
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

        if (allowAe2) {
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
                    default -> itemNetRate;
                };
            }
        }

        boolean isInfinite = isInfiniteStorage
                || (storageMax == Integer.MAX_VALUE)
                || (storageMax >= (Long.MAX_VALUE / 2));

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
                0f, false, "", 0L, 0L,
                0f, 0f, 0f, 0f, false, null,
                state, allSlotCounts, allTankAmounts, sideRates,
                (short) 0,
                false, 0f, 0f, 0f, 0, 0.0, 0.0, (byte) 0, false, 0, 0,
                storageCurrent, storageMax, primaryNetRate, logisticsFlags
        );
    }

    private static IItemHandler resolveItemHandler(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            IItemHandler h = safeItem(level, pos, hitFace);
            if (h != null && h.getSlots() > 0) return h;
        }
        IItemHandler h = safeItem(level, pos, null);
        if (h != null && h.getSlots() > 0) return h;
        for (Direction side : Direction.values()) {
            h = safeItem(level, pos, side);
            if (h != null && h.getSlots() > 0) return h;
        }
        return null;
    }

    private static IItemHandler safeItem(Level level, BlockPos pos, Direction side) {
        try {
            return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IFluidHandler resolveFluidHandler(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            IFluidHandler h = safeFluid(level, pos, hitFace);
            if (h != null && h.getTanks() > 0) return h;
        }
        IFluidHandler h = safeFluid(level, pos, null);
        if (h != null && h.getTanks() > 0) return h;
        for (Direction side : Direction.values()) {
            h = safeFluid(level, pos, side);
            if (h != null && h.getTanks() > 0) return h;
        }
        return null;
    }

    private static IFluidHandler safeFluid(Level level, BlockPos pos, Direction side) {
        try {
            return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IEnergyStorage resolveEnergyStorage(Level level, BlockPos pos, Direction hitFace) {
        if (hitFace != null) {
            IEnergyStorage s = safeEnergy(level, pos, hitFace);
            if (s != null && s.getMaxEnergyStored() > 0) return s;
        }
        IEnergyStorage s = safeEnergy(level, pos, null);
        if (s != null && s.getMaxEnergyStored() > 0) return s;
        for (Direction side : Direction.values()) {
            s = safeEnergy(level, pos, side);
            if (s != null && s.getMaxEnergyStored() > 0) return s;
        }
        return null;
    }

    private static IEnergyStorage safeEnergy(Level level, BlockPos pos, Direction side) {
        try {
            return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void cleanupStaleEntries(long now) {
        CACHE.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMs > CLEANUP_EXPIRE_MS);
    }
}

