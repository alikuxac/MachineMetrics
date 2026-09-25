package com.alikuxac.machinemetrics.network;

import com.alikuxac.machinemetrics.client.ClientTelemetryCache;
import com.alikuxac.machinemetrics.neoforge.telemetry.ServerTelemetrySampler;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class NetworkHandler {

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
                TelemetryData data = ServerTelemetrySampler.sampleTargetBlock(serverLevel, payload.targetPos());
                SyncTelemetryPayload response = SyncTelemetryPayload.from(payload.targetPos(), data);
                context.reply(response);
            }
        });
    }

    private static void handleSyncPayload(SyncTelemetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTelemetryCache.put(payload.targetPos(), payload.toTelemetryData());
        });
    }
}
