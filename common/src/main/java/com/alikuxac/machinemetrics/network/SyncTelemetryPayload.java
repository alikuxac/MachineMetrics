package com.alikuxac.machinemetrics.network;

import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncTelemetryPayload(
        BlockPos targetPos,
        float itemThroughput,
        boolean hasItems,
        float fluidThroughput,
        boolean hasFluids,
        String fluidName,
        long fluidAmount,
        long fluidCapacity,
        float energyDelta,
        boolean hasEnergy,
        float chemicalThroughput,
        boolean hasChemicals,
        String chemicalName,
        long chemicalAmount,
        long chemicalCapacity,
        float kineticSpeed,
        float kineticStress,
        float kineticCapacity,
        float kineticBlockStress,
        boolean hasKinetic,
        byte kineticDirection,
        byte machineState,
        float[] sideMetrics,
        short serverDisabledMask,
        boolean hasAe2Network,
        float aePowerRate,
        float aePowerDrain,
        float aeSubnetDrain,
        int aeSubnetCount,
        double aeStoredPower,
        double aeMaxPower,
        byte aeStabilityState,
        boolean aeIsCreative,
        int aeUsedChannels,
        int aeMaxChannels,
        long storageCurrent,
        long storageMax,
        float netRate,
        byte logisticsFlags
) implements CustomPacketPayload {
    public static final Type<SyncTelemetryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("machinemetrics", "sync_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SyncTelemetryPayload> STREAM_CODEC = 
            StreamCodec.of(SyncTelemetryPayload::encode, SyncTelemetryPayload::decode);

    public static SyncTelemetryPayload from(BlockPos pos, TelemetryData data) {
        return from(pos, data, data.serverDisabledMask());
    }

    public static SyncTelemetryPayload from(BlockPos pos, TelemetryData data, short serverDisabledMask) {
        byte dirByte = data.kineticDirection() != null ? (byte) data.kineticDirection().ordinal() : (byte) 2;
        return new SyncTelemetryPayload(
                pos,
                data.itemThroughput(),
                data.hasItems(),
                data.fluidThroughput(),
                data.hasFluids(),
                data.fluidName() != null ? data.fluidName() : "",
                data.fluidAmount(),
                data.fluidCapacity(),
                data.energyDelta(),
                data.hasEnergy(),
                data.chemicalThroughput(),
                data.hasChemicals(),
                data.chemicalName() != null ? data.chemicalName() : "",
                data.chemicalAmount(),
                data.chemicalCapacity(),
                data.kineticSpeed(),
                data.kineticStress(),
                data.kineticCapacity(),
                data.kineticBlockStress(),
                data.hasKinetic(),
                dirByte,
                (byte) data.bottleneckState().ordinal(),
                data.sideMetrics(),
                serverDisabledMask,
                data.hasAe2Network(),
                data.aePowerRate(),
                data.aePowerDrain(),
                data.aeSubnetDrain(),
                data.aeSubnetCount(),
                data.aeStoredPower(),
                data.aeMaxPower(),
                data.aeStabilityState(),
                data.aeIsCreative(),
                data.aeUsedChannels(),
                data.aeMaxChannels(),
                data.storageCurrent(),
                data.storageMax(),
                data.netRate(),
                data.logisticsFlags()
        );
    }

    public TelemetryData toTelemetryData() {
        TelemetryData.BottleneckState[] states = TelemetryData.BottleneckState.values();
        TelemetryData.BottleneckState state = (machineState >= 0 && machineState < states.length) ? states[machineState] : TelemetryData.BottleneckState.IDLE;
        var dirs = CreateKineticInspector.RotationDirection.values();
        var dir = (kineticDirection >= 0 && kineticDirection < dirs.length) ? dirs[kineticDirection] : CreateKineticInspector.RotationDirection.STATIONARY;
        return new TelemetryData(
                itemThroughput,
                hasItems,
                fluidThroughput,
                hasFluids,
                fluidName,
                fluidAmount,
                fluidCapacity,
                energyDelta,
                hasEnergy,
                chemicalThroughput,
                hasChemicals,
                chemicalName,
                chemicalAmount,
                chemicalCapacity,
                kineticSpeed,
                kineticStress,
                kineticCapacity,
                kineticBlockStress,
                hasKinetic,
                dir,
                state,
                new IntArrayList(),
                new IntArrayList(),
                sideMetrics != null ? sideMetrics : new float[18],
                serverDisabledMask,
                hasAe2Network,
                aePowerRate,
                aePowerDrain,
                aeSubnetDrain,
                aeSubnetCount,
                aeStoredPower,
                aeMaxPower,
                aeStabilityState,
                aeIsCreative,
                aeUsedChannels,
                aeMaxChannels,
                storageCurrent,
                storageMax,
                netRate,
                logisticsFlags
        );
    }

    public static final int PRESENT_ITEMS     = 1 << 0;
    public static final int PRESENT_FLUIDS    = 1 << 1;
    public static final int PRESENT_ENERGY    = 1 << 2;
    public static final int PRESENT_KINETIC   = 1 << 3;
    public static final int PRESENT_CHEMICALS = 1 << 4;
    public static final int PRESENT_AE2       = 1 << 5;
    public static final int PRESENT_SIDES     = 1 << 6;
    public static final int PRESENT_DISABLED  = 1 << 7;
    public static final int PRESENT_STORAGE   = 1 << 8;

    public static final int MAX_NAME_LENGTH = 64;

    private static final int KNOWN_FLAGS = PRESENT_ITEMS | PRESENT_FLUIDS | PRESENT_ENERGY | PRESENT_KINETIC
            | PRESENT_CHEMICALS | PRESENT_AE2 | PRESENT_SIDES | PRESENT_DISABLED | PRESENT_STORAGE;

    private static boolean hasActiveSides(float[] sides) {
        if (sides == null) return false;
        for (float val : sides) {
            if (val > 0.01f) return true;
        }
        return false;
    }

    private static void encode(FriendlyByteBuf buf, SyncTelemetryPayload payload) {
        int flags = 0;
        if (payload.hasItems || payload.itemThroughput > 0.001f) {
            flags |= PRESENT_ITEMS;
        }
        if (payload.hasFluids || payload.fluidThroughput > 0.001f || payload.fluidCapacity > 0) {
            flags |= PRESENT_FLUIDS;
        }
        if (payload.hasEnergy || Math.abs(payload.energyDelta) > 0.001f) {
            flags |= PRESENT_ENERGY;
        }
        if (payload.hasKinetic || Math.abs(payload.kineticSpeed) > 0.001f || payload.kineticCapacity > 0.001f) {
            flags |= PRESENT_KINETIC;
        }
        if (payload.hasChemicals || payload.chemicalThroughput > 0.001f || payload.chemicalCapacity > 0) {
            flags |= PRESENT_CHEMICALS;
        }
        if (payload.hasAe2Network) {
            flags |= PRESENT_AE2;
        }
        if (hasActiveSides(payload.sideMetrics)) {
            flags |= PRESENT_SIDES;
        }
        if (payload.serverDisabledMask != 0) {
            flags |= PRESENT_DISABLED;
        }
        if (payload.storageMax > 0 || payload.storageCurrent > 0 || (payload.logisticsFlags & TelemetryData.FLAG_LOGISTICS_HAS_STORAGE) != 0 || Math.abs(payload.netRate) > 0.001f) {
            flags |= PRESENT_STORAGE;
        }

        buf.writeBlockPos(payload.targetPos);
        buf.writeByte(payload.machineState);
        buf.writeVarInt(flags);

        if ((flags & PRESENT_DISABLED) != 0) {
            buf.writeShort(payload.serverDisabledMask);
        }

        if ((flags & PRESENT_ITEMS) != 0) {
            buf.writeFloat(payload.itemThroughput);
            buf.writeBoolean(payload.hasItems);
        }

        if ((flags & PRESENT_FLUIDS) != 0) {
            buf.writeFloat(payload.fluidThroughput);
            buf.writeBoolean(payload.hasFluids);
            buf.writeUtf(payload.fluidName != null ? payload.fluidName : "", MAX_NAME_LENGTH);
            buf.writeVarLong(payload.fluidAmount);
            buf.writeVarLong(payload.fluidCapacity);
        }

        if ((flags & PRESENT_ENERGY) != 0) {
            buf.writeFloat(payload.energyDelta);
            buf.writeBoolean(payload.hasEnergy);
        }

        if ((flags & PRESENT_KINETIC) != 0) {
            buf.writeFloat(payload.kineticSpeed);
            buf.writeFloat(payload.kineticStress);
            buf.writeFloat(payload.kineticCapacity);
            buf.writeFloat(payload.kineticBlockStress);
            buf.writeByte(payload.kineticDirection);
        }

        if ((flags & PRESENT_CHEMICALS) != 0) {
            buf.writeFloat(payload.chemicalThroughput);
            buf.writeUtf(payload.chemicalName != null ? payload.chemicalName : "", MAX_NAME_LENGTH);
            buf.writeVarLong(payload.chemicalAmount);
            buf.writeVarLong(payload.chemicalCapacity);
        }

        if ((flags & PRESENT_AE2) != 0) {
            buf.writeFloat(payload.aePowerRate);
            buf.writeFloat(payload.aePowerDrain);
            buf.writeFloat(payload.aeSubnetDrain);
            buf.writeVarInt(payload.aeSubnetCount);
            buf.writeDouble(payload.aeStoredPower);
            buf.writeDouble(payload.aeMaxPower);
            buf.writeByte(payload.aeStabilityState);
            buf.writeBoolean(payload.aeIsCreative);
            buf.writeVarInt(payload.aeUsedChannels);
            buf.writeVarInt(payload.aeMaxChannels);
        }

        if ((flags & PRESENT_SIDES) != 0) {
            int nonZeroMask = 0;
            for (int i = 0; i < 18; i++) {
                if (payload.sideMetrics != null && i < payload.sideMetrics.length && payload.sideMetrics[i] > 0.01f) {
                    nonZeroMask |= (1 << i);
                }
            }
            buf.writeVarInt(nonZeroMask);
            for (int i = 0; i < 18; i++) {
                if ((nonZeroMask & (1 << i)) != 0) {
                    buf.writeFloat(payload.sideMetrics[i]);
                }
            }
        }

        if ((flags & PRESENT_STORAGE) != 0) {
            buf.writeVarLong(payload.storageCurrent);
            buf.writeVarLong(payload.storageMax);
            buf.writeFloat(payload.netRate);
            buf.writeByte(payload.logisticsFlags);
        }
    }

    private static SyncTelemetryPayload decode(FriendlyByteBuf buf) {
        BlockPos targetPos = buf.readBlockPos();
        byte machineState = buf.readByte();
        int flags = buf.readVarInt() & KNOWN_FLAGS;

        short serverDisabledMask = (flags & PRESENT_DISABLED) != 0 ? buf.readShort() : (short) 0;

        float itemThroughput = 0.0f;
        boolean hasItems = false;
        if ((flags & PRESENT_ITEMS) != 0) {
            itemThroughput = buf.readFloat();
            hasItems = buf.readBoolean();
        }

        float fluidThroughput = 0.0f;
        boolean hasFluids = false;
        String fluidName = "";
        long fluidAmount = 0L;
        long fluidCapacity = 0L;
        if ((flags & PRESENT_FLUIDS) != 0) {
            fluidThroughput = buf.readFloat();
            hasFluids = buf.readBoolean();
            fluidName = buf.readUtf(MAX_NAME_LENGTH);
            fluidAmount = buf.readVarLong();
            fluidCapacity = buf.readVarLong();
        }

        float energyDelta = 0.0f;
        boolean hasEnergy = false;
        if ((flags & PRESENT_ENERGY) != 0) {
            energyDelta = buf.readFloat();
            hasEnergy = buf.readBoolean();
        }

        float kineticSpeed = 0.0f;
        float kineticStress = 0.0f;
        float kineticCapacity = 0.0f;
        float kineticBlockStress = 0.0f;
        boolean hasKinetic = (flags & PRESENT_KINETIC) != 0;
        byte kineticDirection = 2;
        if (hasKinetic) {
            kineticSpeed = buf.readFloat();
            kineticStress = buf.readFloat();
            kineticCapacity = buf.readFloat();
            kineticBlockStress = buf.readFloat();
            kineticDirection = buf.readByte();
        }

        float chemicalThroughput = 0.0f;
        boolean hasChemicals = (flags & PRESENT_CHEMICALS) != 0;
        String chemicalName = "";
        long chemicalAmount = 0L;
        long chemicalCapacity = 0L;
        if (hasChemicals) {
            chemicalThroughput = buf.readFloat();
            chemicalName = buf.readUtf(MAX_NAME_LENGTH);
            chemicalAmount = buf.readVarLong();
            chemicalCapacity = buf.readVarLong();
        }

        boolean hasAe2Network = (flags & PRESENT_AE2) != 0;
        float aePowerRate = 0.0f;
        float aePowerDrain = 0.0f;
        float aeSubnetDrain = 0.0f;
        int aeSubnetCount = 0;
        double aeStoredPower = 0.0;
        double aeMaxPower = 0.0;
        byte aeStabilityState = 0;
        boolean aeIsCreative = false;
        int aeUsedChannels = 0;
        int aeMaxChannels = 0;
        if (hasAe2Network) {
            aePowerRate = buf.readFloat();
            aePowerDrain = buf.readFloat();
            aeSubnetDrain = buf.readFloat();
            aeSubnetCount = buf.readVarInt();
            aeStoredPower = buf.readDouble();
            aeMaxPower = buf.readDouble();
            aeStabilityState = buf.readByte();
            aeIsCreative = buf.readBoolean();
            aeUsedChannels = buf.readVarInt();
            aeMaxChannels = buf.readVarInt();
        }

        float[] sideMetrics = new float[18];
        if ((flags & PRESENT_SIDES) != 0) {
            int nonZeroMask = buf.readVarInt();
            for (int i = 0; i < 18; i++) {
                if ((nonZeroMask & (1 << i)) != 0) {
                    sideMetrics[i] = buf.readFloat();
                }
            }
        }

        long storageCurrent = 0L;
        long storageMax = 0L;
        float netRate = 0.0f;
        byte logisticsFlags = (byte) 0;
        if ((flags & PRESENT_STORAGE) != 0) {
            storageCurrent = buf.readVarLong();
            storageMax = buf.readVarLong();
            netRate = buf.readFloat();
            logisticsFlags = buf.readByte();
        }

        return new SyncTelemetryPayload(
                targetPos,
                itemThroughput,
                hasItems,
                fluidThroughput,
                hasFluids,
                fluidName,
                fluidAmount,
                fluidCapacity,
                energyDelta,
                hasEnergy,
                chemicalThroughput,
                hasChemicals,
                chemicalName,
                chemicalAmount,
                chemicalCapacity,
                kineticSpeed,
                kineticStress,
                kineticCapacity,
                kineticBlockStress,
                hasKinetic,
                kineticDirection,
                machineState,
                sideMetrics,
                serverDisabledMask,
                hasAe2Network,
                aePowerRate,
                aePowerDrain,
                aeSubnetDrain,
                aeSubnetCount,
                aeStoredPower,
                aeMaxPower,
                aeStabilityState,
                aeIsCreative,
                aeUsedChannels,
                aeMaxChannels,
                storageCurrent,
                storageMax,
                netRate,
                logisticsFlags
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
