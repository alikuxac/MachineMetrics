package com.alikuxac.machinemetrics.compat.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class DrawerStorageAdapter {

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
        if (level == null || pos == null) {
            return Optional.empty();
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return Optional.empty();
        }

        String beClassName = be.getClass().getName();

        if (isFunctionalStorageLoaded() || beClassName.contains("functionalstorage")) {
            try {
                var fsData = FunctionalStorageWorker.inspect(be);
                if (fsData.isPresent()) {
                    return fsData;
                }
            } catch (Throwable ignored) {
            }
        }

        if (isStorageDrawersLoaded() || beClassName.contains("storagedrawers")) {
            try {
                var sdData = StorageDrawersWorker.inspect(be);
                if (sdData.isPresent()) {
                    return sdData;
                }
            } catch (Throwable ignored) {
            }
        }

        return Optional.empty();
    }

    private static final class FunctionalStorageWorker {

        private static Optional<DrawerMetricData> inspect(BlockEntity be) {
            try {
                Class<?> clazz = be.getClass();
                boolean isFsTile = false;
                Class<?> current = clazz;
                while (current != null && current != Object.class) {
                    if (current.getName().contains("ControllableDrawerTile") || current.getName().contains("functionalstorage")) {
                        isFsTile = true;
                        break;
                    }
                    current = current.getSuperclass();
                }

                if (!isFsTile) {
                    return Optional.empty();
                }

                boolean isCreative = getBooleanField(be, "isCreative");
                int mult = getIntField(be, "mult", 1);
                boolean isCompacting = clazz.getSimpleName().contains("Compacting");

                long storedItems = 0L;
                long maxCapacity = 0L;

                Object storageHandler = null;
                try {
                    Method getStorageMethod = clazz.getMethod("getStorage");
                    storageHandler = getStorageMethod.invoke(be);
                } catch (Throwable ignored) {
                    try {
                        Method getHandlerMethod = clazz.getMethod("getHandler");
                        storageHandler = getHandlerMethod.invoke(be);
                    } catch (Throwable ignored2) {
                    }
                }

                if (storageHandler != null) {
                    try {
                        Method getSlotsMethod = storageHandler.getClass().getMethod("getSlots");
                        int slots = (int) getSlotsMethod.invoke(storageHandler);

                        Method getStackInSlotMethod = storageHandler.getClass().getMethod("getStackInSlot", int.class);
                        Method getSlotLimitMethod = storageHandler.getClass().getMethod("getSlotLimit", int.class);

                        if (isCompacting) {
                            if (slots > 0) {
                                ItemStack stack0 = (ItemStack) getStackInSlotMethod.invoke(storageHandler, 0);
                                if (stack0 != null && !stack0.isEmpty()) {
                                    storedItems = stack0.getCount();
                                }
                                int limit0 = (int) getSlotLimitMethod.invoke(storageHandler, 0);
                                maxCapacity = limit0;
                            }
                        } else {
                            for (int i = 0; i < slots; i++) {
                                ItemStack stack = (ItemStack) getStackInSlotMethod.invoke(storageHandler, i);
                                if (stack != null && !stack.isEmpty()) {
                                    storedItems += stack.getCount();
                                }
                                int limit = (int) getSlotLimitMethod.invoke(storageHandler, i);
                                maxCapacity += limit;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (isCreative || maxCapacity >= (Long.MAX_VALUE / 2) || maxCapacity == Integer.MAX_VALUE) {
                    isCreative = true;
                    maxCapacity = Long.MAX_VALUE / 2;
                }

                return Optional.of(new DrawerMetricData(
                        true,
                        storedItems,
                        maxCapacity,
                        isCreative
                ));
            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }

        private static boolean getBooleanField(Object obj, String fieldName) {
            Class<?> c = obj.getClass();
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.getBoolean(obj);
                } catch (Throwable ignored) {
                }
                c = c.getSuperclass();
            }
            return false;
        }

        private static int getIntField(Object obj, String fieldName, int fallback) {
            Class<?> c = obj.getClass();
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.getInt(obj);
                } catch (Throwable ignored) {
                }
                c = c.getSuperclass();
            }
            return fallback;
        }
    }

    private static final class StorageDrawersWorker {

        private static Optional<DrawerMetricData> inspect(BlockEntity be) {
            try {
                Class<?> clazz = be.getClass();
                boolean isSdTile = false;
                Class<?> current = clazz;
                while (current != null && current != Object.class) {
                    if (current.getName().contains("BlockEntityDrawers") || current.getName().contains("TileEntityDrawers") || current.getName().contains("storagedrawers")) {
                        isSdTile = true;
                        break;
                    }
                    current = current.getSuperclass();
                }

                if (!isSdTile) {
                    return Optional.empty();
                }

                Object drawerGroup = null;
                try {
                    Method getGroupMethod = clazz.getMethod("getGroup");
                    drawerGroup = getGroupMethod.invoke(be);
                } catch (Throwable ignored) {
                }

                if (drawerGroup == null) {
                    return Optional.empty();
                }

                Method getDrawerCountMethod = drawerGroup.getClass().getMethod("getDrawerCount");
                int drawerCount = (int) getDrawerCountMethod.invoke(drawerGroup);
                if (drawerCount <= 0) {
                    return Optional.empty();
                }

                Method getDrawerMethod = drawerGroup.getClass().getMethod("getDrawer", int.class);
                boolean isCompacting = drawerGroup.getClass().getSimpleName().contains("Compacting") || clazz.getSimpleName().contains("Compacting");

                long storedItems = 0L;
                long maxCapacity = 0L;
                boolean isInfinite = false;

                if (isCompacting) {
                    Object drawer0 = getDrawerMethod.invoke(drawerGroup, 0);
                    if (drawer0 != null) {
                        storedItems = getDrawerStoredCount(drawer0);
                        maxCapacity = getDrawerMaxCapacity(drawer0);
                        if (checkDrawerInfinite(drawer0, maxCapacity)) {
                            isInfinite = true;
                        }
                    }
                } else {
                    for (int i = 0; i < drawerCount; i++) {
                        Object drawer = getDrawerMethod.invoke(drawerGroup, i);
                        if (drawer == null) continue;

                        try {
                            Method isEnabledMethod = drawer.getClass().getMethod("isEnabled");
                            if (!((boolean) isEnabledMethod.invoke(drawer))) {
                                continue;
                            }
                        } catch (Throwable ignored) {
                        }

                        storedItems += getDrawerStoredCount(drawer);
                        long cap = getDrawerMaxCapacity(drawer);
                        maxCapacity += cap;

                        if (checkDrawerInfinite(drawer, cap)) {
                            isInfinite = true;
                        }
                    }
                }

                if (isInfinite || maxCapacity >= (Long.MAX_VALUE / 2) || maxCapacity == Integer.MAX_VALUE) {
                    isInfinite = true;
                    maxCapacity = Long.MAX_VALUE / 2;
                }

                return Optional.of(new DrawerMetricData(
                        true,
                        storedItems,
                        maxCapacity,
                        isInfinite
                ));
            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }

        private static long getDrawerStoredCount(Object drawer) {
            try {
                Method m = drawer.getClass().getMethod("getStoredItemCount");
                Object res = m.invoke(drawer);
                if (res instanceof Number num) {
                    return num.longValue();
                }
            } catch (Throwable ignored) {
            }
            return 0L;
        }

        private static long getDrawerMaxCapacity(Object drawer) {
            try {
                Method m = drawer.getClass().getMethod("getMaxCapacity");
                Object res = m.invoke(drawer);
                if (res instanceof Number num) {
                    return num.longValue();
                }
            } catch (Throwable ignored) {
                try {
                    Method m = drawer.getClass().getMethod("getMaxCapacity", ItemStack.class);
                    Object res = m.invoke(drawer, ItemStack.EMPTY);
                    if (res instanceof Number num) {
                        return num.longValue();
                    }
                } catch (Throwable ignored2) {
                }
            }
            return 0L;
        }

        private static boolean checkDrawerInfinite(Object drawer, long cap) {
            if (cap >= (Long.MAX_VALUE / 2) || cap == Integer.MAX_VALUE) {
                return true;
            }
            try {
                Method m = drawer.getClass().getMethod("isUnlimited");
                if ((boolean) m.invoke(drawer)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
    }
}
