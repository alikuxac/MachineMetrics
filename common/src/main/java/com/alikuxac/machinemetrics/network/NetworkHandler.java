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

    private static final Map<BlockPos, TelemetryData> LAST_SENT_TO_CLIENT = new ConcurrentHashMap<>();

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
                
                LAST_SENT_TO_CLIENT.put(targetPos, data);
                SyncTelemetryPayload response = SyncTelemetryPayload.from(targetPos, data);
                context.reply(response);
            }
        });
    }

    private static void handleSyncPayload(SyncTelemetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTelemetryCache.putSynced(payload.targetPos(), payload.toTelemetryData());
        });
    }
}
