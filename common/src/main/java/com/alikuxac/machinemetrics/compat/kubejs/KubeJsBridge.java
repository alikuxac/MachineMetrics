package com.alikuxac.machinemetrics.compat.kubejs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public final class KubeJsBridge {

    private static final KubeJsHook NOOP = new KubeJsHook() {
        @Override
        public List<MetricLine> collectMetrics(Level level, BlockPos pos, Direction face) {
            return List.of();
        }

        @Override
        public boolean shouldInspect(Player player, BlockPos pos) {
            return true;
        }
    };

    private static volatile KubeJsHook hook = NOOP;

    private KubeJsBridge() {
    }

    public static void setHook(KubeJsHook hook) {
        KubeJsBridge.hook = hook != null ? hook : NOOP;
    }

    public static boolean isAvailable() {
        return hook != NOOP;
    }

    public static List<MetricLine> collectMetrics(Level level, BlockPos pos, Direction face) {
        if (level == null || pos == null || hook == NOOP) {
            return List.of();
        }
        try {
            List<MetricLine> lines = hook.collectMetrics(level, pos, face);
            return lines != null ? lines : List.of();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static boolean shouldInspect(Player player, BlockPos pos) {
        if (hook == NOOP) {
            return true;
        }
        try {
            return hook.shouldInspect(player, pos);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
