package com.alikuxac.machinemetrics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Telemetry Server & Logic Configuration").push("debug");

        DEBUG_LOGGING = builder
                .comment("Enable detailed telemetry debug logging in game console")
                .define("enableDebugLogging", false);

        builder.pop();
        SPEC = builder.build();
    }
}
