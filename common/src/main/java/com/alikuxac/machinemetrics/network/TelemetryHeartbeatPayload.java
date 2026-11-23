package com.alikuxac.machinemetrics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TelemetryHeartbeatPayload(BlockPos targetPos) implements CustomPacketPayload {

    public static final byte HEARTBEAT_MARKER = 1;

    public static final Type<TelemetryHeartbeatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("machinemetrics", "telemetry_heartbeat"));

    public static final StreamCodec<FriendlyByteBuf, TelemetryHeartbeatPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.targetPos);
                buf.writeByte(HEARTBEAT_MARKER);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                buf.readByte();
                return new TelemetryHeartbeatPayload(pos);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
