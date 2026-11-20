package com.alikuxac.machinemetrics.compat.kubejs;

import com.alikuxac.machinemetrics.config.ClientConfig;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class BlockMetricEventJS implements KubeEvent {

    private final Level level;
    private final BlockPos pos;
    private final BlockEntity blockEntity;
    private final Direction face;
    private final List<MetricLine> lines = new ArrayList<>();

    public BlockMetricEventJS(Level level, BlockPos pos, BlockEntity blockEntity, Direction face) {
        this.level = level;
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.face = face;
    }

    public Level getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockEntity getBlockEntity() {
        return blockEntity;
    }

    public Direction getFace() {
        return face;
    }

    public String getBlockId() {
        BlockState state = level.getBlockState(pos);
        return state.getBlock().getDescriptionId();
    }

    public CompoundTag getData() {
        return blockEntity != null ? blockEntity.saveWithoutMetadata(level.registryAccess()) : new CompoundTag();
    }

    public BlockMetricEventJS addLine(String label, String value) {
        return addLine(label, value, 0xFFFFFF);
    }

    public BlockMetricEventJS addLine(String label, String value, String hexColor) {
        return addLine(label, value, ClientConfig.parseHexColor(hexColor, 0xFFFFFF));
    }

    public BlockMetricEventJS addLine(String label, String value, int color) {
        lines.add(MetricLine.of(label, value, color));
        return this;
    }

    public List<MetricLine> getLines() {
        return lines;
    }
}
