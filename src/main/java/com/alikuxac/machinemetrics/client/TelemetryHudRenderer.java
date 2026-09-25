package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import com.alikuxac.machinemetrics.config.TelemetryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.alikuxac.machinemetrics.network.RequestTelemetryPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TelemetryHudRenderer {

    private static BlockPos currentHoverPos = null;
    private static long hoverStartTimeMs = 0L;
    private static BlockPos lastSentPos = null;
    private static long lastRequestTimeMs = 0L;

    public static void renderHud(GuiGraphics graphics, Function<BlockPos, TelemetryData> sampler) {
        if (!KeyBindings.hudEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            currentHoverPos = null;
            return;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos targetPos = blockHitResult.getBlockPos();

        long now = System.currentTimeMillis();

        if (!targetPos.equals(currentHoverPos)) {
            currentHoverPos = targetPos;
            hoverStartTimeMs = now;
        }

        // Raycast Debounce (~2-3 ticks / 120ms hover before sending network packet)
        if (now - hoverStartTimeMs >= 120L) {
            if (now - lastRequestTimeMs >= 200L || !targetPos.equals(lastSentPos)) {
                lastRequestTimeMs = now;
                lastSentPos = targetPos;
                try {
                    if (mc.getConnection() != null) {
                        PacketDistributor.sendToServer(new RequestTelemetryPayload(targetPos));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        TelemetryData data = ClientTelemetryCache.get(targetPos);
        if (data.isEmpty()) {
            data = sampler.apply(targetPos);
        }

        if (data == null || data.isEmpty()) {
            return;
        }

        boolean isCompact = !Screen.hasShiftDown();

        float scale = TelemetryConfig.HUD_SCALE != null ? TelemetryConfig.HUD_SCALE.get().floatValue() : 1.0f;
        int rawX = HudPositionScreen.posX;
        int rawY = HudPositionScreen.posY;

        graphics.pose().pushPose();
        if (scale != 1.0f) {
            graphics.pose().translate(rawX, rawY, 0);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.pose().translate(-rawX, -rawY, 0);
        }

        if (isCompact) {
            renderCompactHud(graphics, mc, data, rawX, rawY);
        } else {
            renderDetailedHud(graphics, mc, data, rawX, rawY);
        }

        graphics.pose().popPose();
    }

    private static void renderCompactHud(GuiGraphics graphics, Minecraft mc, TelemetryData data, int x, int y) {
        String mainMetric;
        int metricColor = 0xFFFFFF;

        if (data.itemThroughput() > 0f || (data.hasItems() && !data.hasFluids() && !data.hasEnergy())) {
            mainMetric = formatItemRate(data.itemThroughput());
        } else if (data.fluidThroughput() > 0f || (data.hasFluids() && !data.hasEnergy())) {
            mainMetric = formatFluidRate(data.fluidThroughput());
        } else if (data.hasEnergy() || data.energyDelta() != 0f) {
            mainMetric = formatEnergyRate(data.energyDelta());
            metricColor = data.energyDelta() > 0 ? 0x55FF55 : (data.energyDelta() < 0 ? 0xFF5555 : 0xAAAAAA);
        } else {
            mainMetric = "Idle";
            metricColor = 0xAAAAAA;
        }

        int titleWidth = mc.font.width("Telemetry");
        int metricWidth = mc.font.width(mainMetric);
        // Minimum width 110px, or dynamic content width + padding (24px for badge/margins)
        int width = Math.max(110, Math.max(titleWidth, metricWidth) + 30);
        int height = 30;

        // Dark glassmorphism container
        graphics.fill(x, y, x + width, y + height, 0xD0101419);
        graphics.renderOutline(x, y, width, height, 0xFF00FFCC);

        graphics.drawString(mc.font, Component.literal("Telemetry"), x + 6, y + 4, 0x00FFCC);
        graphics.drawString(mc.font, Component.literal(mainMetric), x + 6, y + 16, metricColor);

        int badgeColor = getStatusColor(data.bottleneckState());
        graphics.fill(x + width - 10, y + 6, x + width - 5, y + 11, badgeColor);
    }

    private static void renderDetailedHud(GuiGraphics graphics, Minecraft mc, TelemetryData data, int x, int y) {
        List<MetricLine> lines = new ArrayList<>();

        if (data.bottleneckState() != TelemetryData.BottleneckState.IDLE) {
            if (data.hasItems() || data.itemThroughput() > 0f) {
                lines.add(new MetricLine("Items", formatItemRate(data.itemThroughput()), 0xFFFFFF));
            }
            if (data.hasFluids() || data.fluidThroughput() > 0f) {
                lines.add(new MetricLine("Fluids", formatFluidRate(data.fluidThroughput()), 0x55FFFF));
            }
            if (data.hasEnergy() || data.energyDelta() != 0f) {
                int energyColor = data.energyDelta() > 0 ? 0x55FF55 : (data.energyDelta() < 0 ? 0xFF5555 : 0xAAAAAA);
                lines.add(new MetricLine("Energy", formatEnergyRate(data.energyDelta()), energyColor));
            }
        }

        String stateText = data.bottleneckState().name();
        int headerLineWidth = mc.font.width("Telemetry HUD") + mc.font.width(stateText) + 20;

        int maxRowWidth = 0;
        for (MetricLine line : lines) {
            int rowW = mc.font.width(line.label() + ":") + mc.font.width(line.value()) + 20;
            if (rowW > maxRowWidth) {
                maxRowWidth = rowW;
            }
        }

        // Minimum width 140px, dynamic expansion based on maximum content line
        int width = Math.max(140, Math.max(headerLineWidth, maxRowWidth));
        int lineHeight = 12;
        // Minimum height 24px (when idle), vertically responsive based on active lines count
        int height = lines.isEmpty() ? 22 : 24 + (lines.size() * lineHeight);

        // Professional dark background & cyan border
        graphics.fill(x, y, x + width, y + height, 0xD0101419);
        graphics.renderOutline(x, y, width, height, 0xFF00FFCC);

        // Header Title
        graphics.drawString(mc.font, Component.literal("Telemetry HUD"), x + 6, y + 4, 0x00FFCC);

        // Status Badge Tag
        int stateColor = getStatusColor(data.bottleneckState());
        graphics.drawString(mc.font, Component.literal(stateText), x + width - mc.font.width(stateText) - 6, y + 4, stateColor);

        if (!lines.isEmpty()) {
            // Divider Line
            graphics.fill(x + 4, y + 15, x + width - 4, y + 16, 0x4000FFCC);

            // Active Metric Rows
            int currentY = y + 20;
            for (MetricLine line : lines) {
                graphics.drawString(mc.font, Component.literal(line.label() + ":"), x + 6, currentY, 0xAAAAAA);
                graphics.drawString(mc.font, Component.literal(line.value()), x + width - mc.font.width(line.value()) - 6, currentY, line.color());
                currentY += lineHeight;
            }
        }
    }

    private static int getStatusColor(TelemetryData.BottleneckState state) {
        return switch (state) {
            case OPTIMAL -> 0x55FF55;
            case STARVED -> 0xFFFF55;
            case CLOGGED -> 0xFF5555;
            case IDLE -> 0xAAAAAA;
        };
    }

    private static String formatItemRate(float rate) {
        if (rate <= 0f) return "0.0 /s";
        if (rate < 1.0f) {
            return String.format("%.2f /s", rate);
        } else {
            return String.format("%.1f /s", rate);
        }
    }

    private static String formatEnergyRate(float rate) {
        float abs = Math.abs(rate);
        String prefix = rate > 0 ? "+" : (rate < 0 ? "-" : "");
        if (abs >= 1_000_000f) {
            return String.format("%s%.2f MFE/t", prefix, abs / 1_000_000f);
        } else if (abs >= 1_000f) {
            return String.format("%s%.1f kFE/t", prefix, abs / 1_000f);
        } else if (abs < 1.0f && abs > 0f) {
            return String.format("%s%.2f FE/t", prefix, abs);
        } else {
            return String.format("%s%.0f FE/t", prefix, abs);
        }
    }

    private static String formatFluidRate(float rate) {
        if (rate >= 1_000f) {
            return String.format("%.2f B/s", rate / 1_000f);
        } else if (rate < 1.0f && rate > 0f) {
            return String.format("%.2f mB/s", rate);
        } else {
            return String.format("%.1f mB/s", rate);
        }
    }

    private record MetricLine(String label, String value, int color) {}
}
