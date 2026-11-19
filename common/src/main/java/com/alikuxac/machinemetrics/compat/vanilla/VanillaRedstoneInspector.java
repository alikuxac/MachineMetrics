package com.alikuxac.machinemetrics.compat.vanilla;

import com.alikuxac.machinemetrics.telemetry.RedstoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;

public final class VanillaRedstoneInspector {

    private VanillaRedstoneInspector() {}

    public static boolean isRedstoneBlock(BlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        return block instanceof RedStoneWireBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock
                || block instanceof CrafterBlock
                || block instanceof CopperBulbBlock;
    }

    public static RedstoneData sample(Level level, BlockPos pos, BlockState state, BlockEntity be) {
        if (state == null) return null;
        Block block = state.getBlock();

        if (block instanceof RedStoneWireBlock) {
            int power = state.hasProperty(BlockStateProperties.POWER) ? state.getValue(BlockStateProperties.POWER) : 0;
            return new RedstoneData(
                    RedstoneData.RedstoneType.WIRE,
                    power,
                    0,
                    false,
                    power > 0,
                    false,
                    false,
                    RedstoneData.MODE_NONE,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        if (block instanceof RepeaterBlock) {
            int delay = state.hasProperty(BlockStateProperties.DELAY) ? state.getValue(BlockStateProperties.DELAY) : 1;
            boolean locked = state.hasProperty(BlockStateProperties.LOCKED) && state.getValue(BlockStateProperties.LOCKED);
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            return new RedstoneData(
                    RedstoneData.RedstoneType.REPEATER,
                    powered ? 15 : 0,
                    delay,
                    locked,
                    powered,
                    false,
                    false,
                    RedstoneData.MODE_NONE,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        if (block instanceof ComparatorBlock) {
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            ComparatorMode mode = state.hasProperty(BlockStateProperties.MODE_COMPARATOR)
                    ? state.getValue(BlockStateProperties.MODE_COMPARATOR)
                    : ComparatorMode.COMPARE;
            byte modeByte = (mode == ComparatorMode.SUBTRACT) ? RedstoneData.MODE_SUBTRACT : RedstoneData.MODE_COMPARE;

            int outputSignal = 0;
            if (be instanceof ComparatorBlockEntity compBe) {
                outputSignal = compBe.getOutputSignal();
            }

            Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                    : Direction.NORTH;

            int rearSignal = calculateRearInputSignal(level, pos, facing);
            int maxSideSignal = calculateMaxSideSignal(level, pos, facing);

            int expectedOutput;
            if (mode == ComparatorMode.SUBTRACT) {
                expectedOutput = Math.max(0, rearSignal - maxSideSignal);
            } else {
                expectedOutput = maxSideSignal > rearSignal ? 0 : rearSignal;
            }

            return new RedstoneData(
                    RedstoneData.RedstoneType.COMPARATOR,
                    outputSignal,
                    0,
                    false,
                    powered,
                    false,
                    false,
                    modeByte,
                    expectedOutput,
                    rearSignal,
                    maxSideSignal,
                    0,
                    0
            );
        }

        if (block instanceof CrafterBlock) {
            boolean crafting = state.hasProperty(BlockStateProperties.CRAFTING) && state.getValue(BlockStateProperties.CRAFTING);
            boolean triggered = state.hasProperty(BlockStateProperties.TRIGGERED) && state.getValue(BlockStateProperties.TRIGGERED);

            int disabledMask = 0;
            int occupiedMask = 0;
            int comparatorSignal = 0;

            if (be instanceof CrafterBlockEntity crafterBe) {
                for (int slot = 0; slot < 9; slot++) {
                    if (crafterBe.isSlotDisabled(slot)) {
                        disabledMask |= (1 << slot);
                    }
                    ItemStack stack = crafterBe.getItem(slot);
                    if (!stack.isEmpty()) {
                        occupiedMask |= (1 << slot);
                    }
                }
                comparatorSignal = crafterBe.getRedstoneSignal();
            }

            return new RedstoneData(
                    RedstoneData.RedstoneType.CRAFTER,
                    comparatorSignal,
                    0,
                    false,
                    triggered,
                    false,
                    crafting,
                    RedstoneData.MODE_NONE,
                    0,
                    0,
                    0,
                    disabledMask,
                    occupiedMask
            );
        }

        if (block instanceof CopperBulbBlock) {
            boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            return new RedstoneData(
                    RedstoneData.RedstoneType.COPPER_BULB,
                    lit ? 15 : 0,
                    0,
                    false,
                    powered,
                    lit,
                    false,
                    RedstoneData.MODE_NONE,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        return null;
    }

    public static int calculateRearInputSignal(Level level, BlockPos pos, Direction facing) {
        if (level == null || pos == null || facing == null) return 0;
        Direction rearDir = facing.getOpposite();
        BlockPos rearPos = pos.relative(rearDir);
        if (!level.isLoaded(rearPos)) return 0;

        BlockState rearState = level.getBlockState(rearPos);
        int signal = 0;

        if (rearState.hasAnalogOutputSignal()) {
            signal = rearState.getAnalogOutputSignal(level, rearPos);
        } else if (rearState.isRedstoneConductor(level, rearPos)) {
            BlockPos farPos = rearPos.relative(rearDir);
            if (level.isLoaded(farPos)) {
                BlockState farState = level.getBlockState(farPos);
                if (farState.hasAnalogOutputSignal()) {
                    signal = farState.getAnalogOutputSignal(level, farPos);
                }
            }
        }

        if (signal == 0) {
            signal = level.getSignal(rearPos, rearDir);
        }

        return Math.min(15, Math.max(0, signal));
    }

    public static int calculateMaxSideSignal(Level level, BlockPos pos, Direction facing) {
        if (level == null || pos == null || facing == null) return 0;
        Direction leftDir = facing.getClockWise();
        Direction rightDir = facing.getCounterClockWise();

        BlockPos leftPos = pos.relative(leftDir);
        BlockPos rightPos = pos.relative(rightDir);

        int leftSignal = level.isLoaded(leftPos) ? level.getSignal(leftPos, leftDir) : 0;
        int rightSignal = level.isLoaded(rightPos) ? level.getSignal(rightPos, rightDir) : 0;

        return Math.min(15, Math.max(leftSignal, rightSignal));
    }
}

