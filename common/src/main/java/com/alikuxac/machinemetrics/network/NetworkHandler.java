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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandler {

    private record SentKey(UUID playerId, BlockPos pos) {}
    private record SentEntry(TelemetryData data, long timestampMs) {}
    private record RateWindow(long windowStartMs, int count) {}

    private static final Map<SentKey, SentEntry> LAST_SENT_TO_CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, RateWindow> RATE_LIMITS = new ConcurrentHashMap<>();
    // ponytail: entries only need to live long enough for the next request; evict anything idle.
    private static final long SENT_ENTRY_EXPIRE_MS = 10000L;
    private static final long RATE_WINDOW_MS = 1000L;
    private static final int MAX_SENT_ENTRIES = 4096;
    private static volatile long lastCleanupMs = 0L;

    private static void cleanupStaleEntries(long now) {
        cleanupStaleEntries(now, false);
    }

    private static void cleanupStaleEntries(long now, boolean force) {
        if (!force && now - lastCleanupMs < SENT_ENTRY_EXPIRE_MS) {
            return;
        }
        lastCleanupMs = now;
        LAST_SENT_TO_CLIENT.entrySet().removeIf(entry -> now - entry.getValue().timestampMs() > SENT_ENTRY_EXPIRE_MS);
        RATE_LIMITS.entrySet().removeIf(entry -> now - entry.getValue().windowStartMs() > SENT_ENTRY_EXPIRE_MS);
    }

    private static void enforceSentEntryCap(long now) {
        if (LAST_SENT_TO_CLIENT.size() <= MAX_SENT_ENTRIES) {
            return;
        }
        cleanupStaleEntries(now, true);
        int excess = LAST_SENT_TO_CLIENT.size() - MAX_SENT_ENTRIES;
        if (excess <= 0) {
            return;
        }
        LAST_SENT_TO_CLIENT.entrySet().stream()
                .sorted(java.util.Comparator.comparingLong(e -> e.getValue().timestampMs()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .forEach(LAST_SENT_TO_CLIENT::remove);
    }

    private static boolean allowRequest(UUID playerId, long now, int maxPerSecond) {
        if (maxPerSecond <= 0) {
            return true;
        }
        RateWindow window = RATE_LIMITS.get(playerId);
        if (window == null || now - window.windowStartMs() >= RATE_WINDOW_MS) {
            RATE_LIMITS.put(playerId, new RateWindow(now, 1));
            return true;
        }
        if (window.count() >= maxPerSecond) {
            return false;
        }
        RATE_LIMITS.put(playerId, new RateWindow(window.windowStartMs(), window.count() + 1));
        return true;
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
                BlockPos targetPos = payload.targetPos();
                if (targetPos == null) {
                    return;
                }

                double maxRange = ServerConfig.MAX_REQUEST_RANGE != null ? ServerConfig.MAX_REQUEST_RANGE.get() : 12.0;
                if (!serverPlayer.blockPosition().closerThan(targetPos, maxRange)) {
                    return;
                }

                ServerLevel serverLevel = serverPlayer.serverLevel();
                if (!serverLevel.isLoaded(targetPos)) {
                    return;
                }

                long now = System.currentTimeMillis();
                cleanupStaleEntries(now);
                int maxPerSecond = ServerConfig.MAX_REQUESTS_PER_SECOND != null ? ServerConfig.MAX_REQUESTS_PER_SECOND.get() : 20;
                if (!allowRequest(serverPlayer.getUUID(), now, maxPerSecond)) {
                    return;
                }

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

                SentKey key = new SentKey(serverPlayer.getUUID(), targetPos);
                SentEntry lastSent = LAST_SENT_TO_CLIENT.get(key);
                if (lastSent == null || data.hasSignificantDelta(lastSent.data) || (now - lastSent.timestampMs() >= 1500L)) {
                    LAST_SENT_TO_CLIENT.put(key, new SentEntry(data, now));
                    enforceSentEntryCap(now);
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