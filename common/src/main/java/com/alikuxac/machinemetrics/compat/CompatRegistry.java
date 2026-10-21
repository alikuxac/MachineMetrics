package com.alikuxac.machinemetrics.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CompatRegistry {

    public static final String NAMESPACE_AE2 = "appeng";
    public static final String NAMESPACE_MEKANISM = "mekanism";
    public static final String NAMESPACE_CREATE = "create";
    public static final String NAMESPACE_FUNCTIONAL_STORAGE = "functionalstorage";
    public static final String NAMESPACE_STORAGE_DRAWERS = "storagedrawers";
    public static final String NAMESPACE_SOPHISTICATED_STORAGE = "sophisticatedstorage";
    public static final String NAMESPACE_SOPHISTICATED_CORE = "sophisticatedcore";

    private static final String UNKNOWN = "";

    private static final Map<Block, String> NAMESPACE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, String> PATH_CACHE = new ConcurrentHashMap<>();

    private CompatRegistry() {
    }

    public static String namespaceOf(Level level, BlockPos pos) {
        BlockState state = blockStateOf(level, pos);
        if (state == null) {
            return UNKNOWN;
        }
        return namespaceOf(state.getBlock());
    }

    public static String namespaceOf(Block block) {
        if (block == null) {
            return UNKNOWN;
        }
        return NAMESPACE_CACHE.computeIfAbsent(block, b -> {
            try {
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(b);
                return key != null ? key.getNamespace() : UNKNOWN;
            } catch (Throwable ignored) {
                return UNKNOWN;
            }
        });
    }

    public static String pathOf(Level level, BlockPos pos) {
        BlockState state = blockStateOf(level, pos);
        if (state == null) {
            return UNKNOWN;
        }
        return pathOf(state.getBlock());
    }

    public static String pathOf(Block block) {
        if (block == null) {
            return UNKNOWN;
        }
        return PATH_CACHE.computeIfAbsent(block, b -> {
            try {
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(b);
                return key != null ? key.getPath() : UNKNOWN;
            } catch (Throwable ignored) {
                return UNKNOWN;
            }
        });
    }

    public static String friendlyNameOf(Level level, BlockPos pos) {
        BlockState state = blockStateOf(level, pos);
        if (state == null) {
            return "unknown";
        }
        try {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return key != null ? key.toString() : "unknown";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    public static boolean isAe2Block(Level level, BlockPos pos) {
        return NAMESPACE_AE2.equals(namespaceOf(level, pos));
    }

    public static boolean isMekanismBlock(Level level, BlockPos pos) {
        return NAMESPACE_MEKANISM.equals(namespaceOf(level, pos));
    }

    public static boolean isCreateBlock(Level level, BlockPos pos) {
        return NAMESPACE_CREATE.equals(namespaceOf(level, pos));
    }

    public static boolean isFluidTankBlock(Level level, BlockPos pos) {
        String path = pathOf(level, pos).toLowerCase(java.util.Locale.ROOT);
        return path.contains("tank")
                || path.contains("fluid")
                || path.contains("vat")
                || path.contains("drum")
                || path.contains("reservoir");
    }

    public static boolean isDrawerBlock(Level level, BlockPos pos) {
        String ns = namespaceOf(level, pos);
        boolean isDrawerNs = NAMESPACE_FUNCTIONAL_STORAGE.equals(ns) || NAMESPACE_STORAGE_DRAWERS.equals(ns);
        return isDrawerNs && !isFluidTankBlock(level, pos);
    }

    public static boolean isSophisticatedStorageBlock(Level level, BlockPos pos) {
        return NAMESPACE_SOPHISTICATED_STORAGE.equals(namespaceOf(level, pos));
    }

    public static boolean blockIsInNamespace(Level level, BlockPos pos, String namespace) {
        if (namespace == null) {
            return false;
        }
        return namespace.equals(namespaceOf(level, pos));
    }

    public static boolean blockPathContains(Level level, BlockPos pos, String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return false;
        }
        return pathOf(level, pos).toLowerCase().contains(fragment.toLowerCase());
    }

    private static BlockState blockStateOf(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        try {
            if (!level.isLoaded(pos)) {
                return null;
            }
            return level.getBlockState(pos);
        } catch (Throwable ignored) {
            return null;
        }
    }
}