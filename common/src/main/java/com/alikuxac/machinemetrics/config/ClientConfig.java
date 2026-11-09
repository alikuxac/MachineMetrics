package com.alikuxac.machinemetrics.config;

import com.alikuxac.machinemetrics.platform.config.Config;

import java.util.List;

public class ClientConfig {

    public enum HudAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CROSSHAIR
    }

    public static final Config.Spec SPEC;

    // HUD General
    public static final Config.Value<Boolean> HUD_ENABLED;
    public static final Config.Value<HudAnchor> HUD_ANCHOR;
    public static final Config.Value<Integer> HUD_X_OFFSET;
    public static final Config.Value<Integer> HUD_Y_OFFSET;
    public static final Config.Value<Double> HUD_SCALE;

    // HUD Colors
    public static final Config.Value<String> BACKGROUND_COLOR;
    public static final Config.Value<String> BORDER_COLOR;
    public static final Config.Value<String> PINNED_BORDER_COLOR;

    // Granular Element Switches
    public static final Config.Value<Boolean> SHOW_MACHINE_TITLE;
    public static final Config.Value<Boolean> AUTO_HIDE_TITLE_WITH_INFO_MODS;
    public static final Config.Value<Boolean> AUTO_HIDE_REDUNDANT_WITH_INFOHUD;
    public static final Config.Value<Boolean> SHOW_STATUS_BADGE;
    public static final Config.Value<Boolean> SHOW_ITEMS_THROUGHPUT;
    public static final Config.Value<Boolean> SHOW_FLUIDS_THROUGHPUT;
    public static final Config.Value<Boolean> SHOW_CHEMICALS_THROUGHPUT;
    public static final Config.Value<Boolean> SHOW_SPEED;
    public static final Config.Value<Boolean> SHOW_ROTATION_DIRECTION;
    public static final Config.Value<Boolean> SHOW_LOCAL_IMPACT;
    public static final Config.Value<Boolean> SHOW_NETWORK_STRESS;
    public static final Config.Value<Boolean> SHOW_HEADROOM;
    public static final Config.Value<Boolean> SHOW_ENERGY_FE;
    public static final Config.Value<Boolean> SHOW_SIDES;
    public static final Config.Value<Boolean> SHOW_AE2_POWER;
    public static final Config.Value<Boolean> SHOW_AE2_STABILITY;
    public static final Config.Value<Boolean> AUTO_HIDE_AE2_CHANNELS_WITH_INFOHUD;
    public static final Config.Value<Boolean> SHOW_AE2_CHANNELS;
    public static final Config.Value<String> AE2_POWER_UNIT;
    public static final Config.Value<Boolean> SHOW_STORAGE_CAPACITY;
    public static final Config.Value<Boolean> SHOW_TIME_TO_FULL;
    public static final Config.Value<Boolean> SHOW_TARGETED_FACE_IO;

    // Compact Formatting & Thresholds
    public static final Config.Value<Boolean> COMPACT_MODE;
    public static final Config.Value<String> COMPACT_TEMPLATE;
    public static final Config.Value<Double> STORAGE_WARNING_THRESHOLD;
    public static final Config.Value<Double> STORAGE_CRITICAL_THRESHOLD;
    public static final Config.Value<Integer> ETA_WARNING_THRESHOLD_SECONDS;
    public static final Config.Value<Integer> ETA_CRITICAL_THRESHOLD_SECONDS;

    // Metric Colors
    public static final Config.Value<String> COLOR_HEADER;
    public static final Config.Value<String> COLOR_ITEMS;
    public static final Config.Value<String> COLOR_FLUIDS;
    public static final Config.Value<String> COLOR_CHEMICALS;
    public static final Config.Value<String> COLOR_KINETIC;
    public static final Config.Value<String> COLOR_ENERGY_POSITIVE;
    public static final Config.Value<String> COLOR_ENERGY_NEGATIVE;
    public static final Config.Value<String> COLOR_ENERGY_NEUTRAL;
    public static final Config.Value<String> COLOR_SIDES;
    public static final Config.Value<String> COLOR_AE2_POWER;
    public static final Config.Value<String> COLOR_AE2_BROWNOUT;
    public static final Config.Value<String> COLOR_STORAGE_NORMAL;
    public static final Config.Value<String> COLOR_STORAGE_WARNING;
    public static final Config.Value<String> COLOR_STORAGE_CRITICAL;
    public static final Config.Value<String> COLOR_ETA_NORMAL;
    public static final Config.Value<String> COLOR_ETA_WARNING;
    public static final Config.Value<String> COLOR_ETA_CRITICAL;

    public static final Config.Value<String> COLOR_STATE_OPTIMAL;
    public static final Config.Value<String> COLOR_STATE_STARVED;
    public static final Config.Value<String> COLOR_STATE_CLOGGED;
    public static final Config.Value<String> COLOR_STATE_OVERLOADED;
    public static final Config.Value<String> COLOR_STATE_IDLE;
    public static final Config.Value<String> COLOR_IO_INPUT;
    public static final Config.Value<String> COLOR_IO_OUTPUT;
    public static final Config.Value<String> COLOR_IO_BIDIRECTIONAL;

    // Inspection Gate & Zoom
    public static final Config.Value<Boolean> REQUIRE_INSPECTION_ITEM;
    public static final Config.Value<List<? extends String>> INSPECTION_ITEM_TAGS;
    public static final Config.Value<Double> SPYGLASS_RAYCAST_DISTANCE;

    static {
        Config.Builder builder = Config.builder();

        builder.comment("HUD Position & Appearance").push("hud");

        HUD_ENABLED = builder
                .comment("Enable or disable HUD")
                .define("hud_enabled", true);

        HUD_ANCHOR = builder
                .comment("Anchor: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CROSSHAIR")
                .defineEnum("hud_anchor", HudAnchor.TOP_LEFT);

        HUD_X_OFFSET = builder
                .comment("Horizontal offset from anchor")
                .defineInRange("hud_x_offset", 10, -4000, 4000);

        HUD_Y_OFFSET = builder
                .comment("Vertical offset from anchor")
                .defineInRange("hud_y_offset", 10, -4000, 4000);

        HUD_SCALE = builder
                .comment("HUD scale multiplier (0.5 to 3.0)")
                .defineInRange("hudScale", 1.0, 0.5, 3.0);

        BACKGROUND_COLOR = builder
                .comment("Background color (hex ARGB/RGB)")
                .define("background_color", "#90000000");

        BORDER_COLOR = builder
                .comment("Border color (hex ARGB/RGB)")
                .define("border_color", "#FF00FFCC");

        PINNED_BORDER_COLOR = builder
                .comment("Pinned HUD Border color (hex ARGB/RGB)")
                .define("pinned_border_color", "#FFFFD700");

        builder.pop();

        builder.comment("Visible Metrics & Elements").push("elements");

        SHOW_MACHINE_TITLE = builder
                .comment("Show block name")
                .define("show_machine_title", true);

        AUTO_HIDE_TITLE_WITH_INFO_MODS = builder
                .comment("Hide title when Jade/Waila/WTHIT/TOP is present")
                .define("auto_hide_title_with_info_mods", true);

        AUTO_HIDE_REDUNDANT_WITH_INFOHUD = builder
                .comment("Hide redundant zero-rate metrics when Jade/Waila/WTHIT/TOP is present")
                .define("auto_hide_redundant_with_info_mods", true);

        SHOW_STATUS_BADGE = builder
                .comment("Show status (OPTIMAL, IDLE, etc.)")
                .define("show_status_badge", true);

        SHOW_ITEMS_THROUGHPUT = builder
                .comment("Show items throughput (Items/s)")
                .define("show_items_throughput", true);

        SHOW_FLUIDS_THROUGHPUT = builder
                .comment("Show fluids throughput (mB/s)")
                .define("show_fluids_throughput", true);

        SHOW_CHEMICALS_THROUGHPUT = builder
                .comment("Show Mekanism chemicals throughput")
                .define("show_chemicals_throughput", true);

        SHOW_SPEED = builder
                .comment("Show Create speed (RPM)")
                .define("show_speed", true);

        SHOW_ROTATION_DIRECTION = builder
                .comment("Show Create rotation direction (CW/CCW)")
                .define("show_rotation_direction", true);

        SHOW_LOCAL_IMPACT = builder
                .comment("Show Create local impact (Stress Units)")
                .define("show_local_impact", true);

        SHOW_NETWORK_STRESS = builder
                .comment("Show Create network stress summary")
                .define("show_network_stress", true);

        SHOW_HEADROOM = builder
                .comment("Show Create available headroom (Remaining SU)")
                .define("show_headroom", true);

        SHOW_ENERGY_FE = builder
                .comment("Show energy throughput (FE/t)")
                .define("show_energy_fe", true);

        SHOW_SIDES = builder
                .comment("Show side-specific throughput rates")
                .define("show_sides", true);

        SHOW_AE2_POWER = builder
                .comment("Show Applied Energistics 2 grid power state")
                .define("show_ae2_power", true);

        SHOW_AE2_STABILITY = builder
                .comment("Show AE2 power stability state (STABLE/BROWNOUT/CRITICAL)")
                .define("show_ae2_stability", true);

        AUTO_HIDE_AE2_CHANNELS_WITH_INFOHUD = builder
                .comment("Hide AE2 channels metric when Jade/Waila/WTHIT/TOP already provides channel information")
                .define("auto_hide_ae2_channels_with_infohud", true);

        SHOW_AE2_CHANNELS = builder
                .comment("Show AE2 cable channel usage (Used / Max)")
                .define("show_ae2_channels", true);

        AE2_POWER_UNIT = builder
                .comment("Unit for AE2 power display (AE or FE)")
                .define("ae2_power_unit", "AE");

        SHOW_STORAGE_CAPACITY = builder
                .comment("Show storage fill percentage and capacity bar for containers and tanks")
                .define("show_storage_capacity", true);

        SHOW_TIME_TO_FULL = builder
                .comment("Show estimated time until storage is full or empty (ETA)")
                .define("show_time_to_full", true);

        SHOW_TARGETED_FACE_IO = builder
                .comment("Show targeted face directional I/O and bottleneck diagnostics")
                .define("show_targeted_face_io", true);

        builder.pop();

        builder.comment("Compact Template & Thresholds").push("formatting");

        COMPACT_MODE = builder
                .comment("Enable single-line compact HUD")
                .define("compact_mode", false);

        COMPACT_TEMPLATE = builder
                .comment("Placeholders: {name}, {speed}, {dir}, {stress}, {cap}, {headroom}, {load_pct}, {state}, {items}, {fluids}, {storage}, {eta}")
                .define("compact_template", "{name} | {speed} RPM {dir} | {stress}/{cap} SU ({load_pct}%) | {state}");

        STORAGE_WARNING_THRESHOLD = builder
                .comment("Storage capacity percentage threshold for warning color (0.0 to 100.0)")
                .defineInRange("storage_warning_threshold", 80.0, 0.0, 100.0);

        STORAGE_CRITICAL_THRESHOLD = builder
                .comment("Storage capacity percentage threshold for critical color (0.0 to 100.0)")
                .defineInRange("storage_critical_threshold", 95.0, 0.0, 100.0);

        ETA_WARNING_THRESHOLD_SECONDS = builder
                .comment("Remaining ETA seconds threshold for warning color")
                .defineInRange("eta_warning_threshold_seconds", 60, 1, 3600);

        ETA_CRITICAL_THRESHOLD_SECONDS = builder
                .comment("Remaining ETA seconds threshold for critical blinking color")
                .defineInRange("eta_critical_threshold_seconds", 15, 1, 3600);

        builder.pop();

        builder.comment("Colors (Hex ARGB or RGB)").push("colors");

        COLOR_HEADER = builder
                .comment("Title & border accent")
                .define("headerColor", "#00FFCC");

        COLOR_ITEMS = builder
                .comment("Items text color")
                .define("itemsColor", "#FFD54F");

        COLOR_FLUIDS = builder
                .comment("Fluids text color")
                .define("fluidsColor", "#4FC3F7");

        COLOR_CHEMICALS = builder
                .comment("Chemicals text color")
                .define("chemicalsColor", "#AEEA00");

        COLOR_KINETIC = builder
                .comment("Kinetic (speed/stress) color")
                .define("kineticColor", "#FF9E59");

        COLOR_ENERGY_POSITIVE = builder
                .comment("Positive energy (+FE/t) color")
                .define("energyPositiveColor", "#69F0AE");

        COLOR_ENERGY_NEGATIVE = builder
                .comment("Negative energy (-FE/t) color")
                .define("energyNegativeColor", "#FF5252");

        COLOR_ENERGY_NEUTRAL = builder
                .comment("Neutral energy (0 FE/t) color")
                .define("energyNeutralColor", "#E040FB");

        COLOR_SIDES = builder
                .comment("Per-side metrics color")
                .define("sidesColor", "#69F0AE");

        COLOR_AE2_POWER = builder
                .comment("AE2 grid power color (Fluix purple)")
                .define("ae2PowerColor", "#A259FF");

        COLOR_AE2_BROWNOUT = builder
                .comment("AE2 grid brownout alert color")
                .define("ae2BrownoutColor", "#FF3333");

        COLOR_STORAGE_NORMAL = builder
                .comment("Storage capacity bar normal color (< warning threshold)")
                .define("storageNormalColor", "#55FF55");

        COLOR_STORAGE_WARNING = builder
                .comment("Storage capacity bar warning color (>= warning threshold and < critical threshold)")
                .define("storageWarningColor", "#FFA726");

        COLOR_STORAGE_CRITICAL = builder
                .comment("Storage capacity bar critical color (>= critical threshold)")
                .define("storageCriticalColor", "#FF5555");

        COLOR_ETA_NORMAL = builder
                .comment("ETA line normal color")
                .define("etaNormalColor", "#55FF55");

        COLOR_ETA_WARNING = builder
                .comment("ETA line warning color (< warning threshold seconds)")
                .define("etaWarningColor", "#FFA726");

        COLOR_ETA_CRITICAL = builder
                .comment("ETA line critical alert color (< critical threshold seconds, flashes red)")
                .define("etaCriticalColor", "#FF3333");

        COLOR_STATE_OPTIMAL = builder
                .comment("OPTIMAL status color")
                .define("stateOptimalColor", "#55FF55");

        COLOR_STATE_STARVED = builder
                .comment("STARVED status color")
                .define("stateStarvedColor", "#FFFF55");

        COLOR_STATE_CLOGGED = builder
                .comment("CLOGGED status color")
                .define("stateCloggedColor", "#FF5555");

        COLOR_STATE_OVERLOADED = builder
                .comment("OVERLOADED status color")
                .define("stateOverloadedColor", "#FF3333");

        COLOR_STATE_IDLE = builder
                .comment("IDLE status color")
                .define("stateIdleColor", "#AAAAAA");

        COLOR_IO_INPUT = builder
                .comment("Targeted face [INPUT] badge color")
                .define("color_io_input", "#4FC3F7");

        COLOR_IO_OUTPUT = builder
                .comment("Targeted face [OUTPUT] badge color")
                .define("color_io_output", "#FFA726");

        COLOR_IO_BIDIRECTIONAL = builder
                .comment("Targeted face [BIDIRECTIONAL] badge color")
                .define("color_io_bidirectional", "#CE93D8");

        builder.pop();

        builder.comment("Inspection Gate & Zoom Mode").push("inspection");

        REQUIRE_INSPECTION_ITEM = builder
                .comment("Require holding an inspection item (wrench, spyglass, etc.) to view HUD and poll server")
                .define("requireInspectionItem", false);

        INSPECTION_ITEM_TAGS = builder
                .comment("List of valid item IDs or item tags (prefixed with #) for inspection requirement")
                .defineList("inspectionItemTags", List.of("#c:tools/wrench", "minecraft:spyglass"), o -> o instanceof String);

        SPYGLASS_RAYCAST_DISTANCE = builder
                .comment("Raycast distance (in blocks) when zooming through a Spyglass")
                .defineInRange("spyglassRaycastDistance", 32.0, 4.0, 64.0);

        builder.pop();

        SPEC = builder.build();
    }

    public static int parseHexColor(Config.Value<String> configValue, int fallbackColor) {
        if (configValue == null || configValue.get() == null) return fallbackColor;
        return parseHexColor(configValue.get(), fallbackColor);
    }

    public static int parseHexColor(String hexStr, int fallbackColor) {
        if (hexStr == null || hexStr.isBlank()) return fallbackColor;
        try {
            String hex = hexStr.trim().replace("#", "").replace("0x", "").replace("0X", "");
            long val = Long.parseLong(hex, 16);
            if (hex.length() <= 6) {
                return (int) (0xFF000000L | val);
            }
            return (int) val;
        } catch (Exception e) {
            return fallbackColor;
        }
    }
}
