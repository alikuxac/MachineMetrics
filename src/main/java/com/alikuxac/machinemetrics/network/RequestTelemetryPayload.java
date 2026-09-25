package com.alikuxac.machinemetrics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestTelemetryPayload(BlockPos targetPos) implements CustomPacketPayload {
    public static final Type<RequestTelemetryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("machinemetrics", "request_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, RequestTelemetryPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestTelemetryPayload::targetPos,
            RequestTelemetryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
