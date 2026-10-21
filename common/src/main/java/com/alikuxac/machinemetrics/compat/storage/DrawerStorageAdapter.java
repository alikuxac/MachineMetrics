package com.alikuxac.machinemetrics.compat.storage;

import com.alikuxac.machinemetrics.compat.CompatRegistry;
import com.alikuxac.machinemetrics.compat.Reflect;
import com.alikuxac.machinemetrics.storage.BlockCapacityReader;
import com.alikuxac.machinemetrics.storage.BlockCapacityReader.StorageCapacity;
import com.alikuxac.machinemetrics.storage.BlockCapacityReader.StorageType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.Optional;

public class DrawerStorageAdapter {

    private static final long INFINITE_THRESHOLD = Long.MAX_VALUE / 2;

    public record DrawerMetricData(
            boolean isDrawer,
            long storedItems,
            long maxCapacity,
            boolean isInfinite
    ) {
        public static final DrawerMetricData EMPTY = new DrawerMetricData(false, 0L, 0L, false);

        public boolean isEmpty() {
            return !isDrawer;
        }
    }

    private static Boolean functionalStorageLoadedCache = null;
    private static Boolean storageDrawersLoadedCache = null;

    public static boolean isFunctionalStorageLoaded() {
        if (functionalStorageLoadedCache == null) {
            try {
                Class.forName("com.buuz135.functionalstorage.block.tile.ControllableDrawerTile", false, DrawerStorageAdapter.class.getClassLoader());
                functionalStorageLoadedCache = true;
            } catch (Throwable ignored) {
                functionalStorageLoadedCache = false;
            }
        }
        return functionalStorageLoadedCache;
    }

    public static boolean isStorageDrawersLoaded() {
        if (storageDrawersLoadedCache == null) {
            try {
                Class.forName("com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup", false, DrawerStorageAdapter.class.getClassLoader());
                storageDrawersLoadedCache = true;
            } catch (Throwable ignored) {
                try {
                    Class.forName("com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers", false, DrawerStorageAdapter.class.getClassLoader());
                    storageDrawersLoadedCache = true;
                } catch (Throwable ignored2) {
                    storageDrawersLoadedCache = false;
                }
            }
        }
        return storageDrawersLoadedCache;
    }

    public static Optional<DrawerMetricData> inspectDrawer(Level level, BlockPos pos) {
        return inspectDrawer(level, pos, null);
    }

    public static Optional<DrawerMetricData> inspectDrawer(Level level, BlockPos pos, net.minecraft.core.Direction hitFace) {
        if (level == null || pos == null) {
            return Optional.empty();
        }

        String namespace = CompatRegistry.namespaceOf(level, pos);
        boolean isFunctionalStorage = CompatRegistry.NAMESPACE_FUNCTIONAL_STORAGE.equals(namespace);
        boolean isStorageDrawers = CompatRegistry.NAMESPACE_STORAGE_DRAWERS.equals(namespace);

        if (!isFunctionalStorage && !isStorageDrawers) {
            return Optional.empty();
        }

        if (CompatRegistry.isFluidTankBlock(level, pos)) {
            return Optional.empty();
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return Optional.empty();
        }

        if (isFunctionalStorage || isFunctionalStorageLoaded()) {
            try {
                Optional<DrawerMetricData> fsData = FunctionalStorageWorker.inspect(be);
                if (fsData.isPresent()) {
                    return fsData;
                }
            } catch (Throwable ignored) {
            }
        }

        if (isStorageDrawers || isStorageDrawersLoaded()) {
            try {
                Optional<DrawerMetricData> sdData = StorageDrawersWorker.inspect(be);
                if (sdData.isPresent()) {
                    return sdData;
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            StorageCapacity cap = BlockCapacityReader.readCapacity(level, pos, hitFace);
            if (cap.hasStorage() && cap.type() == StorageType.ITEM) {
                return Optional.of(new DrawerMetricData(true, cap.current(), cap.max(), cap.isInfinite()));
            }
        } catch (Throwable ignored) {
        }

        return Optional.empty();
    }

    private static final class FunctionalStorageWorker {

        private static final String[] STORAGE_METHODS = { "getStorage", "getHandler", "getItemHandler" };
        private static final String[] SLOT_COUNT_METHODS = { "getSlots", "getSlotCount", "getDrawerCount" };

        private static Optional<DrawerMetricData> inspect(BlockEntity be) {
            try {
                boolean isCreative = Reflect.getBooleanField(be, false, "isCreative", "creative", "unlimited");
                Integer multObj = Reflect.getIntField(be, "mult", "multiplier");
                int mult = multObj != null ? multObj : 1;

                Object storageHandler = null;
                Method storageMethod = Reflect.findMethod(be.getClass(), STORAGE_METHODS);
                if (storageMethod != null) {
                    storageHandler = Reflect.invokeSafe(storageMethod, be);
                }

                if (storageHandler == null) {
                    return Optional.empty();
                }

                int slots = slotCount(storageHandler);
                if (slots <= 0) {
                    return Optional.empty();
                }

                boolean isCompacting = determineCompacting(storageHandler, slots);

                long storedItems = 0L;
                long maxCapacity = 0L;

                if (isCompacting) {
                    storedItems = slotCountStored(storageHandler, 0);
                    maxCapacity = slotLimit(storageHandler, 0);
                } else {
                    for (int i = 0; i < slots; i++) {
                        storedItems += slotCountStored(storageHandler, i);
                        maxCapacity += slotLimit(storageHandler, i);
                    }
                }

                if (mult > 1 && maxCapacity > 0) {
                    maxCapacity *= mult;
                }

                boolean isInfinite = isCreative;
                if (maxCapacity >= INFINITE_THRESHOLD || maxCapacity == Integer.MAX_VALUE) {
                    isInfinite = true;
                    maxCapacity = INFINITE_THRESHOLD;
                }

                return Optional.of(new DrawerMetricData(true, storedItems, maxCapacity, isInfinite));
            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }

        private static int slotCount(Object handler) {
            Integer slots = Reflect.invokeInt(Reflect.findMethod(handler.getClass(), SLOT_COUNT_METHODS), handler);
            return slots != null ? slots : 0;
        }

        private static long slotCountStored(Object handler, int slot) {
            Object stack = Reflect.invokeSafe(Reflect.findMethodTyped(handler.getClass(), new Class<?>[] { int.class }, "getStackInSlot"), handler, slot);
            if (stack instanceof ItemStack itemStack) {
                return itemStack.isEmpty() ? 0L : itemStack.getCount();
            }
            return 0L;
        }

        private static long slotLimit(Object handler, int slot) {
            Method m = Reflect.findMethodTyped(handler.getClass(), new Class<?>[] { int.class }, "getSlotLimit", "getSlotCapacity");
            Object res = Reflect.invokeSafe(m, handler, slot);
            if (res instanceof Number num) {
                return num.longValue();
            }
            return 0L;
        }

        private static boolean determineCompacting(Object storageHandler, int slots) {
            try {
                Object compacting = Reflect.invokeSafe(
                        Reflect.findMethod(storageHandler.getClass(), "isCompacting", "isCompactingDrawer", "getCompacting"),
                        storageHandler);
                if (compacting instanceof Boolean b) {
                    return b;
                }
                Boolean field = null;
                Object fieldVal = Reflect.getField(storageHandler, "compacting", "isCompacting");
                if (fieldVal instanceof Boolean b) {
                    field = b;
                }
                if (field != null) {
                    return field;
                }
            } catch (Throwable ignored) {
            }
            return slots == 1;
        }
    }

    private static final class StorageDrawersWorker {

        private static final String[] GROUP_METHODS = { "getGroup", "getDrawerGroup" };
        private static final String[] DRAWER_COUNT_METHODS = { "getDrawerCount" };

        private static Optional<DrawerMetricData> inspect(BlockEntity be) {
            try {
                Object drawerGroup = Reflect.invokeSafe(Reflect.findMethod(be.getClass(), GROUP_METHODS), be);
                if (drawerGroup == null) {
                    return Optional.empty();
                }

                Integer countObj = Reflect.invokeInt(Reflect.findMethod(drawerGroup.getClass(), DRAWER_COUNT_METHODS), drawerGroup);
                int drawerCount = countObj != null ? countObj : 0;
                if (drawerCount <= 0) {
                    return Optional.empty();
                }

                Method getDrawerMethod = Reflect.findMethodTyped(drawerGroup.getClass(), new Class<?>[] { int.class }, "getDrawer");
                if (getDrawerMethod == null) {
                    return Optional.empty();
                }

                boolean isCompacting = determineCompacting(drawerGroup, drawerCount);

                long storedItems = 0L;
                long maxCapacity = 0L;
                boolean isInfinite = false;

                if (isCompacting) {
                    Object drawer0 = Reflect.invokeSafe(getDrawerMethod, drawerGroup, 0);
                    if (drawer0 != null) {
                        storedItems = drawerStoredCount(drawer0);
                        maxCapacity = drawerMaxCapacity(drawer0);
                        if (drawerInfinite(drawer0, maxCapacity)) {
                            isInfinite = true;
                        }
                    }
                } else {
                    for (int i = 0; i < drawerCount; i++) {
                        Object drawer = Reflect.invokeSafe(getDrawerMethod, drawerGroup, i);
                        if (drawer == null) {
                            continue;
                        }

                        Object enabled = Reflect.invokeSafe(Reflect.findMethod(drawer.getClass(), "isEnabled"), drawer);
                        if (enabled instanceof Boolean b && !b) {
                            continue;
                        }

                        storedItems += drawerStoredCount(drawer);
                        long cap = drawerMaxCapacity(drawer);
                        maxCapacity += cap;

                        if (drawerInfinite(drawer, cap)) {
                            isInfinite = true;
                        }
                    }
                }

                if (isInfinite || maxCapacity >= INFINITE_THRESHOLD || maxCapacity == Integer.MAX_VALUE) {
                    isInfinite = true;
                    maxCapacity = INFINITE_THRESHOLD;
                }

                return Optional.of(new DrawerMetricData(true, storedItems, maxCapacity, isInfinite));
            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }

        private static boolean determineCompacting(Object drawerGroup, int drawerCount) {
            try {
                Object compacting = Reflect.invokeSafe(
                        Reflect.findMethod(drawerGroup.getClass(), "isCompacting", "isCompactingDrawer"),
                        drawerGroup);
                if (compacting instanceof Boolean b) {
                    return b;
                }
            } catch (Throwable ignored) {
            }
            return drawerCount == 1;
        }

        private static long drawerStoredCount(Object drawer) {
            Long stored = Reflect.invokeLong(
                    Reflect.findMethod(drawer.getClass(), "getStoredItemCount", "getStoredCount"), drawer);
            return stored != null ? stored : 0L;
        }

        private static long drawerMaxCapacity(Object drawer) {
            Long cap = Reflect.invokeLong(
                    Reflect.findMethod(drawer.getClass(), "getMaxCapacity", "getMaxItemCount"), drawer);
            if (cap != null) {
                return cap;
            }
            Method m = Reflect.findMethodTyped(drawer.getClass(), new Class<?>[] { ItemStack.class }, "getMaxCapacity");
            Object res = Reflect.invokeSafe(m, drawer, ItemStack.EMPTY);
            if (res instanceof Number num) {
                return num.longValue();
            }
            return 0L;
        }

        private static boolean drawerInfinite(Object drawer, long cap) {
            if (cap >= INFINITE_THRESHOLD || cap == Integer.MAX_VALUE) {
                return true;
            }
            Object unlimited = Reflect.invokeSafe(Reflect.findMethod(drawer.getClass(), "isUnlimited"), drawer);
            return unlimited instanceof Boolean b && b;
        }
    }
}