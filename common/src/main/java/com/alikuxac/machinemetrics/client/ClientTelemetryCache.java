package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTelemetryCache {

    private record Entry(TelemetryData data, long receivedTimeMs) {}

    private static final Map<BlockPos, Entry> CACHE = new ConcurrentHashMap<>();
    private static final long EXPIRE_MS = 2000L;

    public static void put(BlockPos pos, TelemetryData data) {
        if (pos != null && data != null) {
            CACHE.put(pos, new Entry(data, System.currentTimeMillis()));
        }
    }

    public static TelemetryData get(BlockPos pos) {
        if (pos == null) {
            return TelemetryData.empty();
        }
        Entry entry = CACHE.get(pos);
        if (entry == null) {
            return TelemetryData.empty();
        }
        if (System.currentTimeMillis() - entry.receivedTimeMs > EXPIRE_MS) {
            CACHE.remove(pos);
            return TelemetryData.empty();
        }
        return entry.data;
    }

    public static void clear() {
        CACHE.clear();
    }
}
