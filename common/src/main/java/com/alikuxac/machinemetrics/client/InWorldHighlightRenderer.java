package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class InWorldHighlightRenderer {

    private static final int COLOR_OPTIMAL = 0x55FF55;
    private static final int COLOR_IDLE = 0xFFFF55;
    private static final int COLOR_CLOGGED = 0xFF5555;
    private static final int COLOR_STARVED = 0xFFAA00;
    private static final int COLOR_OVERLOADED = 0xFF3333;
    private static final float OUTLINE_ALPHA = 0.4F;

    private InWorldHighlightRenderer() {
    }

    public static boolean renderHighlight(PoseStack poseStack, MultiBufferSource buffers, Camera camera, BlockHitResult target) {
        if (ClientConfig.ENABLE_IN_WORLD_BOUNDING_BOX == null || !ClientConfig.ENABLE_IN_WORLD_BOUNDING_BOX.get()) {
            return false;
        }
        if (poseStack == null || buffers == null || camera == null || target == null) {
            return false;
        }

        BlockPos pos = target.getBlockPos();
        ClientTelemetryCache.Entry entry = ClientTelemetryCache.getCacheEntry(pos);
        if (entry == null || entry.data() == null || entry.data().isEmpty()) {
            return false;
        }

        int argb = colorFor(entry.data().bottleneckState());

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        Entity entity = camera.getEntity();
        VoxelShape shape = state.getShape(level, pos, CollisionContext.of(entity));
        Vec3 cameraPos = camera.getPosition();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        renderShape(
                poseStack,
                consumer,
                shape,
                pos.getX() - cameraPos.x,
                pos.getY() - cameraPos.y,
                pos.getZ() - cameraPos.z,
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                OUTLINE_ALPHA
        );
        return true;
    }

    private static int colorFor(TelemetryData.BottleneckState state) {
        if (state == null) {
            return COLOR_IDLE;
        }
        return switch (state) {
            case OPTIMAL -> COLOR_OPTIMAL;
            case STARVED -> COLOR_STARVED;
            case CLOGGED -> COLOR_CLOGGED;
            case OVERLOADED -> COLOR_OVERLOADED;
            case IDLE -> COLOR_IDLE;
        };
    }

    private static void renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
                                    double x, double y, double z, float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
            float dx = (float) (x1 - x0);
            float dy = (float) (y1 - y0);
            float dz = (float) (z1 - z0);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            consumer.addVertex(pose, (float) (x0 + x), (float) (y0 + y), (float) (z0 + z))
                    .setColor(red, green, blue, alpha)
                    .setNormal(pose, dx, dy, dz);
            consumer.addVertex(pose, (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .setColor(red, green, blue, alpha)
                    .setNormal(pose, dx, dy, dz);
        });
    }
}
