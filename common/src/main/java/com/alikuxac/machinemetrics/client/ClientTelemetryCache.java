package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTelemetryCache {

    public record RenderRow(Component labelComp, Component valueComp, int color, int valueWidth) {}

    public record CachedHudRenderData(
            TelemetryData rawData,
            Component compactTitleComp,
            Component compactMainMetricComp,
            int compactMetricColor,
            int compactWidth,
            int compactHeight,
            int compactBadgeColor,
            Component detailedTitleComp,
            Component detailedStateComp,
            int detailedStateColor,
            List<RenderRow> detailedRows,
            int detailedWidth,
            int detailedHeight,
            int headerColor
    ) {}

    public record Entry(
            TelemetryData data,
            CachedHudRenderData renderData,
            long receivedTimeMs,
            float smoothedNetRate,
            MetricResolver.EtaResult etaResult
    ) {}

    private static final Map<BlockPos, Entry> CACHE = new ConcurrentHashMap<>();
    private static final long EXPIRE_MS = 2000L;

    public static void putSynced(BlockPos pos, TelemetryData data) {
        if (pos != null && data != null) {
            Entry existing = CACHE.get(pos);
            boolean hasExisting = existing != null && (System.currentTimeMillis() - existing.receivedTimeMs < EXPIRE_MS);
            float smoothedRate = MetricResolver.smoothRate(
                    data.netRate(),
                    hasExisting ? existing.smoothedNetRate : 0.0f,
                    MetricResolver.DEFAULT_SMOOTHING_ALPHA,
                    !hasExisting
            );

            MetricResolver.EtaResult eta = MetricResolver.calculateEta(
                    data.storageCurrent(),
                    data.storageMax(),
                    smoothedRate,
                    data.isStorageInfinite(),
                    data.isStorageDraining()
            );

            CachedHudRenderData renderData = buildRenderData(data);
            CACHE.put(pos, new Entry(data, renderData, System.currentTimeMillis(), smoothedRate, eta));
        }
    }

    public static void put(BlockPos pos, TelemetryData data) {
        if (pos != null && data != null) {
            Entry existing = CACHE.get(pos);
            if (existing != null && (System.currentTimeMillis() - existing.receivedTimeMs < EXPIRE_MS)) {
                return;
            }
            float smoothedRate = MetricResolver.smoothRate(
                    data.netRate(),
                    0.0f,
                    MetricResolver.DEFAULT_SMOOTHING_ALPHA,
                    true
            );

            MetricResolver.EtaResult eta = MetricResolver.calculateEta(
                    data.storageCurrent(),
                    data.storageMax(),
                    smoothedRate,
                    data.isStorageInfinite(),
                    data.isStorageDraining()
            );

            CachedHudRenderData renderData = buildRenderData(data);
            CACHE.put(pos, new Entry(data, renderData, System.currentTimeMillis(), smoothedRate, eta));
        }
    }

    public static TelemetryData get(BlockPos pos) {
        Entry entry = getEntry(pos);
        return entry != null ? entry.data : TelemetryData.empty();
    }

    public static float getSmoothedNetRate(BlockPos pos) {
        Entry entry = getEntry(pos);
        return entry != null ? entry.smoothedNetRate : 0.0f;
    }

    public static MetricResolver.EtaResult getEta(BlockPos pos) {
        Entry entry = getEntry(pos);
        if (entry != null && entry.etaResult != null) {
            return entry.etaResult;
        }
        return new MetricResolver.EtaResult(MetricResolver.EtaType.STATIONARY, -1);
    }

    public static CachedHudRenderData getRenderData(BlockPos pos, TelemetryData rawDataFallback) {
        Entry entry = getEntry(pos);
        if (entry != null && entry.renderData != null) {
            return entry.renderData;
        }
        return buildRenderData(rawDataFallback != null ? rawDataFallback : TelemetryData.empty());
    }

    public static Entry getCacheEntry(BlockPos pos) {
        return getEntry(pos);
    }

    private static Entry getEntry(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Entry entry = CACHE.get(pos);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.receivedTimeMs > EXPIRE_MS) {
            CACHE.remove(pos);
            return null;
        }
        return entry;
    }

    public static void remove(BlockPos pos) {
        if (pos != null) {
            CACHE.remove(pos);
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    public static CachedHudRenderData buildRenderData(TelemetryData data) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int itemsColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ITEMS, 0xFFD54F);
        int fluidsColor = ClientConfig.parseHexColor(ClientConfig.COLOR_FLUIDS, 0x4FC3F7);
        int chemicalsColor = ClientConfig.parseHexColor(ClientConfig.COLOR_CHEMICALS, 0xAEEA00);
        int kineticColor = ClientConfig.parseHexColor(ClientConfig.COLOR_KINETIC, 0xFF9E59);
        int positiveEnergyColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ENERGY_POSITIVE, 0x69F0AE);
        int negativeEnergyColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ENERGY_NEGATIVE, 0xFF5252);
        int neutralEnergyColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ENERGY_NEUTRAL, 0xE040FB);
        int detailedPositiveEnergyColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ENERGY_POSITIVE, 0x69F0AE);
        int sideColor = ClientConfig.parseHexColor(ClientConfig.COLOR_SIDES, 0x69F0AE);
        int idleColor = ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_IDLE, 0xAAAAAA);
        int headerColor = ClientConfig.parseHexColor(ClientConfig.COLOR_HEADER, 0x00FFCC);

        String compactMetricStr;
        int compactMetricColor = 0xFFFFFF;

        if (data.hasKinetic() || Math.abs(data.kineticSpeed()) > 0f) {
            String dirSuffix = (data.kineticDirection() != null && data.kineticDirection() != CreateKineticInspector.RotationDirection.STATIONARY)
                    ? " " + data.kineticDirection().getLabel()
                    : "";
            compactMetricStr = String.format("%.0f RPM%s", Math.abs(data.kineticSpeed()), dirSuffix);
            compactMetricColor = kineticColor;
        } else if (data.itemThroughput() > 0f || (data.hasItems() && !data.hasFluids() && !data.hasChemicals() && !data.hasEnergy())) {
            compactMetricStr = formatItemRate(data.itemThroughput());
            compactMetricColor = itemsColor;
        } else if (data.fluidThroughput() > 0f || (data.hasFluids() && !data.hasChemicals() && !data.hasEnergy())) {
            compactMetricStr = formatFluidRate(data.fluidThroughput());
            compactMetricColor = fluidsColor;
        } else if (data.chemicalThroughput() > 0f || (data.hasChemicals() && !data.hasEnergy())) {
            if (data.chemicalThroughput() > 0.001f) {
                compactMetricStr = formatChemicalRate(data.chemicalThroughput());
            } else if (data.chemicalCapacity() > 0) {
                compactMetricStr = "🧪 " + MetricResolver.formatVolumeWithCapacity(data.chemicalAmount(), data.chemicalCapacity());
            } else {
                compactMetricStr = formatChemicalRate(data.chemicalThroughput());
            }
            compactMetricColor = chemicalsColor;
        } else if (data.hasEnergy() || data.energyDelta() != 0f) {
            compactMetricStr = formatEnergyRate(data.energyDelta());
            compactMetricColor = data.energyDelta() > 0 ? positiveEnergyColor : (data.energyDelta() < 0 ? negativeEnergyColor : neutralEnergyColor);
        } else if (data.hasStorage()) {
            compactMetricStr = MetricResolver.formatCompactStorageSummary(
                    data.storageCurrent(),
                    data.storageMax(),
                    data.isStorageInfinite(),
                    MetricResolver.calculateEta(data.storageCurrent(), data.storageMax(), data.netRate(), data.isStorageInfinite(), data.isStorageDraining())
            );
            compactMetricColor = MetricResolver.getStorageBarColor(data.storageCurrent(), data.storageMax(), data.isStorageInfinite());
        } else {
            compactMetricStr = Component.translatable("machinemetrics.state.idle").getString();
            compactMetricColor = idleColor;
        }

        Component compactTitleComp = Component.translatable("machinemetrics.hud.title");
        Component compactMetricComp = Component.literal(compactMetricStr);

        int compactTitleWidth = font != null ? font.width(compactTitleComp) : 80;
        int compactMetricWidth = font != null ? font.width(compactMetricStr) : 80;
        int compactWidth = Math.max(110, Math.max(compactTitleWidth, compactMetricWidth) + 30);
        int compactHeight = 30;
        int compactBadgeColor = getStatusColor(data.bottleneckState());

        Component detailedTitleComp = Component.translatable("machinemetrics.hud.detailed_title");
        Component detailedStateComp = getStateComponent(data.bottleneckState());
        int detailedStateColor = getStatusColor(data.bottleneckState());

        List<RenderRow> detailedRows = new ArrayList<>();

        if (data.hasKinetic() || Math.abs(data.kineticSpeed()) > 0f || data.kineticCapacity() > 0f) {
            String dirSuffix = (data.kineticDirection() != null && data.kineticDirection() != CreateKineticInspector.RotationDirection.STATIONARY)
                    ? " " + data.kineticDirection().getLabel()
                    : "";
            String speedLabel = Component.translatable("machinemetrics.metrics.speed").getString() + ":";
            String speedVal = String.format("%.0f RPM%s", Math.abs(data.kineticSpeed()), dirSuffix);
            detailedRows.add(new RenderRow(
                    Component.literal(speedLabel),
                    Component.literal(speedVal),
                    kineticColor,
                    font != null ? font.width(speedVal) : 0
            ));

            float actualCapacity = data.kineticCapacity();
            float actualStress = data.kineticStress();

            String stressLabel = Component.translatable("machinemetrics.metrics.stress").getString() + ":";
            String stressVal;
            if (actualCapacity > 0f) {
                float stressPct = (actualStress / actualCapacity) * 100f;
                stressVal = String.format("%.0f / %.0f SU (%.0f%%)", actualStress, actualCapacity, stressPct);
            } else if (actualStress > 0f) {
                stressVal = String.format("%.0f SU", actualStress);
            } else {
                stressVal = "0 SU";
            }
            int stressColor = data.bottleneckState() == TelemetryData.BottleneckState.OVERLOADED ?
                    ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_OVERLOADED, 0xFF3333) : kineticColor;
            detailedRows.add(new RenderRow(
                    Component.literal(stressLabel),
                    Component.literal(stressVal),
                    stressColor,
                    font != null ? font.width(stressVal) : 0
            ));

            if (data.kineticBlockStress() > 0.001f) {
                String impactLabel = Component.translatable("machinemetrics.metrics.stress_impact").getString() + ":";
                String impactVal = String.format("%.0f SU", data.kineticBlockStress());
                detailedRows.add(new RenderRow(
                        Component.literal(impactLabel),
                        Component.literal(impactVal),
                        kineticColor,
                        font != null ? font.width(impactVal) : 0
                ));
            }
        }

        if (data.bottleneckState() != TelemetryData.BottleneckState.IDLE) {
            if (data.hasItems() || data.itemThroughput() > 0f) {
                String labelStr = Component.translatable("machinemetrics.metrics.items").getString() + ":";
                String valStr = formatItemRate(data.itemThroughput());
                detailedRows.add(new RenderRow(
                        Component.literal(labelStr),
                        Component.literal(valStr),
                        itemsColor,
                        font != null ? font.width(valStr) : 0
                ));
            }

            if (data.hasFluids() || data.fluidThroughput() > 0f) {
                String labelStr = Component.translatable("machinemetrics.metrics.fluids").getString() + ":";
                String valStr;
                if (data.fluidCapacity() > 0) {
                    String vol = MetricResolver.formatVolumeWithCapacity(data.fluidAmount(), data.fluidCapacity());
                    String namePart = (data.fluidName() != null && !data.fluidName().isEmpty())
                            ? data.fluidName() + " " : "";
                    valStr = data.fluidThroughput() > 0.001f
                            ? namePart + vol + " | " + formatFluidRate(data.fluidThroughput())
                            : namePart + vol;
                } else {
                    valStr = formatFluidRate(data.fluidThroughput());
                }
                detailedRows.add(new RenderRow(
                        Component.literal(labelStr),
                        Component.literal(valStr),
                        fluidsColor,
                        font != null ? font.width(valStr) : 0
                ));
            }

            if (data.hasChemicals() || data.chemicalThroughput() > 0f) {
                String labelStr = Component.translatable("machinemetrics.metrics.chemicals").getString() + ":";
                String valStr;
                if (data.chemicalCapacity() > 0) {
                    String vol = MetricResolver.formatVolumeWithCapacity(data.chemicalAmount(), data.chemicalCapacity());
                    valStr = data.chemicalThroughput() > 0.001f
                            ? vol + " | " + formatChemicalRate(data.chemicalThroughput())
                            : vol;
                } else {
                    valStr = formatChemicalRate(data.chemicalThroughput());
                }
                detailedRows.add(new RenderRow(
                        Component.literal(labelStr),
                        Component.literal(valStr),
                        chemicalsColor,
                        font != null ? font.width(valStr) : 0
                ));
            }

            if (data.hasEnergy() || data.energyDelta() != 0f) {
                String labelStr = Component.translatable("machinemetrics.metrics.energy").getString() + ":";
                String valStr = formatEnergyRate(data.energyDelta());
                int energyColor = data.energyDelta() > 0 ? detailedPositiveEnergyColor : (data.energyDelta() < 0 ? negativeEnergyColor : neutralEnergyColor);
                detailedRows.add(new RenderRow(
                        Component.literal(labelStr),
                        Component.literal(valStr),
                        energyColor,
                        font != null ? font.width(valStr) : 0
                ));
            }
        }

        if (data.hasStorage()) {
            boolean showStorage = ClientConfig.SHOW_STORAGE_CAPACITY == null || ClientConfig.SHOW_STORAGE_CAPACITY.get();
            if (showStorage) {
                String progressBar = MetricResolver.buildStorageProgressBar(data.storageCurrent(), data.storageMax(), data.isStorageInfinite());
                int barColor = MetricResolver.getStorageBarColor(data.storageCurrent(), data.storageMax(), data.isStorageInfinite());
                detailedRows.add(new RenderRow(
                        Component.literal(progressBar),
                        Component.literal(""),
                        barColor,
                        0
                ));
            }

            boolean showTimeToFull = ClientConfig.SHOW_TIME_TO_FULL == null || ClientConfig.SHOW_TIME_TO_FULL.get();
            if (showTimeToFull) {
                MetricResolver.EtaResult eta = MetricResolver.calculateEta(
                        data.storageCurrent(),
                        data.storageMax(),
                        data.netRate(),
                        data.isStorageInfinite(),
                        data.isStorageDraining()
                );
                if (eta.hasEta()) {
                    Component etaComp = (eta.type() == MetricResolver.EtaType.FULL_IN)
                            ? Component.translatable("machinemetrics.hud.storage.full_in", eta.formatted())
                            : Component.translatable("machinemetrics.hud.storage.empty_in", eta.formatted());
                    int etaColor = MetricResolver.getEtaColor(eta.seconds());
                    detailedRows.add(new RenderRow(
                            etaComp,
                            Component.literal(""),
                            etaColor,
                            0
                    ));
                }
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
                String labelStr = summaryText.toString().trim() + ":";
                detailedRows.add(new RenderRow(
                        Component.literal(labelStr),
                        Component.literal(""),
                        sideColor,
                        0
                ));
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
                        String labelStr = sideText.toString().trim() + ":";
                        detailedRows.add(new RenderRow(
                                Component.literal(labelStr),
                                Component.literal(""),
                                sideColor,
                                0
                        ));
                    }
                }
            }
        }

        int headerLineWidth = (font != null ? font.width(detailedTitleComp) + font.width(detailedStateComp) : 100) + 20;

        int maxRowWidth = 0;
        for (RenderRow row : detailedRows) {
            int labelW = font != null ? font.width(row.labelComp()) : 0;
            int rowW = labelW + row.valueWidth() + 20;
            if (rowW > maxRowWidth) {
                maxRowWidth = rowW;
            }
        }

        int detailedWidth = Math.max(140, Math.max(headerLineWidth, maxRowWidth));
        int lineHeight = 12;
        int detailedHeight = detailedRows.isEmpty() ? 22 : 24 + (detailedRows.size() * lineHeight);

        return new CachedHudRenderData(
                data,
                compactTitleComp,
                compactMetricComp,
                compactMetricColor,
                compactWidth,
                compactHeight,
                compactBadgeColor,
                detailedTitleComp,
                detailedStateComp,
                detailedStateColor,
                detailedRows,
                detailedWidth,
                detailedHeight,
                headerColor
        );
    }

    private static Component getStateComponent(TelemetryData.BottleneckState state) {
        return switch (state) {
            case OPTIMAL -> Component.translatable("machinemetrics.state.optimal");
            case STARVED -> Component.translatable("machinemetrics.state.starved");
            case CLOGGED -> Component.translatable("machinemetrics.state.clogged");
            case OVERLOADED -> Component.translatable("machinemetrics.state.overloaded");
            case IDLE -> Component.translatable("machinemetrics.state.idle");
        };
    }

    private static int getStatusColor(TelemetryData.BottleneckState state) {
        return switch (state) {
            case OPTIMAL -> ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_OPTIMAL, 0x55FF55);
            case STARVED -> ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_STARVED, 0xFFFF55);
            case CLOGGED -> ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_CLOGGED, 0xFF5555);
            case OVERLOADED -> ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_OVERLOADED, 0xFF3333);
            case IDLE -> ClientConfig.parseHexColor(ClientConfig.COLOR_STATE_IDLE, 0xAAAAAA);
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
}

