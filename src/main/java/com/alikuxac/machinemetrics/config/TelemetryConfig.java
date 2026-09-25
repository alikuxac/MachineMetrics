package com.alikuxac.machinemetrics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class TelemetryConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    public static final ModConfigSpec.DoubleValue HUD_SCALE;

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
        SPEC = builder.build();
    }
}
