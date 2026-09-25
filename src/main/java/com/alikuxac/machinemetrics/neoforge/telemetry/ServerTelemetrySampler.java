package com.alikuxac.machinemetrics.neoforge.telemetry;

import com.alikuxac.machinemetrics.telemetry.TelemetryData;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerTelemetrySampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTelemetrySampler.class);
    private static final Map<BlockPos, SampleHistory> CACHE = new ConcurrentHashMap<>();
    private static final long CLEANUP_EXPIRE_MS = 10000L;

    private record Sample(long timestampMs, int[] itemSlots, int[] fluidTanks, int energyAmount, int totalItems, int totalFluid) {}

    private static class SampleHistory {
        private Sample previousSample;
        private Sample lastSample;
        private long lastAccessMs;
        private long lastActiveTimeMs;
        private float lastItemTp;
        private float lastFluidTp;
        private float lastEnergyDelta;

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
        if ((beClassName.contains("appeng") && (beClassName.contains("cable") || beClassName.contains("multipart") || beClassName.contains("bus")))
                || beClassName.contains("drawer") || beClassName.contains("chest") || beClassName.contains("barrel")
                || beClassName.contains("storage") || beClassName.contains("shulker") || beClassName.contains("vault")
                || beClassName.contains("bin") || beClassName.contains("crate")) {
            return TelemetryData.empty();
        }

        int currentItems = 0;
        int maxItems = 0;
        int currentFluid = 0;
        int maxFluid = 0;
        int currentEnergy = 0;
        int maxEnergy = 0;

        boolean hasAnyCapability = false;

        Direction[] sidesToQuery = new Direction[] { null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

        List<Integer> allSlotCounts = new ArrayList<>();
        Set<IItemHandler> processedItemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Direction side : sidesToQuery) {
            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            if (itemHandler != null && itemHandler.getSlots() > 0 && processedItemHandlers.add(itemHandler)) {
                hasAnyCapability = true;
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    var stack = itemHandler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        currentItems += stack.getCount();
                        maxItems += stack.getMaxStackSize();
                        allSlotCounts.add(stack.getCount());
                    } else {
                        maxItems += 64;
                        allSlotCounts.add(0);
                    }
                }
            }
        }
        int[] itemSlots = allSlotCounts.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> allTankAmounts = new ArrayList<>();
        Set<IFluidHandler> processedFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Direction side : sidesToQuery) {
            IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
            if (fluidHandler != null && fluidHandler.getTanks() > 0 && processedFluidHandlers.add(fluidHandler)) {
                hasAnyCapability = true;
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
        int[] fluidTanks = allTankAmounts.stream().mapToInt(Integer::intValue).toArray();

        Set<IEnergyStorage> processedEnergyStorages = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Direction side : sidesToQuery) {
            IEnergyStorage energyStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
            if (energyStorage != null && energyStorage.getMaxEnergyStored() > 0 && processedEnergyStorages.add(energyStorage)) {
                hasAnyCapability = true;
                currentEnergy += energyStorage.getEnergyStored();
                maxEnergy += energyStorage.getMaxEnergyStored();
            }
        }

        if (!hasAnyCapability || (maxItems == 0 && maxFluid == 0 && maxEnergy == 0)) {
            return TelemetryData.empty();
        }

        Sample currentSample = new Sample(now, itemSlots, fluidTanks, currentEnergy, currentItems, currentFluid);
        SampleHistory history = CACHE.computeIfAbsent(pos, p -> new SampleHistory(currentSample));

        if (now - history.lastSample.timestampMs() >= 100L) {
            history.addSample(currentSample);
        }

        float itemThroughput = 0f;
        float fluidThroughput = 0f;
        float energyDelta = 0f;

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

                float timeDiffTicks = timeDiffSec * 20f;
                energyDelta = (history.lastSample.energyAmount() - history.previousSample.energyAmount()) / timeDiffTicks;
            }
        }

        // Active Burst Detection & Hold Time Window (1.5 seconds)
        boolean hasImmediateActivity = itemThroughput > 0f || fluidThroughput > 0f || Math.abs(energyDelta) > 0.1f;
        if (hasImmediateActivity) {
            history.lastActiveTimeMs = now;
            history.lastItemTp = Math.max(history.lastItemTp, itemThroughput);
            history.lastFluidTp = Math.max(history.lastFluidTp, fluidThroughput);
            if (Math.abs(energyDelta) > 0.1f) {
                history.lastEnergyDelta = energyDelta;
            }
        }

        boolean isActive = (now - history.lastActiveTimeMs) < 1500L;
        if (isActive) {
            if (itemThroughput == 0f) itemThroughput = history.lastItemTp;
            if (fluidThroughput == 0f) fluidThroughput = history.lastFluidTp;
            if (energyDelta == 0f) energyDelta = history.lastEnergyDelta;
        } else {
            history.lastItemTp = 0f;
            history.lastFluidTp = 0f;
            history.lastEnergyDelta = 0f;
        }

        boolean hasItems = maxItems > 0;
        boolean hasFluids = maxFluid > 0;
        boolean hasEnergy = maxEnergy > 0;

        TelemetryData.BottleneckState state = isActive ? TelemetryData.BottleneckState.OPTIMAL : TelemetryData.BottleneckState.IDLE;

        return new TelemetryData(itemThroughput, hasItems, fluidThroughput, hasFluids, energyDelta, hasEnergy, state);
    }

    private static void cleanupStaleEntries(long now) {
        CACHE.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMs > CLEANUP_EXPIRE_MS);
    }
}
