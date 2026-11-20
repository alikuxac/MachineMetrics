package com.alikuxac.machinemetrics.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class MachineMetricsKubeJsPlugin implements KubeJSPlugin, KubeJsHook {

    @Override
    public void init() {
        KubeJsBridge.setHook(this);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MachineMetricsEvents.GROUP);
    }

    @Override
    public List<MetricLine> collectMetrics(Level level, BlockPos pos, Direction face) {
        if (level == null || pos == null) {
            return List.of();
        }
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            BlockMetricEventJS event = new BlockMetricEventJS(level, pos, blockEntity, face);
            MachineMetricsEvents.REGISTER_BLOCK_METRICS.post(ScriptType.CLIENT, event);
            return new ArrayList<>(event.getLines());
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    @Override
    public boolean shouldInspect(Player player, BlockPos pos) {
        if (player == null) {
            return false;
        }
        try {
            ShouldInspectEventJS event = new ShouldInspectEventJS(player, pos);
            EventResult result = MachineMetricsEvents.SHOULD_INSPECT.post(ScriptType.CLIENT, event);
            return !result.interruptFalse();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
