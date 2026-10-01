package com.alikuxac.machinemetrics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class TelemetryConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;

    // Configurable Colors (HEX string format, e.g. "0x00FFCC" or "#00FFCC")
    public static final ModConfigSpec.ConfigValue<String> COLOR_HEADER;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ITEMS;
    public static final ModConfigSpec.ConfigValue<String> COLOR_FLUIDS;
    public static final ModConfigSpec.ConfigValue<String> COLOR_CHEMICALS;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ENERGY_POSITIVE;

    public static final ModConfigSpec.ConfigValue<String> COLOR_ENERGY_NEGATIVE;
    public static final ModConfigSpec.ConfigValue<String> COLOR_ENERGY_NEUTRAL;
    public static final ModConfigSpec.ConfigValue<String> COLOR_SIDES;

    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_OPTIMAL;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_STARVED;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_CLOGGED;
    public static final ModConfigSpec.ConfigValue<String> COLOR_STATE_IDLE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Telemetry Debug Configuration").push("debug");

        DEBUG_LOGGING = builder
                .comment("Enable detailed telemetry debug logging in game console")
                .define("enableDebugLogging", false);

        builder.pop();

        builder.comment("Telemetry HUD Visual Configuration").push("hud");

        HUD_SCALE = builder
                .comment("Custom HUD scale multiplier (1.0 = normal UI scale, 0.5 = small, 1.5 = large)")
                .defineInRange("hudScale", 1.0, 0.5, 3.0);

        builder.pop();

        builder.comment("Telemetry HUD Color Customization (Hex String Format: e.g. #00FFCC or 0x00FFCC)").push("colors");

        COLOR_HEADER = builder
                .comment("Header text and outline color")
                .define("headerColor", "#00FFCC");

        COLOR_ITEMS = builder
                .comment("Items metric color")
                .define("itemsColor", "#FFD54F");

        COLOR_FLUIDS = builder
                .comment("Fluids metric color")
                .define("fluidsColor", "#4FC3F7");

        COLOR_CHEMICALS = builder
                .comment("Chemicals metric color")
                .define("chemicalsColor", "#AEEA00");

        COLOR_ENERGY_POSITIVE = builder

                .comment("Positive energy rate color (+FE/t)")
                .define("energyPositiveColor", "#69F0AE");

        COLOR_ENERGY_NEGATIVE = builder
                .comment("Negative energy rate color (-FE/t)")
                .define("energyNegativeColor", "#FF5252");

        COLOR_ENERGY_NEUTRAL = builder
                .comment("Neutral energy color")
                .define("energyNeutralColor", "#E040FB");

        COLOR_SIDES = builder
                .comment("Side/Direction metrics color")
                .define("sidesColor", "#69F0AE");

        COLOR_STATE_OPTIMAL = builder
                .comment("OPTIMAL status state color")
                .define("stateOptimalColor", "#55FF55");

        COLOR_STATE_STARVED = builder
                .comment("STARVED status state color")
                .define("stateStarvedColor", "#FFFF55");

        COLOR_STATE_CLOGGED = builder
                .comment("CLOGGED status state color")
                .define("stateCloggedColor", "#FF5555");

        COLOR_STATE_IDLE = builder
                .comment("IDLE status state color")
                .define("stateIdleColor", "#AAAAAA");

        builder.pop();
        SPEC = builder.build();
    }

    public static int parseHexColor(ModConfigSpec.ConfigValue<String> configValue, int fallbackColor) {
        if (configValue == null || configValue.get() == null) return fallbackColor;
        try {
            String hex = configValue.get().trim().replace("#", "").replace("0x", "").replace("0X", "");
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return fallbackColor;
        }
    }
}
