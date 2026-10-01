package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.config.TelemetryConfig;
import com.alikuxac.machinemetrics.network.RequestTelemetryPayload;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
        int itemsColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ITEMS, 0xFFD54F);
        int fluidsColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_FLUIDS, 0x4FC3F7);
        int chemicalsColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_CHEMICALS, 0xAEEA00);
        int positiveEnergyColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ENERGY_POSITIVE, 0x55FF55);
        int negativeEnergyColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ENERGY_NEGATIVE, 0xFF5252);
        int neutralEnergyColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ENERGY_NEUTRAL, 0xE040FB);
        int idleColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_STATE_IDLE, 0xAAAAAA);

        int metricColor = 0xFFFFFF;

        if (data.itemThroughput() > 0f || (data.hasItems() && !data.hasFluids() && !data.hasChemicals() && !data.hasEnergy())) {
            mainMetric = formatItemRate(data.itemThroughput());
            metricColor = itemsColor;
        } else if (data.fluidThroughput() > 0f || (data.hasFluids() && !data.hasChemicals() && !data.hasEnergy())) {
            mainMetric = formatFluidRate(data.fluidThroughput());
            metricColor = fluidsColor;
        } else if (data.chemicalThroughput() > 0f || (data.hasChemicals() && !data.hasEnergy())) {
            mainMetric = formatChemicalRate(data.chemicalThroughput());
            metricColor = chemicalsColor;
        } else if (data.hasEnergy() || data.energyDelta() != 0f) {
            mainMetric = formatEnergyRate(data.energyDelta());
            metricColor = data.energyDelta() > 0 ? positiveEnergyColor : (data.energyDelta() < 0 ? negativeEnergyColor : neutralEnergyColor);
        } else {
            mainMetric = Component.translatable("machinemetrics.state.idle").getString();
            metricColor = idleColor;
        }



        Component titleComp = Component.translatable("machinemetrics.hud.title");
        int titleWidth = mc.font.width(titleComp);
        int metricWidth = mc.font.width(mainMetric);
        int width = Math.max(110, Math.max(titleWidth, metricWidth) + 30);
        int height = 30;

        int headerColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_HEADER, 0x00FFCC);

        // Dark glassmorphism container
        graphics.fill(x, y, x + width, y + height, 0xD0101419);
        graphics.renderOutline(x, y, width, height, 0xFF000000 | headerColor);

        graphics.drawString(mc.font, titleComp, x + 6, y + 4, headerColor);
        graphics.drawString(mc.font, Component.literal(mainMetric), x + 6, y + 16, metricColor);

        int badgeColor = getStatusColor(data.bottleneckState());
        graphics.fill(x + width - 10, y + 6, x + width - 5, y + 11, badgeColor);
    }

    private static void renderDetailedHud(GuiGraphics graphics, Minecraft mc, TelemetryData data, int x, int y) {
        List<MetricLine> lines = new ArrayList<>();

        int itemsColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ITEMS, 0xFFD54F);
        int fluidsColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_FLUIDS, 0x4FC3F7);
        int chemicalsColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_CHEMICALS, 0xAEEA00);
        int positiveEnergyColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ENERGY_POSITIVE, 0x69F0AE);
        int negativeEnergyColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ENERGY_NEGATIVE, 0xFF5252);
        int neutralEnergyColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_ENERGY_NEUTRAL, 0xE040FB);
        int sideColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_SIDES, 0x69F0AE);
        int headerColor = TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_HEADER, 0x00FFCC);

        if (data.bottleneckState() != TelemetryData.BottleneckState.IDLE) {

            if (data.hasItems() || data.itemThroughput() > 0f) {
                lines.add(new MetricLine(Component.translatable("machinemetrics.metrics.items").getString(), formatItemRate(data.itemThroughput()), itemsColor));
            }

            if (data.hasFluids() || data.fluidThroughput() > 0f) {
                lines.add(new MetricLine(Component.translatable("machinemetrics.metrics.fluids").getString(), formatFluidRate(data.fluidThroughput()), fluidsColor));
            }

            if (data.hasChemicals() || data.chemicalThroughput() > 0f) {
                lines.add(new MetricLine(Component.translatable("machinemetrics.metrics.chemicals").getString(), formatChemicalRate(data.chemicalThroughput()), chemicalsColor));
            }

            if (data.hasEnergy() || data.energyDelta() != 0f) {


                int energyColor = data.energyDelta() > 0 ? positiveEnergyColor : (data.energyDelta() < 0 ? negativeEnergyColor : neutralEnergyColor);
                lines.add(new MetricLine(Component.translatable("machinemetrics.metrics.energy").getString(), formatEnergyRate(data.energyDelta()), energyColor));
            }
        }

        if (data.hasActiveSides()) {
            Direction[] directions = Direction.values();
            List<Direction> activeItemSides = new ArrayList<>();
            List<Direction> activeFluidSides = new ArrayList<>();
            List<Direction> activeChemicalSides = new ArrayList<>();
            Float firstItemRate = null;
            Float firstFluidRate = null;
            Float firstChemicalRate = null;
            boolean allItemRatesEqual = true;
            boolean allFluidRatesEqual = true;
            boolean allChemicalRatesEqual = true;

            for (Direction dir : directions) {
                float itemVal = data.getItemSideRate(dir);
                float fluidVal = data.getFluidSideRate(dir);
                float chemVal = data.getChemicalSideRate(dir);

                if (itemVal > 0.01f) {
                    activeItemSides.add(dir);
                    if (firstItemRate == null) {
                        firstItemRate = itemVal;
                    } else if (Math.abs(firstItemRate - itemVal) > 0.01f) {
                        allItemRatesEqual = false;
                    }
                }

                if (fluidVal > 0.01f) {
                    activeFluidSides.add(dir);
                    if (firstFluidRate == null) {
                        firstFluidRate = fluidVal;
                    } else if (Math.abs(firstFluidRate - fluidVal) > 0.01f) {
                        allFluidRatesEqual = false;
                    }
                }

                if (chemVal > 0.01f) {
                    activeChemicalSides.add(dir);
                    if (firstChemicalRate == null) {
                        firstChemicalRate = chemVal;
                    } else if (Math.abs(firstChemicalRate - chemVal) > 0.01f) {
                        allChemicalRatesEqual = false;
                    }
                }
            }

            boolean canGroupAll = (activeItemSides.isEmpty() || (activeItemSides.size() > 1 && allItemRatesEqual)) &&
                                   (activeFluidSides.isEmpty() || (activeFluidSides.size() > 1 && allFluidRatesEqual)) &&
                                   (activeChemicalSides.isEmpty() || (activeChemicalSides.size() > 1 && allChemicalRatesEqual)) &&
                                   (!activeItemSides.isEmpty() || !activeFluidSides.isEmpty() || !activeChemicalSides.isEmpty());

            if (canGroupAll) {
                String allSidesLabel = Component.translatable("machinemetrics.metrics.all_sides").getString();
                StringBuilder summaryText = new StringBuilder("  └ [").append(allSidesLabel).append("] ");
                if (firstItemRate != null) {
                    summaryText.append(String.format("📦 %s ", formatItemRate(firstItemRate)));
                }
                if (firstFluidRate != null) {
                    summaryText.append(String.format("💧 %s ", formatFluidRate(firstFluidRate)));
                }
                if (firstChemicalRate != null) {
                    summaryText.append(String.format("🧪 %s", formatChemicalRate(firstChemicalRate)));
                }
                lines.add(new MetricLine(summaryText.toString().trim(), "", sideColor));
            } else {
                for (Direction dir : directions) {
                    float itemVal = data.getItemSideRate(dir);
                    float fluidVal = data.getFluidSideRate(dir);
                    float chemVal = data.getChemicalSideRate(dir);

                    if (itemVal > 0.01f || fluidVal > 0.01f || chemVal > 0.01f) {
                        StringBuilder sideText = new StringBuilder();
                        sideText.append("  └ [").append(dir.getName().toUpperCase()).append("] ");
                        
                        if (itemVal > 0.01f) {
                            sideText.append(String.format("📦 %s ", formatItemRate(itemVal)));
                        }
                        if (fluidVal > 0.01f) {
                            sideText.append(String.format("💧 %s ", formatFluidRate(fluidVal)));
                        }
                        if (chemVal > 0.01f) {
                            sideText.append(String.format("🧪 %s", formatChemicalRate(chemVal)));
                        }

                        lines.add(new MetricLine(sideText.toString().trim(), "", sideColor));
                    }
                }
            }
        }

        Component titleComp = Component.translatable("machinemetrics.hud.detailed_title");

        Component stateComp = getStateComponent(data.bottleneckState());

        int headerLineWidth = mc.font.width(titleComp) + mc.font.width(stateComp) + 20;

        int maxRowWidth = 0;
        for (MetricLine line : lines) {
            int rowW = mc.font.width(line.label() + ":") + mc.font.width(line.value()) + 20;
            if (rowW > maxRowWidth) {
                maxRowWidth = rowW;
            }
        }

        int width = Math.max(140, Math.max(headerLineWidth, maxRowWidth));
        int lineHeight = 12;
        int height = lines.isEmpty() ? 22 : 24 + (lines.size() * lineHeight);

        // Background & Customizable Header Outline
        graphics.fill(x, y, x + width, y + height, 0xD0101419);
        graphics.renderOutline(x, y, width, height, 0xFF000000 | headerColor);

        // Header Title
        graphics.drawString(mc.font, titleComp, x + 6, y + 4, headerColor);

        // Status Badge Tag
        int stateColor = getStatusColor(data.bottleneckState());
        graphics.drawString(mc.font, stateComp, x + width - mc.font.width(stateComp) - 6, y + 4, stateColor);

        if (!lines.isEmpty()) {
            // Divider Line
            graphics.fill(x + 4, y + 15, x + width - 4, y + 16, 0x40000000 | (headerColor & 0xFFFFFF));

            // Active Metric Rows
            int currentY = y + 20;
            for (MetricLine line : lines) {
                graphics.drawString(mc.font, Component.literal(line.label() + ":"), x + 6, currentY, 0xAAAAAA);
                graphics.drawString(mc.font, Component.literal(line.value()), x + width - mc.font.width(line.value()) - 6, currentY, line.color());
                currentY += lineHeight;
            }
        }
    }

    private static Component getStateComponent(TelemetryData.BottleneckState state) {
        return switch (state) {
            case OPTIMAL -> Component.translatable("machinemetrics.state.optimal");
            case STARVED -> Component.translatable("machinemetrics.state.starved");
            case CLOGGED -> Component.translatable("machinemetrics.state.clogged");
            case IDLE -> Component.translatable("machinemetrics.state.idle");
        };
    }

    private static int getStatusColor(TelemetryData.BottleneckState state) {
        return switch (state) {
            case OPTIMAL -> TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_STATE_OPTIMAL, 0x55FF55);
            case STARVED -> TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_STATE_STARVED, 0xFFFF55);
            case CLOGGED -> TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_STATE_CLOGGED, 0xFF5555);
            case IDLE -> TelemetryConfig.parseHexColor(TelemetryConfig.COLOR_STATE_IDLE, 0xAAAAAA);
        };
    }

    private static String formatItemRate(float rate) {
        if (rate <= 0.001f) return Component.translatable("machinemetrics.metrics.none").getString();
        if (rate < 1.0f) {
            return String.format("%.2f /s", rate);
        } else {
            return String.format("%.1f /s", rate);
        }
    }

    private static String formatEnergyRate(float rate) {
        float abs = Math.abs(rate);
        if (abs <= 0.001f) return Component.translatable("machinemetrics.metrics.none").getString();
        String prefix = rate > 0 ? "+" : (rate < 0 ? "-" : "");
        if (abs >= 1_000_000f) {
            return String.format("%s%.2f MFE/t", prefix, abs / 1_000_000f);
        } else if (abs >= 1_000f) {
            return String.format("%s%.1f kFE/t", prefix, abs / 1_000f);
        } else if (abs < 1.0f) {
            return String.format("%s%.2f FE/t", prefix, abs);
        } else {
            return String.format("%s%.0f FE/t", prefix, abs);
        }
    }

    private static String formatFluidRate(float rate) {
        if (rate <= 0.001f) return Component.translatable("machinemetrics.metrics.none").getString();
        if (rate >= 1_000f) {
            return String.format("%.2f B/s", rate / 1_000f);
        } else if (rate < 1.0f) {
            return String.format("%.2f mB/s", rate);
        } else {
            return String.format("%.1f mB/s", rate);
        }
    }

    private static String formatChemicalRate(float rate) {
        if (rate <= 0.001f) return Component.translatable("machinemetrics.metrics.none").getString();
        if (rate >= 1_000f) {
            return String.format("%.2f B/s", rate / 1_000f);
        } else if (rate < 1.0f) {
            return String.format("%.2f mB/s", rate);
        } else {
            return String.format("%.1f mB/s", rate);
        }
    }

    private record MetricLine(String label, String value, int color) {}
}

