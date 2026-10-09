package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.network.RequestTelemetryPayload;
import com.alikuxac.machinemetrics.platform.Services.ClientSampler;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Function;

public class TelemetryHudRenderer {

    private static BlockPos currentHoverPos = null;
    private static Direction currentHoverFace = null;
    private static long hoverStartTimeMs = 0L;
    private static BlockPos lastSentPos = null;
    private static Direction lastSentFace = null;
    private static long lastRequestTimeMs = 0L;

    public static void renderHud(GuiGraphics graphics, ClientSampler sampler) {
        if (!KeyBindings.hudEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            if (currentHoverPos != null) {
                ClientTelemetryCache.remove(currentHoverPos);
                currentHoverPos = null;
                currentHoverFace = null;
            }
            return;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos targetPos = blockHitResult.getBlockPos();
        Direction hitFace = blockHitResult.getDirection();

        long now = System.currentTimeMillis();

        if (!targetPos.equals(currentHoverPos) || hitFace != currentHoverFace) {
            if (currentHoverPos != null) {
                ClientTelemetryCache.remove(currentHoverPos);
            }
            currentHoverPos = targetPos;
            currentHoverFace = hitFace;
            hoverStartTimeMs = now;
        }

        // Raycast Debounce (~2-3 ticks / 120ms hover before sending network packet)
        if (now - hoverStartTimeMs >= 120L) {
            if (now - lastRequestTimeMs >= 200L || !targetPos.equals(lastSentPos) || hitFace != lastSentFace) {
                lastRequestTimeMs = now;
                lastSentPos = targetPos;
                lastSentFace = hitFace;
                try {
                    if (mc.getConnection() != null) {
                        PacketDistributor.sendToServer(new RequestTelemetryPayload(targetPos, hitFace));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        TelemetryData data = ClientTelemetryCache.get(targetPos);
        if (data.isEmpty()) {
            data = sampler.sample(targetPos, hitFace);
        }

        if (data == null || data.isEmpty()) {
            return;
        }

        boolean isCompact = !Screen.hasShiftDown();

        float scale = ClientConfig.HUD_SCALE != null ? ClientConfig.HUD_SCALE.get().floatValue() : 1.0f;
        int rawX = HudPositionScreen.posX;
        int rawY = HudPositionScreen.posY;

        graphics.pose().pushPose();
        if (scale != 1.0f) {
            graphics.pose().translate(rawX, rawY, 0);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.pose().translate(-rawX, -rawY, 0);
        }

        ClientTelemetryCache.CachedHudRenderData renderData = ClientTelemetryCache.getRenderData(targetPos, data);

        if (isCompact) {
            renderCompactHud(graphics, mc, renderData, rawX, rawY);
        } else {
            renderDetailedHud(graphics, mc, renderData, rawX, rawY);
        }

        graphics.pose().popPose();
    }

    private static void renderCompactHud(GuiGraphics graphics, Minecraft mc, ClientTelemetryCache.CachedHudRenderData renderData, int x, int y) {
        int width = renderData.compactWidth();
        int height = renderData.compactHeight();
        int headerColor = renderData.headerColor();

        // Dark glassmorphism container
        graphics.fill(x, y, x + width, y + height, 0xD0101419);
        graphics.renderOutline(x, y, width, height, 0xFF000000 | headerColor);

        graphics.drawString(mc.font, renderData.compactTitleComp(), x + 6, y + 4, headerColor);
        graphics.drawString(mc.font, renderData.compactMainMetricComp(), x + 6, y + 16, renderData.compactMetricColor());

        graphics.fill(x + width - 10, y + 6, x + width - 5, y + 11, renderData.compactBadgeColor());
    }

    private static void renderDetailedHud(GuiGraphics graphics, Minecraft mc, ClientTelemetryCache.CachedHudRenderData renderData, int x, int y) {
        int width = renderData.detailedWidth();
        int height = renderData.detailedHeight();
        int headerColor = renderData.headerColor();

        // Background & Customizable Header Outline
        graphics.fill(x, y, x + width, y + height, 0xD0101419);
        graphics.renderOutline(x, y, width, height, 0xFF000000 | headerColor);

        // Header Title
        graphics.drawString(mc.font, renderData.detailedTitleComp(), x + 6, y + 4, headerColor);

        // Status Badge Tag
        graphics.drawString(mc.font, renderData.detailedStateComp(), x + width - mc.font.width(renderData.detailedStateComp()) - 6, y + 4, renderData.detailedStateColor());

        List<ClientTelemetryCache.RenderRow> rows = renderData.detailedRows();
        if (!rows.isEmpty()) {
            // Divider Line
            graphics.fill(x + 4, y + 15, x + width - 4, y + 16, 0x40000000 | (headerColor & 0xFFFFFF));

            // Active Metric Rows
            int lineHeight = 12;
            int currentY = y + 20;
            for (ClientTelemetryCache.RenderRow row : rows) {
                graphics.drawString(mc.font, row.labelComp(), x + 6, currentY, 0xAAAAAA);
                if (row.valueComp() != null && !row.valueComp().getString().isEmpty()) {
                    graphics.drawString(mc.font, row.valueComp(), x + width - row.valueWidth() - 6, currentY, row.color());
                }
                currentY += lineHeight;
            }
        }
    }
}
