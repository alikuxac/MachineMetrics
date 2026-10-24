package com.alikuxac.machinemetrics.network;
 
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestTelemetryPayload(BlockPos targetPos, Direction hitFace) implements CustomPacketPayload {
    public static final Type<RequestTelemetryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("machinemetrics", "request_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, RequestTelemetryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.targetPos);
                buf.writeByte(payload.hitFace != null ? payload.hitFace.get3DDataValue() : -1);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                byte faceByte = buf.readByte();
                Direction face = faceByte >= 0 && faceByte < 6 ? Direction.from3DDataValue(faceByte) : null;
                return new RequestTelemetryPayload(pos, face);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
