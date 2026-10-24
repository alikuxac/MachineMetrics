package com.alikuxac.machinemetrics.compat.ae2;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.pathing.IPathingService;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.StorageCell;
import com.alikuxac.machinemetrics.compat.Reflect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class AE2NetworkInspector {

    public enum AE2StabilityState {
        STABLE,
        DISCHARGING,
        BROWNOUT,
        BOOTING,
        CREATIVE;

        public static AE2StabilityState fromOrdinal(byte ord) {
            AE2StabilityState[] values = values();
            if (ord >= 0 && ord < values.length) {
                return values[ord];
            }
            return STABLE;
        }
    }

    public record AE2MetricData(
            boolean hasNetwork,
            float powerRate,
            float powerDrain,
            float subnetDrain,
            int subnetCount,
            double storedPower,
            double maxPower,
            byte stabilityState,
            boolean isCreative,
            int usedChannels,
            int maxChannels
    ) {
        public static final AE2MetricData EMPTY = new AE2MetricData(false, 0f, 0f, 0f, 0, 0.0, 0.0, (byte) 0, false, 0, 0);

        public boolean isEmpty() {
            return !hasNetwork;
        }
    }

    public record SubnetDrainResult(float totalSubnetDrain, int subnetCount) {}

    public record AE2CellStorageData(
            boolean isCellStorage,
            long usedBytes,
            long totalBytes,
            boolean isInfinite
    ) {
        public static final AE2CellStorageData EMPTY = new AE2CellStorageData(false, 0L, 0L, false);

        public boolean isEmpty() {
            return !isCellStorage;
        }
    }

    private static Boolean ae2LoadedCache = null;

    public static boolean isAE2Loaded() {
        if (ae2LoadedCache == null) {
            try {
                Class.forName("appeng.api.networking.IGridNode", false, AE2NetworkInspector.class.getClassLoader());
                ae2LoadedCache = true;
            } catch (Throwable ignored) {
                ae2LoadedCache = false;
            }
        }
        return ae2LoadedCache;
    }

    /**
     * Safely reads the player's active power unit selection from AE2's client config/GUI.
     * Returns "FE", "AE", or null if unavailable / not on client.
     */
    public static String getClientSelectedPowerUnit() {
        if (!isAE2Loaded()) {
            return null;
        }
        try {
            return Ae2Worker.getClientSelectedPowerUnit();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Optional<AE2MetricData> inspectNetwork(Level level, BlockPos pos, Direction side) {
        if (level == null || pos == null || !isAE2Loaded()) {
            return Optional.empty();
        }
        try {
            return Ae2Worker.inspectNetwork(level, pos, side);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public static Optional<AE2CellStorageData> inspectCellStorage(Level level, BlockPos pos) {
        if (level == null || pos == null || !isAE2Loaded()) {
            return Optional.empty();
        }
        try {
            return Ae2Worker.inspectCellStorage(level, pos);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static final class Ae2Worker {

        private static String getClientSelectedPowerUnit() {
            try {
                Class<?> aeConfigClass = Class.forName("appeng.core.AEConfig");
                Object instance = aeConfigClass.getMethod("instance").invoke(null);
                if (instance != null) {
                    Object unit = aeConfigClass.getMethod("getSelectedEnergyUnit").invoke(instance);
                    if (unit != null) {
                        return unit.toString();
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        /**
         * Traverses subnets connected across cross-grid connections (e.g. Quartz Fiber)
         * using a bounded BFS to prevent circular graph traversal and excessive work.
         */
        private static SubnetDrainResult calculateSubnetDrain(IGrid rootGrid, int maxDepth) {
            if (rootGrid == null) {
                return new SubnetDrainResult(0f, 0);
            }

            try {
                Set<IGrid> visited = new HashSet<>();
                visited.add(rootGrid);

                Queue<IGrid> queue = new ArrayDeque<>();
                queue.add(rootGrid);

                double totalSubnetDrain = 0.0;
                int subnetCount = 0;
                int depth = 0;

                while (!queue.isEmpty() && depth < maxDepth && visited.size() <= 16) {
                    int levelSize = queue.size();
                    depth++;

                    for (int i = 0; i < levelSize; i++) {
                        IGrid current = queue.poll();
                        if (current == null) continue;

                        for (IGridNode n : current.getNodes()) {
                            for (IGridConnection conn : n.getConnections()) {
                                try {
                                    IGridNode otherNode = conn.getOtherSide(n);
                                    if (otherNode == null) continue;

                                    IGrid otherGrid = otherNode.getGrid();
                                    if (otherGrid != null && visited.add(otherGrid)) {
                                        subnetCount++;
                                        IEnergyService es = otherGrid.getService(IEnergyService.class);
                                        if (es != null) {
                                            totalSubnetDrain += es.getAvgPowerUsage();
                                        }
                                        queue.add(otherGrid);
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                }

                return new SubnetDrainResult((float) totalSubnetDrain, subnetCount);
            } catch (Throwable ignored) {
                return new SubnetDrainResult(0f, 0);
            }
        }

        private static Optional<AE2MetricData> inspectNetwork(Level level, BlockPos pos, Direction side) {
            try {
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null) {
                    return Optional.empty();
                }

                IGridNode node = null;
                IInWorldGridNodeHost host = null;
                if (be instanceof IInWorldGridNodeHost directHost) {
                    host = directHost;
                } else {
                    try {
                        host = GridHelper.getNodeHost(level, pos);
                    } catch (Throwable ignored) {
                    }
                }

                if (host != null) {
                    if (side != null) {
                        node = host.getGridNode(side);
                    }
                    if (node == null) {
                        node = host.getGridNode(null);
                    }
                    if (node == null) {
                        for (Direction d : Direction.values()) {
                            node = host.getGridNode(d);
                            if (node != null) break;
                        }
                    }
                }

                if (node == null) {
                    try {
                        if (side != null) {
                            node = GridHelper.getExposedNode(level, pos, side);
                        }
                        if (node == null) {
                            node = GridHelper.getExposedNode(level, pos, null);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (node == null) {
                    return Optional.empty();
                }

                IGrid grid = node.getGrid();
                if (grid == null) {
                    return Optional.empty();
                }

                IEnergyService energyService = grid.getService(IEnergyService.class);
                IPathingService pathingService = grid.getService(IPathingService.class);

                if (energyService == null) {
                    return Optional.empty();
                }

                double storedPower = energyService.getStoredPower();
                double maxPower = energyService.getMaxStoredPower();
                double avgUsage = energyService.getAvgPowerUsage();
                double avgInjection = energyService.getAvgPowerInjection();
                boolean isPowered = energyService.isNetworkPowered();

                boolean isBooting = pathingService != null && pathingService.isNetworkBooting();

                boolean isCreative = Double.isInfinite(storedPower) || Double.isInfinite(maxPower) || maxPower >= 1.0E12;

                float powerRate = (float) (avgInjection - avgUsage);
                float powerDrain = (float) avgUsage;

                SubnetDrainResult subnetResult = calculateSubnetDrain(grid, 3);

                AE2StabilityState stabilityState;
                if (isCreative) {
                    stabilityState = AE2StabilityState.CREATIVE;
                } else if (!isPowered || (isBooting && storedPower <= Math.max(10.0, avgUsage * 2.0))) {
                    stabilityState = AE2StabilityState.BROWNOUT;
                } else if (isBooting) {
                    stabilityState = AE2StabilityState.BOOTING;
                } else if (powerRate < -0.01f && maxPower > 0 && (storedPower / maxPower) <= 0.50) {
                    stabilityState = AE2StabilityState.DISCHARGING;
                } else {
                    stabilityState = AE2StabilityState.STABLE;
                }

                int usedChannels = 0;
                int maxChannels = 32;
                try {
                    usedChannels = node.getUsedChannels();
                    maxChannels = node.getMaxChannels();
                } catch (Throwable ignored) {
                }

                return Optional.of(new AE2MetricData(
                        true,
                        powerRate,
                        powerDrain,
                        subnetResult.totalSubnetDrain(),
                        subnetResult.subnetCount(),
                        storedPower,
                        maxPower,
                        (byte) stabilityState.ordinal(),
                        isCreative,
                        usedChannels,
                        maxChannels
                ));

            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }

        private static boolean isCreativeCellItem(Item cellItem) {
            if (cellItem == null) {
                return false;
            }
            try {
                Identifier key = BuiltInRegistries.ITEM.getKey(cellItem);
                return key != null
                        && "ae2".equals(key.getNamespace())
                        && key.getPath().contains("creative");
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static Long firstLong(Object target, String... names) {
            Method m = Reflect.findMethod(target.getClass(), names);
            return Reflect.invokeLong(m, target);
        }

        private static Optional<AE2CellStorageData> inspectCellStorage(Level level, BlockPos pos) {
            try {
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null) {
                    return Optional.empty();
                }

                if (!(be instanceof IChestOrDrive chestOrDrive)) {
                    return Optional.empty();
                }

                int cellCount = chestOrDrive.getCellCount();
                if (cellCount <= 0) {
                    return Optional.empty();
                }

                long sumUsedBytes = 0L;
                long sumTotalBytes = 0L;
                boolean isInfinite = false;

                for (int slot = 0; slot < cellCount; slot++) {
                    StorageCell cell = null;
                    try {
                        cell = chestOrDrive.getOriginalCellInventory(slot);
                    } catch (Throwable ignored) {
                    }

                    MEStorage meStorage = null;
                    try {
                        meStorage = chestOrDrive.getCellInventory(slot);
                    } catch (Throwable ignored) {
                    }

                    Item cellItem = null;
                    try {
                        cellItem = chestOrDrive.getCellItem(slot);
                    } catch (Throwable ignored) {
                    }

                    if (cell == null && meStorage == null && cellItem == null) {
                        continue;
                    }

                    boolean cellIsCreative = false;
                    long cellUsed = 0L;
                    long cellTotal = 0L;

                    if (isCreativeCellItem(cellItem)) {
                        cellIsCreative = true;
                    }

                    Object target = cell != null ? cell : meStorage;
                    if (target != null) {
                        Long used = firstLong(target, "getUsedBytes", "usedBytes", "getStoredItemCount");
                        if (used != null) {
                            cellUsed = used;
                        }
                        Long total = firstLong(target, "getTotalBytes", "totalBytes", "getBytes");
                        if (total != null) {
                            cellTotal = total;
                        }
                    }

                    if (cellTotal >= (Long.MAX_VALUE / 2) || cellUsed >= (Long.MAX_VALUE / 2) || cellIsCreative) {
                        isInfinite = true;
                    } else {
                        sumUsedBytes += Math.max(0L, cellUsed);
                        sumTotalBytes += Math.max(0L, cellTotal);
                    }
                }

                if (sumTotalBytes >= (Long.MAX_VALUE / 2)) {
                    isInfinite = true;
                }

                return Optional.of(new AE2CellStorageData(
                        true,
                        sumUsedBytes,
                        isInfinite ? (Long.MAX_VALUE / 2) : sumTotalBytes,
                        isInfinite
                ));
            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }
    }
}
