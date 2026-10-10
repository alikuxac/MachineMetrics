package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.compat.infohud.InfoHudDetector;
import com.alikuxac.machinemetrics.compat.mekanism.MekanismChemicalInspector;
import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.network.RequestTelemetryPayload;
import com.alikuxac.machinemetrics.platform.Services.ClientSampler;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TelemetryHudRenderer {

    public record HudLine(Component text, int color) {}

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

        if (ClientConfig.HUD_ENABLED != null && !ClientConfig.HUD_ENABLED.get()) {
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

        // Raycast Debounce (~120ms hover before sending network packet)
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

        String machineName = MetricResolver.getBlockOrMachineName(mc.level, targetPos);
        List<HudLine> lines = buildHudLines(machineName, data);

        if (lines.isEmpty()) {
            return;
        }

        renderAdaptiveHud(graphics, mc, lines);
    }

    public static List<HudLine> buildHudLines(String machineName, TelemetryData data) {
        List<HudLine> lines = new ArrayList<>();

        boolean isCompact = ClientConfig.COMPACT_MODE != null && ClientConfig.COMPACT_MODE.get();
        if (isCompact) {
            String template = ClientConfig.COMPACT_TEMPLATE != null ? ClientConfig.COMPACT_TEMPLATE.get() : "{name} | {speed} RPM {dir} | {stress}/{cap} SU ({load_pct}%) | {state}";
            String resolved = MetricResolver.resolveTemplate(template, machineName, data);
            int headerColor = ClientConfig.parseHexColor(ClientConfig.COLOR_HEADER, 0x00FFCC);
            lines.add(new HudLine(Component.literal(resolved), headerColor));
            return lines;
        }

        int headerColor = ClientConfig.parseHexColor(ClientConfig.COLOR_HEADER, 0x00FFCC);
        int itemsColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ITEMS, 0xFFD54F);
        int fluidsColor = ClientConfig.parseHexColor(ClientConfig.COLOR_FLUIDS, 0x4FC3F7);
        int chemicalsColor = ClientConfig.parseHexColor(ClientConfig.COLOR_CHEMICALS, 0xAEEA00);
        int kineticColor = ClientConfig.parseHexColor(ClientConfig.COLOR_KINETIC, 0xFF9E59);
        int positiveEnergyColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ENERGY_POSITIVE, 0x69F0AE);
        int negativeEnergyColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ENERGY_NEGATIVE, 0xFF5252);
        int neutralEnergyColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ENERGY_NEUTRAL, 0xE040FB);

        boolean showTitleConfig = ClientConfig.SHOW_MACHINE_TITLE == null || ClientConfig.SHOW_MACHINE_TITLE.get();
        boolean autoHideTitle = ClientConfig.AUTO_HIDE_TITLE_WITH_INFO_MODS == null || ClientConfig.AUTO_HIDE_TITLE_WITH_INFO_MODS.get();
        boolean isInfoHudPresent = InfoHudDetector.isInfoHudPresent();
        boolean showTitle = showTitleConfig && (!autoHideTitle || !isInfoHudPresent);
        boolean showBadge = ClientConfig.SHOW_STATUS_BADGE == null || ClientConfig.SHOW_STATUS_BADGE.get();

        if (showTitle || showBadge) {
            Component headerComponent;
            Component nameComp = Component.literal(showTitle ? machineName : "");
            if (showBadge && data.bottleneckState() != null) {
                Component stateComp = MetricResolver.getStateComponent(data.bottleneckState());
                if (showTitle) {
                    headerComponent = Component.empty()
                            .append(nameComp)
                            .append(Component.literal("  ["))
                            .append(stateComp)
                            .append(Component.literal("]"));
                } else {
                    headerComponent = Component.empty()
                            .append(Component.literal("["))
                            .append(stateComp)
                            .append(Component.literal("]"));
                }
            } else {
                headerComponent = nameComp;
            }
            if (!headerComponent.getString().isEmpty()) {
                lines.add(new HudLine(headerComponent, headerColor));
            }
        }

        Component disabledServerComp = Component.translatable("machinemetrics.metrics.disabled_by_server");
        int disabledColor = 0xFF6666;

        boolean isCreateInstalled = CreateKineticInspector.isCreateLoaded();
        if (isCreateInstalled) {
            boolean kineticDisabledByServer = data.isKineticDisabledByServer();
            boolean hasKinetic = data.hasKinetic() || Math.abs(data.kineticSpeed()) > 0.001f || data.kineticCapacity() > 0.001f;
            if (kineticDisabledByServer) {
                boolean showSpeed = ClientConfig.SHOW_SPEED == null || ClientConfig.SHOW_SPEED.get();
                if (showSpeed) {
                    Component speedComp = Component.translatable("machinemetrics.metrics.speed")
                            .append(Component.literal(": [")).append(disabledServerComp).append(Component.literal("]"));
                    lines.add(new HudLine(speedComp, disabledColor));
                }
            } else if (hasKinetic) {
                boolean showSpeed = ClientConfig.SHOW_SPEED == null || ClientConfig.SHOW_SPEED.get();
                boolean showDir = ClientConfig.SHOW_ROTATION_DIRECTION == null || ClientConfig.SHOW_ROTATION_DIRECTION.get();

                if (showSpeed) {
                    String dirSuffix = "";
                    if (showDir) {
                        dirSuffix = " " + MetricResolver.resolveRotationDirection(data.kineticDirection(), data.kineticSpeed());
                    }
                    Component speedComp = Component.translatable(
                            "machinemetrics.metrics.speed"
                    ).append(Component.literal(String.format(": %.1f %s%s",
                            Math.abs(data.kineticSpeed()),
                            Component.translatable("machinemetrics.metrics.unit.rpm").getString(),
                            dirSuffix)));
                    lines.add(new HudLine(speedComp, kineticColor));
                }

                boolean showNetworkStress = ClientConfig.SHOW_NETWORK_STRESS == null || ClientConfig.SHOW_NETWORK_STRESS.get();
                if (showNetworkStress) {
                    float stress = data.kineticStress();
                    float cap = data.kineticCapacity();
                    String suUnit = Component.translatable("machinemetrics.metrics.unit.su").getString();
                    String stressStr;
                    if (cap > 0.001f) {
                        float pct = (stress / cap) * 100f;
                        stressStr = String.format(": %s / %s %s (%.0f%%)",
                                MetricResolver.formatCompactNumber(stress),
                                MetricResolver.formatCompactNumber(cap),
                                suUnit,
                                pct);
                    } else if (stress > 0.001f) {
                        stressStr = String.format(": %s %s", MetricResolver.formatCompactNumber(stress), suUnit);
                    } else {
                        stressStr = String.format(": 0 %s", suUnit);
                    }
                    Component networkComp = Component.translatable("machinemetrics.metrics.network").append(Component.literal(stressStr));
                    int stressColor = data.bottleneckState() == TelemetryData.BottleneckState.OVERLOADED ?
                            ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_OVERLOADED, 0xFF3333) : kineticColor;
                    lines.add(new HudLine(networkComp, stressColor));
                }

                boolean showHeadroom = ClientConfig.SHOW_HEADROOM == null || ClientConfig.SHOW_HEADROOM.get();
                if (showHeadroom && data.kineticCapacity() > 0.001f) {
                    float headroom = Math.max(0f, data.kineticCapacity() - data.kineticStress());
                    String suUnit = Component.translatable("machinemetrics.metrics.unit.su").getString();
                    Component headroomComp = Component.translatable("machinemetrics.metrics.headroom")
                            .append(Component.literal(String.format(": %s %s", MetricResolver.formatHeadroom(headroom), suUnit)));
                    lines.add(new HudLine(headroomComp, kineticColor));
                }

                boolean showLocalImpact = ClientConfig.SHOW_LOCAL_IMPACT != null && ClientConfig.SHOW_LOCAL_IMPACT.get();
                if (showLocalImpact && data.kineticBlockStress() > 0.001f) {
                    String suUnit = Component.translatable("machinemetrics.metrics.unit.su").getString();
                    Component localStressComp = Component.translatable("machinemetrics.metrics.local_stress")
                            .append(Component.literal(String.format(": %s %s", MetricResolver.formatCompactNumber(data.kineticBlockStress()), suUnit)));
                    lines.add(new HudLine(localStressComp, kineticColor));
                }
            }
        }

        boolean showItems = ClientConfig.SHOW_ITEMS_THROUGHPUT == null || ClientConfig.SHOW_ITEMS_THROUGHPUT.get();
        if (showItems) {
            if (data.isItemsDisabledByServer()) {
                Component itemComp = Component.translatable("machinemetrics.metrics.items")
                        .append(Component.literal(": [")).append(disabledServerComp).append(Component.literal("]"));
                lines.add(new HudLine(itemComp, disabledColor));
            } else if (data.hasItems() || data.itemThroughput() > 0.001f) {
                String itemVal = MetricResolver.formatItemRate(data.itemThroughput());
                Component itemComp = Component.translatable("machinemetrics.metrics.items")
                        .append(Component.literal(": " + itemVal));
                lines.add(new HudLine(itemComp, itemsColor));
            }
        }

        boolean showFluids = ClientConfig.SHOW_FLUIDS_THROUGHPUT == null || ClientConfig.SHOW_FLUIDS_THROUGHPUT.get();
        if (showFluids) {
            if (data.isFluidsDisabledByServer()) {
                Component fluidComp = Component.translatable("machinemetrics.metrics.fluids")
                        .append(Component.literal(": [")).append(disabledServerComp).append(Component.literal("]"));
                lines.add(new HudLine(fluidComp, disabledColor));
            } else if (data.hasFluids() || data.fluidThroughput() > 0.001f) {
                String fluidVal = MetricResolver.formatFluidRate(data.fluidThroughput());
                Component fluidComp = Component.translatable("machinemetrics.metrics.fluids")
                        .append(Component.literal(": " + fluidVal));
                lines.add(new HudLine(fluidComp, fluidsColor));
            }
        }

        boolean isMekanismInstalled = MekanismChemicalInspector.isMekanismLoaded();
        if (isMekanismInstalled) {
            boolean showChemicals = ClientConfig.SHOW_CHEMICALS_THROUGHPUT == null || ClientConfig.SHOW_CHEMICALS_THROUGHPUT.get();
            if (showChemicals) {
                if (data.isChemicalsDisabledByServer()) {
                    Component chemComp = Component.translatable("machinemetrics.metrics.chemicals")
                            .append(Component.literal(": [")).append(disabledServerComp).append(Component.literal("]"));
                    lines.add(new HudLine(chemComp, disabledColor));
                } else if (data.hasChemicals() || data.chemicalThroughput() > 0.001f) {
                    String chemVal = MetricResolver.formatFluidRate(data.chemicalThroughput());
                    Component chemComp = Component.translatable("machinemetrics.metrics.chemicals")
                            .append(Component.literal(": " + chemVal));
                    lines.add(new HudLine(chemComp, chemicalsColor));
                }
            }
        }

        boolean showEnergy = ClientConfig.SHOW_ENERGY_FE == null || ClientConfig.SHOW_ENERGY_FE.get();
        if (showEnergy) {
            if (data.isEnergyDisabledByServer()) {
                Component energyComp = Component.translatable("machinemetrics.metrics.energy")
                        .append(Component.literal(": [")).append(disabledServerComp).append(Component.literal("]"));
                lines.add(new HudLine(energyComp, disabledColor));
            } else if (data.hasEnergy() || data.energyDelta() != 0f) {
                float delta = data.energyDelta();
                int color = delta > 0 ? positiveEnergyColor : (delta < 0 ? negativeEnergyColor : neutralEnergyColor);
                String prefix = delta > 0 ? "+" : "";
                String feUnit = Component.translatable("machinemetrics.metrics.unit.fe_per_tick").getString();
                Component energyComp = Component.translatable("machinemetrics.metrics.energy")
                        .append(Component.literal(String.format(": %s%s %s", prefix, MetricResolver.formatCompactNumber(delta), feUnit)));
                lines.add(new HudLine(energyComp, color));
            }
        }

        boolean showSides = ClientConfig.SHOW_SIDES == null || ClientConfig.SHOW_SIDES.get();
        if (showSides && !data.isSidesDisabledByServer() && data.hasActiveSides()) {
            int sideColor = ClientConfig.parseHexColor(ClientConfig.COLOR_SIDES, 0x69F0AE);
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
                    summaryText.append(String.format("📦 %s ", MetricResolver.formatItemRate(firstItemRate)));
                }
                if (firstFluidRate != null) {
                    summaryText.append(String.format("💧 %s ", MetricResolver.formatFluidRate(firstFluidRate)));
                }
                if (firstChemicalRate != null && isMekanismInstalled) {
                    summaryText.append(String.format("🧪 %s", MetricResolver.formatChemicalRate(firstChemicalRate)));
                }
                String labelStr = summaryText.toString().trim() + ":";
                lines.add(new HudLine(Component.literal(labelStr), sideColor));
            } else {
                for (Direction dir : directions) {
                    float itemVal = data.getItemSideRate(dir);
                    float fluidVal = data.getFluidSideRate(dir);
                    float chemVal = data.getChemicalSideRate(dir);

                    if (itemVal > 0.01f || fluidVal > 0.01f || (chemVal > 0.01f && isMekanismInstalled)) {
                        StringBuilder sideText = new StringBuilder();
                        sideText.append("  └ [").append(dir.getName().toUpperCase()).append("] ");

                        if (itemVal > 0.01f) {
                            sideText.append(String.format("📦 %s ", MetricResolver.formatItemRate(itemVal)));
                        }
                        if (fluidVal > 0.01f) {
                            sideText.append(String.format("💧 %s ", MetricResolver.formatFluidRate(fluidVal)));
                        }
                        if (chemVal > 0.01f && isMekanismInstalled) {
                            sideText.append(String.format("🧪 %s", MetricResolver.formatChemicalRate(chemVal)));
                        }
                        String labelStr = sideText.toString().trim() + ":";
                        lines.add(new HudLine(Component.literal(labelStr), sideColor));
                    }
                }
            }
        }

        return lines;
    }

    public static int[] calculateRenderCoordinates(int boxWidth, int boxHeight, int screenWidth, int screenHeight) {
        ClientConfig.HudAnchor anchor = ClientConfig.HUD_ANCHOR != null ? ClientConfig.HUD_ANCHOR.get() : ClientConfig.HudAnchor.TOP_LEFT;
        int offsetX = ClientConfig.HUD_X_OFFSET != null ? ClientConfig.HUD_X_OFFSET.get() : 10;
        int offsetY = ClientConfig.HUD_Y_OFFSET != null ? ClientConfig.HUD_Y_OFFSET.get() : 10;

        return calculatePositionForAnchor(anchor, offsetX, offsetY, boxWidth, boxHeight, screenWidth, screenHeight);
    }

    public static int[] calculatePositionForAnchor(ClientConfig.HudAnchor anchor, int offsetX, int offsetY, int boxWidth, int boxHeight, int screenWidth, int screenHeight) {
        int x;
        int y;
        switch (anchor) {
            case TOP_LEFT -> {
                x = offsetX;
                y = offsetY;
            }
            case TOP_RIGHT -> {
                x = screenWidth - boxWidth - offsetX;
                y = offsetY;
            }
            case BOTTOM_LEFT -> {
                x = offsetX;
                y = screenHeight - boxHeight - offsetY;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - boxWidth - offsetX;
                y = screenHeight - boxHeight - offsetY;
            }
            case CROSSHAIR -> {
                x = (screenWidth - boxWidth) / 2 + offsetX;
                y = (screenHeight - boxHeight) / 2 + offsetY;
            }
            default -> {
                x = offsetX;
                y = offsetY;
            }
        }
        return new int[]{x, y};
    }

    private static void renderAdaptiveHud(GuiGraphics graphics, Minecraft mc, List<HudLine> lines) {
        Font font = mc.font;
        int maxLineWidth = 0;
        for (HudLine line : lines) {
            int w = font.width(line.text());
            if (w > maxLineWidth) {
                maxLineWidth = w;
            }
        }

        int paddingX = 8;
        int paddingY = 6;
        int lineHeight = font.lineHeight + 3;

        int boxWidth = maxLineWidth + (paddingX * 2);
        int boxHeight = (lines.size() * lineHeight) + (paddingY * 2) - 2;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int[] coords = calculateRenderCoordinates(boxWidth, boxHeight, screenWidth, screenHeight);
        int renderX = coords[0];
        int renderY = coords[1];

        float scale = ClientConfig.HUD_SCALE != null ? ClientConfig.HUD_SCALE.get().floatValue() : 1.0f;

        graphics.pose().pushPose();
        if (scale != 1.0f) {
            graphics.pose().translate(renderX, renderY, 0);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.pose().translate(-renderX, -renderY, 0);
        }

        int bgColor = ClientConfig.parseHexColor(ClientConfig.BACKGROUND_COLOR, 0x90000000);
        int borderColor = ClientConfig.parseHexColor(ClientConfig.BORDER_COLOR, 0xFF00FFCC);

        // Backdrop & Outline
        graphics.fill(renderX, renderY, renderX + boxWidth, renderY + boxHeight, bgColor);
        graphics.renderOutline(renderX, renderY, boxWidth, boxHeight, borderColor);

        // Render sequential lines
        int currentY = renderY + paddingY;
        for (HudLine line : lines) {
            graphics.drawString(font, line.text(), renderX + paddingX, currentY, line.color());
            currentY += lineHeight;
        }

        graphics.pose().popPose();
    }
}
