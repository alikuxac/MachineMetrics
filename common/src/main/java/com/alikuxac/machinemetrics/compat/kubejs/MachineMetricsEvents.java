package com.alikuxac.machinemetrics.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public final class MachineMetricsEvents {

    public static final EventGroup GROUP = EventGroup.of("MachineMetricsEvents");

    public static final EventHandler REGISTER_BLOCK_METRICS = GROUP.client("registerBlockMetrics", () -> BlockMetricEventJS.class);

    public static final EventHandler SHOULD_INSPECT = GROUP.client("shouldInspect", () -> ShouldInspectEventJS.class).hasResult();

    private MachineMetricsEvents() {
    }
}
