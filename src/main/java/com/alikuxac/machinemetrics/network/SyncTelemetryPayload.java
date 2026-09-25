package com.alikuxac.machinemetrics.network;

import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncTelemetryPayload(
        BlockPos targetPos,
        float itemThroughput,
        boolean hasItems,
        float fluidThroughput,
        boolean hasFluids,
        float energyDelta,
        boolean hasEnergy,
        byte machineState
) implements CustomPacketPayload {
    public static final Type<SyncTelemetryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("machinemetrics", "sync_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SyncTelemetryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.targetPos());
                buf.writeFloat(payload.itemThroughput());
                buf.writeBoolean(payload.hasItems());
                buf.writeFloat(payload.fluidThroughput());
                buf.writeBoolean(payload.hasFluids());
                buf.writeFloat(payload.energyDelta());
                buf.writeBoolean(payload.hasEnergy());
                buf.writeByte(payload.machineState());
            },
            buf -> new SyncTelemetryPayload(
                BlockPos.STREAM_CODEC.decode(buf),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readByte()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncTelemetryPayload from(BlockPos pos, TelemetryData data) {
        byte stateByte = switch (data.bottleneckState()) {
            case OPTIMAL -> (byte) 0;
            case STARVED -> (byte) 1;
            case CLOGGED -> (byte) 2;
            case IDLE -> (byte) 3;
        };
        return new SyncTelemetryPayload(
                pos,
                data.itemThroughput(),
                data.hasItems(),
                data.fluidThroughput(),
                data.hasFluids(),
                data.energyDelta(),
                data.hasEnergy(),
                stateByte
        );
    }

    public TelemetryData toTelemetryData() {
        TelemetryData.BottleneckState state = switch (machineState) {
            case 0 -> TelemetryData.BottleneckState.OPTIMAL;
            case 1 -> TelemetryData.BottleneckState.STARVED;
            case 2 -> TelemetryData.BottleneckState.CLOGGED;
            default -> TelemetryData.BottleneckState.IDLE;
        };
        return new TelemetryData(itemThroughput, hasItems, fluidThroughput, hasFluids, energyDelta, hasEnergy, state);
    }
}
