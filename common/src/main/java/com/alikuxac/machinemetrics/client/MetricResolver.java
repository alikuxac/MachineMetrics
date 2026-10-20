package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector;
import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public class MetricResolver {

    public static String formatCompactNumber(float value) {
        if (Float.isNaN(value)) {
            return "0";
        }
        if (Float.isInfinite(value)) {
            return "∞";
        }
        float abs = Math.abs(value);
        if (abs >= 1_000_000_000f) {
            return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000f);
        } else if (abs >= 1_000_000f) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000f);
        } else if (abs >= 1_000f) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000f);
        } else if (abs < 1.0f && abs > 0.001f) {
            return String.format(Locale.ROOT, "%.1f", value);
        } else {
            return String.format(Locale.ROOT, "%.0f", value);
        }
    }

    public static String formatCompactNumber(long value) {
        if (value >= (Long.MAX_VALUE / 2) || value < 0) {
            return "∞";
        }
        return formatCompactNumber((float) value);
    }

    public static String formatStorageNumber(long value, boolean isInfinite) {
        if (isInfinite || value >= (Long.MAX_VALUE / 2) || value < 0) {
            return "∞";
        }
        return formatCompactNumber(value);
    }

    public static String formatStorageCount(long value, boolean isInfinite) {
        if (isInfinite || value >= (Long.MAX_VALUE / 2) || value < 0) {
            return "∞";
        }
        if (value >= 10_000_000L) {
            return formatCompactNumber(value);
        }
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String formatStoragePercent(long current, long max, boolean isInfinite) {
        if (isInfinite || max >= (Long.MAX_VALUE / 2)) {
            return "∞";
        }
        if (max <= 0) {
            return "0%";
        }
        float pct = (float) ((current / (double) max) * 100.0);
        return String.format(Locale.ROOT, "%.1f%%", pct);
    }

    public static final int DEFAULT_STORAGE_BAR_LENGTH = 10;

    public static String buildStorageProgressBar(long current, long max, boolean isInfinite) {
        return buildStorageProgressBar(current, max, isInfinite, DEFAULT_STORAGE_BAR_LENGTH);
    }

    public static String buildStorageProgressBar(long current, long max, boolean isInfinite, int barLength) {
        StringBuilder sb = new StringBuilder("[");
        if (isInfinite || max >= (Long.MAX_VALUE / 2)) {
            sb.append("█".repeat(Math.max(1, barLength)));
            sb.append("] ∞% (∞ / ∞)");
            return sb.toString();
        }
        double ratio = max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) current / (double) max));
        int filled = (int) Math.round(ratio * barLength);
        filled = Math.max(0, Math.min(barLength, filled));
        int unfilled = barLength - filled;

        sb.append("█".repeat(filled));
        sb.append("░".repeat(unfilled));
        sb.append("] ");
        sb.append(String.format(Locale.ROOT, "%.1f%%", ratio * 100.0));
        sb.append(String.format(Locale.ROOT, " (%s / %s)", formatStorageCount(current, false), formatStorageCount(max, false)));
        return sb.toString();
    }

    public static int getStorageBarColor(long current, long max, boolean isInfinite) {
        int normalColor = ClientConfig.parseHexColor(ClientConfig.COLOR_STORAGE_NORMAL, 0x55FF55);
        int warningColor = ClientConfig.parseHexColor(ClientConfig.COLOR_STORAGE_WARNING, 0xFFA726);
        int criticalColor = ClientConfig.parseHexColor(ClientConfig.COLOR_STORAGE_CRITICAL, 0xFF5555);

        if (isInfinite || max <= 0) {
            return normalColor;
        }

        double warningThreshold = ClientConfig.STORAGE_WARNING_THRESHOLD != null ? ClientConfig.STORAGE_WARNING_THRESHOLD.get() : 80.0;
        double criticalThreshold = ClientConfig.STORAGE_CRITICAL_THRESHOLD != null ? ClientConfig.STORAGE_CRITICAL_THRESHOLD.get() : 95.0;

        double pct = ((double) current / (double) max) * 100.0;
        if (pct >= criticalThreshold) {
            return criticalColor;
        } else if (pct >= warningThreshold) {
            return warningColor;
        } else {
            return normalColor;
        }
    }

    public static int getEtaColor(long seconds) {
        int normalColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ETA_NORMAL, 0x55FF55);
        int warningColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ETA_WARNING, 0xFFA726);
        int criticalColor = ClientConfig.parseHexColor(ClientConfig.COLOR_ETA_CRITICAL, 0xFF3333);

        if (seconds < 0) {
            return normalColor;
        }

        int warningSeconds = ClientConfig.ETA_WARNING_THRESHOLD_SECONDS != null ? ClientConfig.ETA_WARNING_THRESHOLD_SECONDS.get() : 60;
        int criticalSeconds = ClientConfig.ETA_CRITICAL_THRESHOLD_SECONDS != null ? ClientConfig.ETA_CRITICAL_THRESHOLD_SECONDS.get() : 15;

        if (seconds < criticalSeconds) {
            // Flashing red alert at 2.5 Hz (400ms period)
            boolean flash = (System.currentTimeMillis() / 400L) % 2L == 0;
            return flash ? criticalColor : warningColor;
        } else if (seconds < warningSeconds) {
            return warningColor;
        } else {
            return normalColor;
        }
    }

    public static String formatCompactStorage(long current, long max, boolean isInfinite) {
        String pct = formatStoragePercent(current, max, isInfinite);
        return "📦 " + pct;
    }

    public static String formatCompactEta(EtaResult eta) {
        if (eta != null && eta.hasEta()) {
            return "⏳ " + eta.formatted();
        }
        return "—";
    }

    public static String formatCompactStorageSummary(long current, long max, boolean isInfinite, EtaResult eta) {
        String storagePart = formatCompactStorage(current, max, isInfinite);
        if (eta != null && eta.hasEta()) {
            return storagePart + " | " + formatCompactEta(eta);
        }
        return storagePart;
    }

    public enum EtaType {
        FULL_IN,
        EMPTY_IN,
        STATIONARY,
        INFINITE
    }

    public record EtaResult(EtaType type, long seconds) {
        public boolean hasEta() {
            return type == EtaType.FULL_IN || type == EtaType.EMPTY_IN;
        }

        public String formatted() {
            return formatDuration(seconds);
        }
    }

    public static final float DEFAULT_SMOOTHING_ALPHA = 0.15f;

    public static EtaResult calculateEta(long current, long max, float netRate, boolean isInfinite, boolean isDraining) {
        if (isInfinite || max >= (Long.MAX_VALUE / 2) || max < 0) {
            return new EtaResult(EtaType.INFINITE, -1);
        }
        if (Float.isNaN(netRate) || Float.isInfinite(netRate) || Math.abs(netRate) <= 0.001f) {
            return new EtaResult(EtaType.STATIONARY, -1);
        }

        if (netRate > 0 && !isDraining) {
            if (current >= max) {
                return new EtaResult(EtaType.FULL_IN, 0);
            }
            long remaining = Math.max(0L, max - current);
            long sec = Math.round((double) remaining / netRate);
            return new EtaResult(EtaType.FULL_IN, Math.max(0L, sec));
        } else {
            if (current <= 0) {
                return new EtaResult(EtaType.EMPTY_IN, 0);
            }
            float absRate = Math.abs(netRate);
            long sec = Math.round((double) current / absRate);
            return new EtaResult(EtaType.EMPTY_IN, Math.max(0L, sec));
        }
    }

    public static float smoothRate(float instantRate, float prevSmoothRate, float alpha, boolean isFirstSample) {
        if (Float.isNaN(instantRate)) {
            return isFirstSample ? 0.0f : prevSmoothRate;
        }
        if (isFirstSample) {
            return instantRate;
        }
        float effectiveAlpha = Math.max(0.01f, Math.min(1.0f, alpha));
        float smoothed = (effectiveAlpha * instantRate) + ((1.0f - effectiveAlpha) * prevSmoothRate);
        if (Math.abs(smoothed) < 0.001f) {
            return 0.0f;
        }
        return smoothed;
    }

    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "—";
        }
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%ds", seconds);
        }
        long minutes = seconds / 60;
        long remainingSec = seconds % 60;
        if (minutes < 60) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, remainingSec);
        }
        long hours = minutes / 60;
        long remainingMin = minutes % 60;
        return String.format(Locale.ROOT, "%dh %02dm", hours, remainingMin);
    }

    public static String formatEta(long seconds) {
        return formatDuration(seconds);
    }

    public static String formatEta(float seconds) {
        if (Float.isNaN(seconds) || seconds < 0) {
            return "—";
        }
        return formatDuration(Math.round(seconds));
    }

    public static String formatHeadroom(float headroom) {
        if (headroom > 0.001f) {
            return "+" + formatCompactNumber(headroom);
        } else {
            return formatCompactNumber(headroom);
        }
    }

    public static String formatItemRate(float rate) {
        if (rate <= 0.001f) return "0.0 /s";
        if (rate < 1.0f) {
            return String.format(Locale.ROOT, "%.2f /s", rate);
        } else {
            return String.format(Locale.ROOT, "%.1f /s", rate);
        }
    }

    public static String formatFluidRate(float rate) {
        if (rate <= 0.001f) return "0.0 mB/s";
        if (rate >= 1_000f) {
            return String.format(Locale.ROOT, "%.2f B/s", rate / 1_000f);
        } else if (rate < 1.0f) {
            return String.format(Locale.ROOT, "%.2f mB/s", rate);
        } else {
            return String.format(Locale.ROOT, "%.1f mB/s", rate);
        }
    }

    public static String formatChemicalRate(float rate) {
        return formatFluidRate(rate);
    }

    public static String resolveRotationDirection(CreateKineticInspector.RotationDirection dir, float speed) {
        if (dir != null) {
            return dir.getLabel();
        }
        return CreateKineticInspector.RotationDirection.fromSpeed(speed).getLabel();
    }

    public static MutableComponent getStateComponent(TelemetryData.BottleneckState state) {
        if (state == null) {
            return Component.translatable("machinemetrics.state.idle").withStyle(ChatFormatting.GRAY);
        }
        return switch (state) {
            case OPTIMAL -> Component.translatable("machinemetrics.state.optimal").withStyle(ChatFormatting.GREEN);
            case STARVED -> Component.translatable("machinemetrics.state.starved").withStyle(ChatFormatting.YELLOW);
            case CLOGGED -> Component.translatable("machinemetrics.state.clogged").withStyle(ChatFormatting.RED);
            case OVERLOADED -> Component.translatable("machinemetrics.state.overloaded").withStyle(ChatFormatting.RED);
            case IDLE -> Component.translatable("machinemetrics.state.idle").withStyle(ChatFormatting.GRAY);
        };
    }

    public static String resolveStateLabelWithFormatting(TelemetryData.BottleneckState state) {
        if (state == null) {
            return ChatFormatting.GRAY + Component.translatable("machinemetrics.state.idle").getString();
        }
        return switch (state) {
            case OPTIMAL -> ChatFormatting.GREEN + Component.translatable("machinemetrics.state.optimal").getString();
            case STARVED -> ChatFormatting.YELLOW + Component.translatable("machinemetrics.state.starved").getString();
            case CLOGGED -> ChatFormatting.RED + Component.translatable("machinemetrics.state.clogged").getString();
            case OVERLOADED -> ChatFormatting.RED + Component.translatable("machinemetrics.state.overloaded").getString();
            case IDLE -> ChatFormatting.GRAY + Component.translatable("machinemetrics.state.idle").getString();
        };
    }

    public static String getBlockOrMachineName(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return Component.translatable("machinemetrics.hud.title").getString();
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return Component.translatable("machinemetrics.hud.title").getString();
        }
        return state.getBlock().getName().getString();
    }

    public static String resolveTemplate(
            String template,
            String blockName,
            TelemetryData data
    ) {
        return resolveTemplate(template, blockName, data, null);
    }

    public static String resolveTemplate(
            String template,
            String blockName,
            TelemetryData data,
            EtaResult eta
    ) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        float speed = data != null ? Math.abs(data.kineticSpeed()) : 0f;
        String speedStr = String.format(Locale.ROOT, "%.1f", speed);
        String dirStr = resolveRotationDirection(data != null ? data.kineticDirection() : null, data != null ? data.kineticSpeed() : 0f);

        float stress = data != null ? data.kineticStress() : 0f;
        float cap = data != null ? data.kineticCapacity() : 0f;
        float headroom = Math.max(0f, cap - stress);
        float loadPct = cap > 0.001f ? (stress / cap) * 100f : 0f;

        String stressStr = formatCompactNumber(stress);
        String capStr = formatCompactNumber(cap);
        String headroomStr = formatHeadroom(headroom);
        String loadPctStr = String.format(Locale.ROOT, "%.0f", loadPct);
        String stateStr = resolveStateLabelWithFormatting(data != null ? data.bottleneckState() : TelemetryData.BottleneckState.IDLE);

        boolean itemsDisabled = data != null && data.isItemsDisabledByServer();
        boolean fluidsDisabled = data != null && data.isFluidsDisabledByServer();
        boolean kineticDisabled = data != null && data.isKineticDisabledByServer();

        String disabledLabel = Component.translatable("machinemetrics.metrics.disabled_by_server").getString();

        String itemsStr = itemsDisabled ? disabledLabel : formatItemRate(data != null ? data.itemThroughput() : 0f);
        String fluidsStr = fluidsDisabled ? disabledLabel : formatFluidRate(data != null ? data.fluidThroughput() : 0f);

        String aePowerStr = "—";
        String aeStateStr = "—";
        String aeDrainStr = "—";
        String aeSubnetsStr = "—";
        if (data != null && data.hasAe2Network()) {
            if (data.isAe2DisabledByServer()) {
                aePowerStr = disabledLabel;
                aeStateStr = disabledLabel;
                aeDrainStr = disabledLabel;
                aeSubnetsStr = disabledLabel;
            } else {
                String unitConfig = ClientConfig.AE2_POWER_UNIT != null
                        ? ClientConfig.AE2_POWER_UNIT.get() : "AUTO";
                boolean isFe = "FE".equalsIgnoreCase(unitConfig);
                if ("AUTO".equalsIgnoreCase(unitConfig)) {
                    isFe = "FE".equalsIgnoreCase(AE2NetworkInspector.getClientSelectedPowerUnit());
                }
                float mult = isFe ? 2.0f : 1.0f;
                String unitStr = isFe ? "FE/t" : "AE/t";
                boolean isInfiniteEnergy = data.aeIsCreative()
                        || Double.isInfinite(data.aeStoredPower())
                        || Double.isInfinite(data.aeMaxPower());
                if (isInfiniteEnergy) {
                    aePowerStr = "∞ " + (isFe ? "FE" : "AE");
                } else {
                    aePowerStr = formatCompactNumber(data.aePowerRate() * mult) + " " + unitStr;
                }
                aeDrainStr = formatCompactNumber(data.aePowerDrain() * mult) + " " + unitStr;
                aeSubnetsStr = String.format(Locale.ROOT, "%d (+%s %s)",
                        data.aeSubnetCount(),
                        formatCompactNumber(data.aeSubnetDrain() * mult),
                        unitStr);
                aeStateStr = AE2NetworkInspector.AE2StabilityState.fromOrdinal(data.aeStabilityState()).name();
            }
        }

        String storageStr = "—";
        String etaStr = "—";
        if (data != null && data.hasStorage()) {
            storageStr = formatCompactStorage(data.storageCurrent(), data.storageMax(), data.isStorageInfinite());
            EtaResult effectiveEta = eta != null ? eta : calculateEta(
                    data.storageCurrent(),
                    data.storageMax(),
                    data.netRate(),
                    data.isStorageInfinite(),
                    data.isStorageDraining()
            );
            etaStr = formatCompactEta(effectiveEta);
        }

        boolean autoHideTitle = ClientConfig.AUTO_HIDE_TITLE_WITH_INFO_MODS == null || ClientConfig.AUTO_HIDE_TITLE_WITH_INFO_MODS.get();
        boolean hideTitle = autoHideTitle && com.alikuxac.machinemetrics.compat.infohud.InfoHudDetector.isInfoHudPresent();
        String effectiveTemplate = template;
        if (hideTitle && effectiveTemplate.startsWith("{name} | ")) {
            effectiveTemplate = effectiveTemplate.substring("{name} | ".length());
        } else if (hideTitle && effectiveTemplate.startsWith("{name} ")) {
            effectiveTemplate = effectiveTemplate.substring("{name} ".length());
        }

        return effectiveTemplate
                .replace("{name}", hideTitle ? "" : (blockName != null ? blockName : Component.translatable("machinemetrics.hud.title").getString()))
                .replace("{speed}", kineticDisabled ? "—" : speedStr)
                .replace("{dir}", kineticDisabled ? "—" : dirStr)
                .replace("{stress}", kineticDisabled ? "—" : stressStr)
                .replace("{cap}", kineticDisabled ? "—" : capStr)
                .replace("{headroom}", kineticDisabled ? "—" : headroomStr)
                .replace("{load_pct}", kineticDisabled ? "—" : loadPctStr)
                .replace("{state}", stateStr)
                .replace("{items}", itemsStr)
                .replace("{fluids}", fluidsStr)
                .replace("{storage}", storageStr)
                .replace("{eta}", etaStr)
                .replace("{ae_power}", aePowerStr)
                .replace("{ae_drain}", aeDrainStr)
                .replace("{ae_subnets}", aeSubnetsStr)
                .replace("{ae_state}", aeStateStr);
    }
}
