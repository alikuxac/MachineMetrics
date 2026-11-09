package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PinnedTargetManager {

    private static BlockPos pinnedPos = null;
    private static Direction pinnedFace = null;
    private static ResourceKey<Level> pinnedDimension = null;
    private static Block pinnedBlock = null;
    private static String pinnedMachineName = null;
    private static long lastPollTimeMs = 0L;
    private static final long POLL_INTERVAL_MS = 200L;

    private PinnedTargetManager() {}

    public static boolean isPinned() {
        return pinnedPos != null;
    }

    public static boolean isPinned(BlockPos pos) {
        return pos != null && pos.equals(pinnedPos);
    }

    public static BlockPos getPinnedPos() {
        return pinnedPos;
    }

    public static Direction getPinnedFace() {
        return pinnedFace;
    }

    public static String getPinnedMachineName() {
        return pinnedMachineName;
    }

    public static void pin(BlockPos pos, Direction face, Level level, String machineName) {
        if (pos == null || level == null) return;
        pinnedPos = pos;
        pinnedFace = face != null ? face : Direction.UP;
        pinnedDimension = level.dimension();
        pinnedBlock = level.getBlockState(pos).getBlock();
        pinnedMachineName = machineName != null ? machineName : MetricResolver.getBlockOrMachineName(level, pos);
        lastPollTimeMs = 0L;
    }

    public static void unpin() {
        pinnedPos = null;
        pinnedFace = null;
        pinnedDimension = null;
        pinnedBlock = null;
        pinnedMachineName = null;
        lastPollTimeMs = 0L;
    }

    public static void togglePinCurrentTarget(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return;

        HitResult hitResult = InspectionGate.performRaycast(mc);
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hitResult;
            BlockPos targetPos = bhr.getBlockPos();
            if (isPinned(targetPos)) {
                unpin();
                mc.player.sendOverlayMessage(
                        Component.translatable("machinemetrics.pin.unpinned").withStyle(ChatFormatting.YELLOW)
                );
            } else {
                String name = MetricResolver.getBlockOrMachineName(mc.level, targetPos);
                pin(targetPos, bhr.getDirection(), mc.level, name);
                mc.player.sendOverlayMessage(
                        Component.translatable("machinemetrics.pin.pinned", name).withStyle(ChatFormatting.GOLD)
                );
            }
        } else if (isPinned()) {
            unpin();
            mc.player.sendOverlayMessage(
                    Component.translatable("machinemetrics.pin.unpinned").withStyle(ChatFormatting.YELLOW)
            );
        } else {
            mc.player.sendOverlayMessage(
                    Component.translatable("machinemetrics.pin.no_target").withStyle(ChatFormatting.RED)
            );
        }
    }

    public static void tick(Minecraft mc) {
        if (!isPinned() || mc == null || mc.level == null || mc.player == null) return;

        if (pinnedDimension != null && mc.level.dimension() != pinnedDimension) {
            unpin();
            mc.player.sendOverlayMessage(
                    Component.translatable("machinemetrics.pin.lost_dimension").withStyle(ChatFormatting.YELLOW)
            );
            return;
        }

        if (!mc.level.hasChunkAt(pinnedPos)) {
            unpin();
            mc.player.sendOverlayMessage(
                    Component.translatable("machinemetrics.pin.lost_out_of_range").withStyle(ChatFormatting.YELLOW)
            );
            return;
        }

        BlockState state = mc.level.getBlockState(pinnedPos);
        if (state.isAir() || (pinnedBlock != null && !state.is(pinnedBlock))) {
            unpin();
            mc.player.sendOverlayMessage(
                    Component.translatable("machinemetrics.pin.lost_destroyed").withStyle(ChatFormatting.YELLOW)
            );
            return;
        }

        int renderDist = mc.options.renderDistance().get();
        double maxDist = renderDist * 16.0;
        if (mc.player.distanceToSqr(Vec3.atCenterOf(pinnedPos)) > maxDist * maxDist) {
            unpin();
            mc.player.sendOverlayMessage(
                    Component.translatable("machinemetrics.pin.lost_out_of_range").withStyle(ChatFormatting.YELLOW)
            );
        }
    }

    public static void pollIfDue(Minecraft mc) {
        if (!isPinned() || mc == null || mc.getConnection() == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPollTimeMs >= POLL_INTERVAL_MS) {
            lastPollTimeMs = now;
            try {
                if (Services.NETWORK_SENDER != null) {
                    Services.NETWORK_SENDER.sendRequest(pinnedPos, pinnedFace);
                }
            } catch (Exception ignored) {
            }
        }
    }
}

