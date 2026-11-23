package com.alikuxac.machinemetrics.config;

import com.alikuxac.machinemetrics.platform.config.Config;

public class ServerConfig {

    public static final Config.Spec SPEC;

    // Feature Toggles (default: true)
    public static final Config.Value<Boolean> ALLOW_ITEMS;
    public static final Config.Value<Boolean> ALLOW_FLUIDS;
    public static final Config.Value<Boolean> ALLOW_ENERGY;
    public static final Config.Value<Boolean> ALLOW_KINETIC;
    public static final Config.Value<Boolean> ALLOW_CHEMICALS;
    public static final Config.Value<Boolean> ALLOW_SIDE_METRICS;
    public static final Config.Value<Boolean> ALLOW_AE2;
    public static final Config.Value<Boolean> ALLOW_REDSTONE;

    // Debug
    public static final Config.Value<Boolean> DEBUG_LOGGING;

    // Synchronization & Throttling
    public static final Config.Value<Double> STORAGE_SYNC_DEADBAND_PERCENT;
    public static final Config.Value<Double> MAX_REQUEST_RANGE;
    public static final Config.Value<Integer> MAX_REQUESTS_PER_SECOND;
    public static final Config.Value<Double> MAX_PINNED_DISTANCE;
    public static final Config.Value<Boolean> ALLOW_PINNED_IN_UNLOADED_CHUNKS;

    static {
        Config.Builder builder = Config.builder();

        builder.comment("Server Telemetry Metric Permissions (What data the server samples and sends to clients)")
                .push("features");

        ALLOW_ITEMS = builder
                .comment("Allow sending item transfer throughput metrics to clients")
                .define("allow_items_telemetry", true);

        ALLOW_FLUIDS = builder
                .comment("Allow sending fluid transfer throughput metrics to clients")
                .define("allow_fluids_telemetry", true);

        ALLOW_ENERGY = builder
                .comment("Allow sending Forge Energy (FE/t) rate metrics to clients")
                .define("allow_energy_telemetry", true);

        ALLOW_KINETIC = builder
                .comment("Allow sending Create kinetic rotational metrics (Speed, Stress, Capacity) to clients")
                .define("allow_kinetic_telemetry", true);

        ALLOW_CHEMICALS = builder
                .comment("Allow sending Mekanism chemical metrics (Gases, Infuse Types, Pigments, Slurries) to clients")
                .define("allow_chemicals_telemetry", true);

        ALLOW_SIDE_METRICS = builder
                .comment("Allow sending per-side / per-face throughput metrics to clients")
                .define("allow_side_metrics_telemetry", true);

        ALLOW_AE2 = builder
                .comment("Allow sending Applied Energistics 2 grid power and stability metrics to clients")
                .define("allow_ae2_telemetry", true);

        ALLOW_REDSTONE = builder
                .comment("Allow sending Redstone and vanilla automation metrics to clients")
                .define("allow_redstone_telemetry", true);

        builder.pop();

        builder.comment("Telemetry Server & Logic Configuration").push("debug");

        DEBUG_LOGGING = builder
                .comment("Enable detailed telemetry debug logging in game console")
                .define("enableDebugLogging", false);

        builder.pop();

        builder.comment("Telemetry Synchronization & Throttling Configuration").push("sync");

        STORAGE_SYNC_DEADBAND_PERCENT = builder
                .comment("Storage synchronization deadband percentage (only sync when container fill percentage changes by more than this threshold)")
                .defineInRange("storage_sync_deadband_percent", 1.0, 0.0, 100.0);

        MAX_REQUEST_RANGE = builder
                .comment("Maximum distance (in blocks) between the player and the block they may request telemetry for")
                .defineInRange("max_request_range", 12.0, 1.0, 64.0);

        MAX_REQUESTS_PER_SECOND = builder
                .comment("Maximum telemetry request packets processed per player per second (0 = unlimited)")
                .defineInRange("max_requests_per_second", 20, 0, 200);

        MAX_PINNED_DISTANCE = builder
                .comment("Maximum distance (in blocks) a player may keep a pinned target polled. "
                        + "The tighter of max_request_range and this value is used for pinned requests.")
                .defineInRange("max_pinned_distance", 12.0, 1.0, 64.0);

        ALLOW_PINNED_IN_UNLOADED_CHUNKS = builder
                .comment("Allow pinned targets in unloaded chunks. Disabled by default to prevent "
                        + "remote chunk loading and cross-chunk capability polling on dedicated servers.")
                .define("allow_pinned_in_unloaded_chunks", false);

        builder.pop();
        SPEC = builder.build();
    }
}
