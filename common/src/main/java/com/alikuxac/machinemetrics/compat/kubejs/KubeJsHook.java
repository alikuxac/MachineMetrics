package com.alikuxac.machinemetrics.compat.kubejs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public interface KubeJsHook {

    List<MetricLine> collectMetrics(Level level, BlockPos pos, Direction face);

    boolean shouldInspect(Player player, BlockPos pos);
}
