package com.alikuxac.machinemetrics.platform;

import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiFunction;

public class Services {
    @FunctionalInterface
    public interface ClientSampler {
        TelemetryData sample(BlockPos pos, Direction hitFace);
    }

    @FunctionalInterface
    public interface ServerSampler {
        TelemetryData sample(ServerLevel level, BlockPos pos, Direction hitFace);
    }

    public static ClientSampler CLIENT_SAMPLER = (pos, face) -> TelemetryData.EMPTY;
    public static ServerSampler SERVER_SAMPLER = (level, pos, face) -> TelemetryData.EMPTY;
    public static NetworkSender NETWORK_SENDER = (pos, face) -> {};

    @FunctionalInterface
    public interface NetworkSender {
        void sendRequest(BlockPos pos, Direction face);
    }
}
