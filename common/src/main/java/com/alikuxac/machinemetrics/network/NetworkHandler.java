package com.alikuxac.machinemetrics.network;

import com.alikuxac.machinemetrics.client.ClientTelemetryCache;
import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.platform.Services;
import com.alikuxac.machinemetrics.platform.network.NetworkPlatform;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandler {

    private record SentEntry(TelemetryData data, long timestampMs) {}
    private static final Map<BlockPos, SentEntry> LAST_SENT_TO_CLIENT = new ConcurrentHashMap<>();
    // ponytail: entries only need to live long enough for the next request; evict anything idle.
    private static final long SENT_ENTRY_EXPIRE_MS = 10000L;
    private static volatile long lastCleanupMs = 0L;

    private static void cleanupStaleEntries(long now) {
        if (now - lastCleanupMs < SENT_ENTRY_EXPIRE_MS) {
            return;
        }
        lastCleanupMs = now;
        LAST_SENT_TO_CLIENT.entrySet().removeIf(entry -> now - entry.getValue().timestampMs() > SENT_ENTRY_EXPIRE_MS);
    }

    public static void register(NetworkPlatform.RegisterEvent event) {
        NetworkPlatform.Registrar registrar = event.registrar("1.0.0");

        registrar.playToServer(
                RequestTelemetryPayload.class,
                RequestTelemetryPayload.TYPE,
                RequestTelemetryPayload.STREAM_CODEC,
                NetworkHandler::handleRequestPayload
        );

        registrar.playToClient(
                SyncTelemetryPayload.class,
                SyncTelemetryPayload.TYPE,
                SyncTelemetryPayload.STREAM_CODEC,
                NetworkHandler::handleSyncPayload
        );
    }

    private static void handleRequestPayload(RequestTelemetryPayload payload, NetworkPlatform.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.player();
            if (serverPlayer != null) {
                ServerLevel serverLevel = serverPlayer.serverLevel();
                BlockPos targetPos = payload.targetPos();
                Direction hitFace = payload.hitFace();
                TelemetryData data = Services.SERVER_SAMPLER.sample(serverLevel, targetPos, hitFace);

                short disabledMask = 0;
                if (ServerConfig.ALLOW_ITEMS != null && !ServerConfig.ALLOW_ITEMS.get()) {
                    disabledMask |= TelemetryData.MASK_ITEMS;
                }
                if (ServerConfig.ALLOW_FLUIDS != null && !ServerConfig.ALLOW_FLUIDS.get()) {
                    disabledMask |= TelemetryData.MASK_FLUIDS;
                }
                if (ServerConfig.ALLOW_ENERGY != null && !ServerConfig.ALLOW_ENERGY.get()) {
                    disabledMask |= TelemetryData.MASK_ENERGY;
                }
                if (ServerConfig.ALLOW_KINETIC != null && !ServerConfig.ALLOW_KINETIC.get()) {
                    disabledMask |= TelemetryData.MASK_KINETIC;
                }
                if (ServerConfig.ALLOW_CHEMICALS != null && !ServerConfig.ALLOW_CHEMICALS.get()) {
                    disabledMask |= TelemetryData.MASK_CHEMICALS;
                }
                if (ServerConfig.ALLOW_SIDE_METRICS != null && !ServerConfig.ALLOW_SIDE_METRICS.get()) {
                    disabledMask |= TelemetryData.MASK_SIDES;
                }

                long now = System.currentTimeMillis();
                cleanupStaleEntries(now);
                SentEntry lastSent = LAST_SENT_TO_CLIENT.get(targetPos);
                if (lastSent == null || data.hasSignificantDelta(lastSent.data) || (now - lastSent.timestampMs >= 1500L)) {
                    LAST_SENT_TO_CLIENT.put(targetPos, new SentEntry(data, now));
                    SyncTelemetryPayload response = SyncTelemetryPayload.from(targetPos, data, disabledMask);
                    context.reply(response);
                }
            }
        });
    }

    private static void handleSyncPayload(SyncTelemetryPayload payload, NetworkPlatform.Context context) {
        context.enqueueWork(() -> {
            ClientTelemetryCache.putSynced(payload.targetPos(), payload.toTelemetryData());
        });
    }
}