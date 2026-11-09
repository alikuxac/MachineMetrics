package com.alikuxac.machinemetrics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ClientConfig {

    public enum HudAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CROSSHAIR
    }

    public static final ModConfigSpec SPEC;

    // HUD General
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<HudAnchor> HUD_ANCHOR;
    public static final ModConfigSpec.IntValue HUD_X_OFFSET;
    public static final ModConfigSpec.IntValue HUD_Y_OFFSET;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;

    // HUD Colors
    public static final ModConfigSpec.ConfigValue<String> BACKGROUND_COLOR;
    public static final ModConfigSpec.ConfigValue<String> BORDER_COLOR;
    public static final ModConfigSpec.ConfigValue<String> PINNED_BORDER_COLOR;

    // Granular Element Switches
    public static final ModConfigSpec.BooleanValue SHOW_MACHINE_TITLE;
    public static final ModConfigSpec.BooleanValue AUTO_HIDE_TITLE_WITH_INFO_MODS;
    public static final ModConfigSpec.BooleanValue AUTO_HIDE_REDUNDANT_WITH_INFOHUD;
    public static final ModConfigSpec.BooleanValue SHOW_STATUS_BADGE;
    public static final ModConfigSpec.BooleanValue SHOW_ITEMS_THROUGHPUT;
    public static final ModConfigSpec.BooleanValue SHOW_FLUIDS_THROUGHPUT;
    public static final ModConfigSpec.BooleanValue SHOW_CHEMICALS_THROUGHPUT;
    public static final ModConfigSpec.BooleanValue SHOW_SPEED;
    public static final ModConfigSpec.BooleanValue SHOW_ROTATION_DIRECTION;
    public static final ModConfigSpec.BooleanValue SHOW_LOCAL_IMPACT;
    public static final ModConfigSpec.BooleanValue SHOW_NETWORK_STRESS;
    public static final ModConfigSpec.BooleanValue SHOW_HEADROOM;
    public static final ModConfigSpec.BooleanValue SHOW_ENERGY_FE;
    public static final ModConfigSpec.BooleanValue SHOW_SIDES;
    public static final ModConfigSpec.BooleanValue SHOW_AE2_POWER;
    public static final ModConfigSpec.BooleanValue SHOW_AE2_STABILITY;
    public static final ModConfigSpec.BooleanValue AUTO_HIDE_AE2_CHANNELS_WITH_INFOHUD;
    public static final ModConfigSpec.BooleanValue SHOW_AE2_CHANNELS;
    public static final ModConfigSpec.ConfigValue<String> AE2_POWER_UNIT;
    public static final ModConfigSpec.BooleanValue SHOW_STORAGE_CAPACITY;
    public static final ModConfigSpec.BooleanValue SHOW_TIME_TO_FULL;
    public static final ModConfigSpec.BooleanValue SHOW_TARGETED_FACE_IO;

    // Compact Formatting & Thresholds
    public static final ModConfigSpec.BooleanValue COMPACT_MODE;
    public static final ModConfigSpec.ConfigValue<String> COMPACT_TEMPLATE;
    public static final ModConfigSpec.DoubleValue STORAGE_WARNING_THRESHOLD;
    public static final ModConfigSpec.DoubleValue STORAGE_CRITICAL_THRESHOLD;
    public static final ModConfigSpec.IntValue ETA_WARNING_THRESHOLD_SECONDS;
    public static final ModConfigSpec.IntValue ETA_CRITICAL_THRESHOLD_SECONDS;

    // Metric Colors
    public static final ModConfigSpec.ConfigValue<String> COLOR_HEADER;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ITEMS;
    public static final ModConfigSpec.ConfigValue<String> COLOR_FLUIDS;
    public static final ModConfigSpec.ConfigValue<String> COLOR_CHEMICALS;
    public static final ModConfigSpec.ConfigValue<String> COLOR_KINETIC;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ENERGY_POSITIVE;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ENERGY_NEGATIVE;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ENERGY_NEUTRAL;
    public static final ModConfigSpec.ConfigValue<String> COLOR_SIDES;
    public static final ModConfigSpec.ConfigValue<String> COLOR_AE2_POWER;
    public static final ModConfigSpec.ConfigValue<String> COLOR_AE2_BROWNOUT;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STORAGE_NORMAL;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STORAGE_WARNING;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STORAGE_CRITICAL;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ETA_NORMAL;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ETA_WARNING;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ETA_CRITICAL;

    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_OPTIMAL;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_STARVED;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_CLOGGED;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_OVERLOADED;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_IDLE;
    public static final ModConfigSpec.ConfigValue<String> COLOR_IO_INPUT;
    public static final ModConfigSpec.ConfigValue<String> COLOR_IO_OUTPUT;
    public static final ModConfigSpec.ConfigValue<String> COLOR_IO_BIDIRECTIONAL;

    // Inspection Gate & Zoom
    public static final ModConfigSpec.BooleanValue REQUIRE_INSPECTION_ITEM;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INSPECTION_ITEM_TAGS;
    public static final ModConfigSpec.DoubleValue SPYGLASS_RAYCAST_DISTANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
                .comment("Scale multiplier for the entire HUD (0.5 to 3.0)")
                .defineInRange("hud_scale", 1.0, 0.5, 3.0);

        BACKGROUND_COLOR = builder
                .comment("Background color (hex ARGB, default #90000000)")
                .define("backgroundColor", "#90000000");

        BORDER_COLOR = builder
                .comment("Border color (hex ARGB, default #FF00FFCC)")
                .define("borderColor", "#FF00FFCC");

        PINNED_BORDER_COLOR = builder
                .comment("Border color for pinned telemetry cards (hex ARGB, default #FFFFD700)")
                .define("pinnedBorderColor", "#FFFFD700");

        builder.pop();

        builder.comment("Toggle Individual Metric Lines").push("elements");

        SHOW_MACHINE_TITLE = builder
                .comment("Show machine title at top of HUD")
                .define("show_machine_title", true);

        AUTO_HIDE_TITLE_WITH_INFO_MODS = builder
                .comment("Auto-hide machine title if Jade, WTHIT, or TOP is installed to avoid duplication")
                .define("auto_hide_title_with_info_mods", true);

        AUTO_HIDE_REDUNDANT_WITH_INFOHUD = builder
                .comment("Auto-hide items, fluids, and energy throughput if Jade, WTHIT, or TOP is active")
                .define("auto_hide_redundant_with_infohud", false);

        SHOW_STATUS_BADGE = builder
                .comment("Show machine status badge [OPTIMAL], [STARVED], [CLOGGED], [OVERLOADED]")
                .define("show_status_badge", true);

        SHOW_ITEMS_THROUGHPUT = builder
                .comment("Show items throughput rate")
                .define("show_items_throughput", true);

        SHOW_FLUIDS_THROUGHPUT = builder
                .comment("Show fluids throughput rate")
                .define("show_fluids_throughput", true);

        SHOW_CHEMICALS_THROUGHPUT = builder
                .comment("Show Mekanism chemicals throughput rate")
                .define("show_chemicals_throughput", true);

        SHOW_SPEED = builder
                .comment("Show kinetic speed (RPM)")
                .define("show_speed", true);

        SHOW_ROTATION_DIRECTION = builder
                .comment("Show kinetic rotation direction (CW/CCW)")
                .define("show_rotation_direction", true);

        SHOW_LOCAL_IMPACT = builder
                .comment("Show local kinetic stress impact (SU)")
                .define("show_local_impact", true);

        SHOW_NETWORK_STRESS = builder
                .comment("Show total network stress and capacity (SU)")
                .define("show_network_stress", true);

        SHOW_HEADROOM = builder
                .comment("Show remaining network headroom (SU)")
                .define("show_headroom", true);

        SHOW_ENERGY_FE = builder
                .comment("Show energy flow rate (FE/t)")
                .define("show_energy_fe", true);

        SHOW_SIDES = builder
                .comment("Show per-side metrics breakdown")
                .define("show_sides", true);

        SHOW_AE2_POWER = builder
                .comment("Show AE2 grid stored power, max power, drain rate, and network status")
                .define("show_ae2_power", true);

        SHOW_AE2_STABILITY = builder
                .comment("Show AE2 network stability badge [ONLINE], [BROWNOUT], [OFFLINE]")
                .define("show_ae2_stability", true);

        AUTO_HIDE_AE2_CHANNELS_WITH_INFOHUD = builder
                .comment("Auto-hide AE2 channel count if Jade/TOP provides it")
                .define("auto_hide_ae2_channels_with_infohud", true);

        SHOW_AE2_CHANNELS = builder
                .comment("Show AE2 cable channel usage (used/max channels)")
                .define("show_ae2_channels", true);

        AE2_POWER_UNIT = builder
                .comment("Unit for AE2 power display: AE or FE (2 FE = 1 AE)")
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

    public static int parseHexColor(ModConfigSpec.ConfigValue<String> configValue, int fallbackColor) {
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
