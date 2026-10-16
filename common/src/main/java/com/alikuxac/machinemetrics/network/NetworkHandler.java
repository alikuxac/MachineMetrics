package com.alikuxac.machinemetrics.network;

import com.alikuxac.machinemetrics.client.ClientTelemetryCache;
import com.alikuxac.machinemetrics.platform.Services;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandler {

    private record SentEntry(TelemetryData data, long timestampMs) {}
    private static final Map<BlockPos, SentEntry> LAST_SENT_TO_CLIENT = new ConcurrentHashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1.0.0").optional();

        registrar.playToServer(
                RequestTelemetryPayload.TYPE,
                RequestTelemetryPayload.STREAM_CODEC,
                NetworkHandler::handleRequestPayload
        );

        registrar.playToClient(
                SyncTelemetryPayload.TYPE,
                SyncTelemetryPayload.STREAM_CODEC,
                NetworkHandler::handleSyncPayload
        );
    }

    private static void handleRequestPayload(RequestTelemetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServerLevel serverLevel = serverPlayer.serverLevel();
                BlockPos targetPos = payload.targetPos();
                Direction hitFace = payload.hitFace();
                TelemetryData data = Services.SERVER_SAMPLER.sample(serverLevel, targetPos, hitFace);
                
                short disabledMask = 0;
                if (com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_ITEMS != null && !com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_ITEMS.get()) {
                    disabledMask |= TelemetryData.MASK_ITEMS;
                }
                if (com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_FLUIDS != null && !com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_FLUIDS.get()) {
                    disabledMask |= TelemetryData.MASK_FLUIDS;
                }
                if (com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_ENERGY != null && !com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_ENERGY.get()) {
                    disabledMask |= TelemetryData.MASK_ENERGY;
                }
                if (com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_KINETIC != null && !com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_KINETIC.get()) {
                    disabledMask |= TelemetryData.MASK_KINETIC;
                }
                if (com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_CHEMICALS != null && !com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_CHEMICALS.get()) {
                    disabledMask |= TelemetryData.MASK_CHEMICALS;
                }
                if (com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_SIDE_METRICS != null && !com.alikuxac.machinemetrics.config.ServerConfig.ALLOW_SIDE_METRICS.get()) {
                    disabledMask |= TelemetryData.MASK_SIDES;
                }

                long now = System.currentTimeMillis();
                SentEntry lastSent = LAST_SENT_TO_CLIENT.get(targetPos);
                if (lastSent == null || data.hasSignificantDelta(lastSent.data) || (now - lastSent.timestampMs >= 1500L)) {
                    LAST_SENT_TO_CLIENT.put(targetPos, new SentEntry(data, now));
                    SyncTelemetryPayload response = SyncTelemetryPayload.from(targetPos, data, disabledMask);
                    context.reply(response);
                }
            }
        });
    }

    private static void handleSyncPayload(SyncTelemetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTelemetryCache.putSynced(payload.targetPos(), payload.toTelemetryData());
        });
    }
}
