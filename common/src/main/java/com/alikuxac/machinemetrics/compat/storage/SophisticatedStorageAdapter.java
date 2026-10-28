package com.alikuxac.machinemetrics.compat.storage;

import com.alikuxac.machinemetrics.compat.CompatRegistry;
import com.alikuxac.machinemetrics.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

public class SophisticatedStorageAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SophisticatedStorageAdapter.class);

    private static final long INFINITE_THRESHOLD = Long.MAX_VALUE / 2;

    public record StorageMetricData(
            boolean isStorage,
            long storedItems,
            long maxCapacity,
            boolean isInfinite
    ) {
        public static final StorageMetricData EMPTY = new StorageMetricData(false, 0L, 0L, false);

        public boolean isEmpty() {
            return !isStorage;
        }
    }

    public static Optional<String> resolveDisplayName(Level level, BlockPos pos) {
        if (level == null || pos == null || !CompatRegistry.isSophisticatedStorageBlock(level, pos)) {
            return Optional.empty();
        }

        BlockEntity be;
        BlockState state;
        try {
            be = level.getBlockEntity(pos);
            state = level.getBlockState(pos);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
        if (be == null || state == null) {
            return Optional.empty();
        }

        String custom = readCustomName(be);
        if (custom != null && !custom.isEmpty()) {
            return Optional.of(custom);
        }

        String tier = readTierMaterialName(be);
        String descriptionId = state.getBlock().getDescriptionId();
        if (tier == null || tier.isEmpty()) {
            return Optional.empty();
        }

        try {
            String name = Component.translatable(descriptionId, tier).getString();
            if (name != null && !name.isEmpty() && !name.equals(descriptionId)) {
                return Optional.of(name);
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static String readCustomName(BlockEntity be) {
        try {
            if (be instanceof Nameable nameable && nameable.hasCustomName()) {
                Component custom = nameable.getCustomName();
                if (custom != null) {
                    String s = custom.getString();
                    if (s != null && !s.isEmpty()) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        for (String methodName : new String[]{"getCustomName"}) {
            try {
                Method m = be.getClass().getMethod(methodName);
                Object result = m.invoke(be);
                if (result instanceof Component comp) {
                    String s = comp.getString();
                    if (s != null && !s.isEmpty()) {
                        return s;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String readTierMaterialName(BlockEntity be) {
        Object tier = null;
        for (String methodName : new String[]{
                "getStorageTier", "getTier", "getMaterial", "getStorageMaterial", "getWoodType"
        }) {
            try {
                Method m = be.getClass().getMethod(methodName);
                Object result = m.invoke(be);
                if (result != null) {
                    tier = result;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        if (tier == null) {
            return null;
        }

        if (tier instanceof Component comp) {
            String s = comp.getString();
            return s != null && !s.isEmpty() ? s : null;
        }

        try {
            Method idM = tier.getClass().getMethod("getSerializedName");
            Object serialized = idM.invoke(tier);
            if (serialized != null) {
                String s = serialized.toString();
                if (!s.isEmpty()) {
                    return capitalize(s.replace('_', ' '));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            String s = tier.toString();
            if (s != null && !s.isEmpty() && !s.contains("@") && !s.contains(".")) {
                return capitalize(s.replace('_', ' '));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public static Optional<StorageMetricData> inspectStorage(Level level, BlockPos pos) {
        return inspectStorage(level, pos, null);
    }

    public static Optional<StorageMetricData> inspectStorage(Level level, BlockPos pos, Direction hitFace) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        if (!CompatRegistry.isSophisticatedStorageBlock(level, pos)) {
            return Optional.empty();
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return Optional.empty();
        }

        ResourceHandler<ItemResource> handler = null;
        try {
            handler = level.getCapability(Capabilities.Item.BLOCK, pos, hitFace);
            if (handler == null) {
                for (Direction side : Direction.values()) {
                    handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
                    if (handler != null) {
                        break;
                    }
                }
            }
            if (handler == null) {
                handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
            }
        } catch (Throwable ignored) {
        }

        if (handler == null) {
            return Optional.empty();
        }

        try {
            int slots = handler.size();
            if (slots <= 0) {
                return Optional.empty();
            }

            long storedItems = 0L;
            long maxCapacity = 0L;
            boolean debug = ServerConfig.DEBUG_LOGGING.get();
            StringBuilder probe = debug ? new StringBuilder() : null;
            if (probe != null) {
                probe.append("[SSDebug] handler=").append(handler.getClass().getName())
                        .append(" slots=").append(slots);
            }

            for (int i = 0; i < slots; i++) {
                ItemResource resource = handler.getResource(i);
                ItemStack stack = resource.toStack();
                boolean hasItem = !stack.isEmpty();
                if (hasItem) {
                    storedItems += handler.getAmountAsLong(i);
                }

                long slotLimit = 0L;
                try {
                    slotLimit = handler.getCapacityAsLong(i, resource);
                } catch (Throwable ignored) {
                }

                long slotMax;
                if (slotLimit == Integer.MAX_VALUE) {
                    slotMax = Integer.MAX_VALUE;
                } else if (slotLimit > 0) {
                    slotMax = slotLimit;
                } else if (hasItem) {
                    slotMax = stack.getMaxStackSize();
                } else {
                    slotMax = 64L;
                }

                if (hasItem && stack.getMaxStackSize() > slotMax) {
                    slotMax = stack.getMaxStackSize();
                }

                maxCapacity += slotMax;

                if (probe != null) {
                    probe.append(" | i=").append(i)
                            .append(" count=").append(hasItem ? stack.getCount() : 0)
                            .append(" maxSS=").append(hasItem ? stack.getMaxStackSize() : 0)
                            .append(" slotLimit=").append(slotLimit);
                }
            }

            boolean isInfinite = maxCapacity >= INFINITE_THRESHOLD;

            if (probe != null) {
                probe.append(" => stored=").append(storedItems)
                        .append(" max=").append(maxCapacity)
                        .append(" infinite=").append(isInfinite);
                LOGGER.info(probe.toString());
            }

            if (maxCapacity <= 0 && !isInfinite) {
                return Optional.empty();
            }

            return Optional.of(new StorageMetricData(true, storedItems, maxCapacity, isInfinite));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }
}