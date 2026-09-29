package com.alikuxac.machinemetrics.network;

import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
        byte machineState,
        float[] sideMetrics,
        IntArrayList allSlotCounts,
        IntArrayList allTankAmounts
) implements CustomPacketPayload {
    public static final Type<SyncTelemetryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("machinemetrics", "sync_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SyncTelemetryPayload> STREAM_CODEC = 
            StreamCodec.of(SyncTelemetryPayload::encode, SyncTelemetryPayload::decode);

    public static SyncTelemetryPayload from(BlockPos pos, TelemetryData data) {
        return new SyncTelemetryPayload(
                pos,
                data.itemThroughput(),
                data.hasItems(),
                data.fluidThroughput(),
                data.hasFluids(),
                data.energyDelta(),
                data.hasEnergy(),
                (byte) data.bottleneckState().ordinal(),
                data.sideMetrics(),
                data.allSlotCounts(),
                data.allTankAmounts()
        );
    }

    public TelemetryData toTelemetryData() {
        TelemetryData.BottleneckState[] states = TelemetryData.BottleneckState.values();
        TelemetryData.BottleneckState state = (machineState >= 0 && machineState < states.length) ? states[machineState] : TelemetryData.BottleneckState.IDLE;
        return new TelemetryData(
                itemThroughput,
                hasItems,
                fluidThroughput,
                hasFluids,
                energyDelta,
                hasEnergy,
                state,
                allSlotCounts != null ? allSlotCounts : new IntArrayList(),
                allTankAmounts != null ? allTankAmounts : new IntArrayList(),
                sideMetrics != null ? sideMetrics : new float[12]
        );
    }

    private static void encode(FriendlyByteBuf buf, SyncTelemetryPayload payload) {
        buf.writeBlockPos(payload.targetPos);
        buf.writeFloat(payload.itemThroughput);
        buf.writeBoolean(payload.hasItems);
        buf.writeFloat(payload.fluidThroughput);
        buf.writeBoolean(payload.hasFluids);
        buf.writeFloat(payload.energyDelta);
        buf.writeBoolean(payload.hasEnergy);
        buf.writeByte(payload.machineState);

        IntArrayList slots = payload.allSlotCounts;
        int slotSize = slots != null ? slots.size() : 0;
        buf.writeVarInt(slotSize);
        for (int i = 0; i < slotSize; i++) {
            buf.writeVarInt(slots.getInt(i));
        }

        IntArrayList tanks = payload.allTankAmounts;
        int tankSize = tanks != null ? tanks.size() : 0;
        buf.writeVarInt(tankSize);
        for (int i = 0; i < tankSize; i++) {
            buf.writeVarInt(tanks.getInt(i));
        }

        float[] sideMetrics = payload.sideMetrics;
        for (int i = 0; i < 12; i++) {
            buf.writeFloat(sideMetrics != null && i < sideMetrics.length ? sideMetrics[i] : 0.0f);
        }
    }

    private static SyncTelemetryPayload decode(FriendlyByteBuf buf) {
        BlockPos targetPos = buf.readBlockPos();
        float itemThroughput = buf.readFloat();
        boolean hasItems = buf.readBoolean();
        float fluidThroughput = buf.readFloat();
        boolean hasFluids = buf.readBoolean();
        float energyDelta = buf.readFloat();
        boolean hasEnergy = buf.readBoolean();
        byte machineState = buf.readByte();

        int slotCount = buf.readVarInt();
        IntArrayList allSlotCounts = new IntArrayList(slotCount);
        for (int i = 0; i < slotCount; i++) {
            allSlotCounts.add(buf.readVarInt());
        }

        int tankCount = buf.readVarInt();
        IntArrayList allTankAmounts = new IntArrayList(tankCount);
        for (int i = 0; i < tankCount; i++) {
            allTankAmounts.add(buf.readVarInt());
        }

        float[] sideMetrics = new float[12];
        for (int i = 0; i < 12; i++) {
            sideMetrics[i] = buf.readFloat();
        }

        return new SyncTelemetryPayload(
                targetPos,
                itemThroughput,
                hasItems,
                fluidThroughput,
                hasFluids,
                energyDelta,
                hasEnergy,
                machineState,
                sideMetrics,
                allSlotCounts,
                allTankAmounts
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

