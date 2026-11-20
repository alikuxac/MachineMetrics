package com.alikuxac.machinemetrics.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class ShouldInspectEventJS implements KubeEvent {

    private final Player player;
    private final BlockPos pos;

    public ShouldInspectEventJS(Player player, BlockPos pos) {
        this.player = player;
        this.pos = pos;
    }

    public Player getPlayer() {
        return player;
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getDimension() {
        return player != null ? player.level().dimension().location().toString() : "";
    }
}
