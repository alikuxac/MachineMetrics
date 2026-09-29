package com.alikuxac.machinemetrics.platform;

import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiFunction;
import java.util.function.Function;

public class Services {
    public static Function<BlockPos, TelemetryData> CLIENT_SAMPLER = pos -> TelemetryData.EMPTY;
    public static BiFunction<ServerLevel, BlockPos, TelemetryData> SERVER_SAMPLER = (level, pos) -> TelemetryData.EMPTY;
    public static NetworkSender NETWORK_SENDER = (pos) -> {};

    @FunctionalInterface
    public interface NetworkSender {
        void sendRequest(BlockPos pos);
    }
}
