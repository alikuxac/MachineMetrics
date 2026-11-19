package com.alikuxac.machinemetrics.network;

import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.telemetry.DirectionalIo;
import com.alikuxac.machinemetrics.telemetry.RedstoneData;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        byte logisticsFlags,
        byte targetedFace,
        byte targetedIoDirection,
        byte targetedState,
        float targetedThroughput,
        RedstoneData redstoneData
) implements CustomPacketPayload {
    public static final Type<SyncTelemetryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("machinemetrics", "sync_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, SyncTelemetryPayload> STREAM_CODEC = 
            StreamCodec.of(SyncTelemetryPayload::encode, SyncTelemetryPayload::decode);

    public static SyncTelemetryPayload from(BlockPos pos, TelemetryData data) {
        return from(pos, data, data.serverDisabledMask());
    }

    public static SyncTelemetryPayload from(BlockPos pos, TelemetryData data, short serverDisabledMask) {
        byte dirByte = data.kineticDirection() != null ? (byte) data.kineticDirection().ordinal() : (byte) 2;
        byte faceByte = data.targetedFace() != null ? (byte) data.targetedFace().get3DDataValue() : (byte) -1;
        byte ioByte = data.targetedIo() != null ? data.targetedIo().getId() : (byte) 0;
        byte stateByte = data.targetedState() != null ? (byte) data.targetedState().ordinal() : (byte) TelemetryData.BottleneckState.IDLE.ordinal();
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
                data.logisticsFlags(),
                faceByte,
                ioByte,
                stateByte,
                data.targetedThroughput(),
                data.redstoneData()
        );
    }

    public TelemetryData toTelemetryData() {
        TelemetryData.BottleneckState[] states = TelemetryData.BottleneckState.values();
        TelemetryData.BottleneckState state = (machineState >= 0 && machineState < states.length) ? states[machineState] : TelemetryData.BottleneckState.IDLE;
        var dirs = CreateKineticInspector.RotationDirection.values();
        var dir = (kineticDirection >= 0 && kineticDirection < dirs.length) ? dirs[kineticDirection] : CreateKineticInspector.RotationDirection.STATIONARY;
        Direction face = (targetedFace >= 0 && targetedFace < 6) ? Direction.from3DDataValue(targetedFace) : null;
        DirectionalIo io = DirectionalIo.fromId(targetedIoDirection);
        TelemetryData.BottleneckState tState = (targetedState >= 0 && targetedState < states.length) ? states[targetedState] : TelemetryData.BottleneckState.IDLE;
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
                logisticsFlags,
                face,
                io,
                tState,
                targetedThroughput,
                redstoneData
        );
    }

    // Wire presence flags (which fields are present in the packet). Distinct from TelemetryData.MASK_*
    // (which fields are config-disabled); do not conflate the two bit sets.
    public static final int PRESENT_ITEMS     = 1 << 0;
    public static final int PRESENT_FLUIDS    = 1 << 1;
    public static final int PRESENT_ENERGY    = 1 << 2;
    public static final int PRESENT_KINETIC   = 1 << 3;
    public static final int PRESENT_CHEMICALS = 1 << 4;
    public static final int PRESENT_AE2       = 1 << 5;
    public static final int PRESENT_SIDES     = 1 << 6;
    public static final int PRESENT_DISABLED  = 1 << 7;
    public static final int PRESENT_STORAGE        = 1 << 8;
    public static final int PRESENT_TARGETED_FACE  = 1 << 9;
    public static final int PRESENT_REDSTONE       = 1 << 10;

    public static final int MAX_NAME_LENGTH = 64;

    private static final int KNOWN_FLAGS = PRESENT_ITEMS | PRESENT_FLUIDS | PRESENT_ENERGY | PRESENT_KINETIC
            | PRESENT_CHEMICALS | PRESENT_AE2 | PRESENT_SIDES | PRESENT_DISABLED | PRESENT_STORAGE | PRESENT_TARGETED_FACE
            | PRESENT_REDSTONE;

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
        if (payload.targetedFace >= 0 || payload.targetedIoDirection != 0 || payload.targetedThroughput > 0.001f) {
            flags |= PRESENT_TARGETED_FACE;
        }
        if (payload.redstoneData != null) {
            flags |= PRESENT_REDSTONE;
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

        if ((flags & PRESENT_TARGETED_FACE) != 0) {
            buf.writeByte(payload.targetedFace);
            buf.writeByte(payload.targetedIoDirection);
            buf.writeByte(payload.targetedState);
            buf.writeFloat(payload.targetedThroughput);
        }

        if ((flags & PRESENT_REDSTONE) != 0 && payload.redstoneData != null) {
            encodeRedstone(buf, payload.redstoneData);
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

        byte targetedFace = -1;
        byte targetedIoDirection = 0;
        byte targetedState = (byte) TelemetryData.BottleneckState.IDLE.ordinal();
        float targetedThroughput = 0.0f;
        if ((flags & PRESENT_TARGETED_FACE) != 0) {
            targetedFace = buf.readByte();
            targetedIoDirection = buf.readByte();
            targetedState = buf.readByte();
            targetedThroughput = buf.readFloat();
        }

        RedstoneData redstoneData = null;
        if ((flags & PRESENT_REDSTONE) != 0) {
            redstoneData = decodeRedstone(buf);
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
                logisticsFlags,
                targetedFace,
                targetedIoDirection,
                targetedState,
                targetedThroughput,
                redstoneData
        );
    }

    private static void encodeRedstone(FriendlyByteBuf buf, RedstoneData rs) {
        buf.writeByte(rs.type().ordinal());
        switch (rs.type()) {
            case WIRE -> {
                buf.writeByte(rs.power() & 0x0F);
            }
            case REPEATER -> {
                int packed = ((rs.delay() & 0x07) << 2) | (rs.locked() ? 2 : 0) | (rs.powered() ? 1 : 0);
                buf.writeByte(packed);
            }
            case COMPARATOR -> {
                int flags = ((rs.comparatorMode() & 0x03) << 2) | (rs.powered() ? 1 : 0);
                buf.writeByte(flags);
                int signals = ((rs.power() & 0x0F) << 4) | (rs.expectedOutput() & 0x0F);
                buf.writeByte(signals);
                int sources = ((rs.signalSource() & 0x0F) << 4) | (rs.maxSideSignal() & 0x0F);
                buf.writeByte(sources);
            }
            case CRAFTER -> {
                buf.writeShort(rs.disabledSlotsMask() & 0x01FF);
                buf.writeShort(rs.occupiedSlotsMask() & 0x01FF);
                int flags = (rs.crafting() ? 2 : 0) | (rs.powered() ? 1 : 0);
                buf.writeByte(flags);
                buf.writeByte(rs.power() & 0x0F);
            }
            case COPPER_BULB -> {
                int flags = (rs.lit() ? 2 : 0) | (rs.powered() ? 1 : 0);
                buf.writeByte(flags);
            }
        }
    }

    private static RedstoneData decodeRedstone(FriendlyByteBuf buf) {
        byte typeOrd = buf.readByte();
        RedstoneData.RedstoneType[] types = RedstoneData.RedstoneType.values();
        RedstoneData.RedstoneType type = (typeOrd >= 0 && typeOrd < types.length) ? types[typeOrd] : RedstoneData.RedstoneType.WIRE;
        switch (type) {
            case WIRE -> {
                int power = buf.readByte() & 0x0F;
                return new RedstoneData(type, power, 0, false, power > 0, false, false, RedstoneData.MODE_NONE, 0, 0, 0, 0, 0);
            }
            case REPEATER -> {
                int packed = buf.readByte() & 0xFF;
                int delay = (packed >> 2) & 0x07;
                boolean locked = (packed & 2) != 0;
                boolean powered = (packed & 1) != 0;
                return new RedstoneData(type, powered ? 15 : 0, delay, locked, powered, false, false, RedstoneData.MODE_NONE, 0, 0, 0, 0, 0);
            }
            case COMPARATOR -> {
                int flags = buf.readByte() & 0xFF;
                byte mode = (byte) ((flags >> 2) & 0x03);
                boolean powered = (flags & 1) != 0;
                int signals = buf.readByte() & 0xFF;
                int power = (signals >> 4) & 0x0F;
                int expected = signals & 0x0F;
                int sources = buf.readByte() & 0xFF;
                int source = (sources >> 4) & 0x0F;
                int side = sources & 0x0F;
                return new RedstoneData(type, power, 0, false, powered, false, false, mode, expected, source, side, 0, 0);
            }
            case CRAFTER -> {
                int disabled = buf.readShort() & 0x01FF;
                int occupied = buf.readShort() & 0x01FF;
                int flags = buf.readByte() & 0xFF;
                boolean crafting = (flags & 2) != 0;
                boolean powered = (flags & 1) != 0;
                int signal = buf.readByte() & 0x0F;
                return new RedstoneData(type, signal, 0, false, powered, false, crafting, RedstoneData.MODE_NONE, 0, 0, 0, disabled, occupied);
            }
            case COPPER_BULB -> {
                int flags = buf.readByte() & 0xFF;
                boolean lit = (flags & 2) != 0;
                boolean powered = (flags & 1) != 0;
                return new RedstoneData(type, lit ? 15 : 0, 0, false, powered, lit, false, RedstoneData.MODE_NONE, 0, 0, 0, 0, 0);
            }
        }
        return null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
