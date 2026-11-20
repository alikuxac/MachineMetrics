package com.alikuxac.machinemetrics.telemetry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class TelemetryRuntime {

    private static final long ACTIVE_CLIENT_TIMEOUT_MS = 5000L;
    private static final Map<UUID, Long> ACTIVE_CLIENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST_TIME = new ConcurrentHashMap<>();

    private static final AtomicInteger MIN_INTERVAL_TICKS = new AtomicInteger(1);
    private static final AtomicLong SCAN_NANOS_TOTAL = new AtomicLong();
    private static final AtomicLong SCAN_COUNT = new AtomicLong();

    private static final Object WINDOW_LOCK = new Object();
    private static long windowStartMs = System.currentTimeMillis();
    private static int windowReplies = 0;
    private static volatile int lastDispatchRate = 0;

    private TelemetryRuntime() {
    }

    public static void noteRequest(UUID playerId) {
        if (playerId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        ACTIVE_CLIENTS.put(playerId, now);
        prune(now);
    }

    public static void noteReply(long scanNanos) {
        SCAN_NANOS_TOTAL.addAndGet(Math.max(0L, scanNanos));
        SCAN_COUNT.incrementAndGet();
        synchronized (WINDOW_LOCK) {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= 1000L) {
                lastDispatchRate = windowReplies;
                windowReplies = 0;
                windowStartMs = now;
            }
            windowReplies++;
        }
    }

    public static int activeClientCount() {
        long now = System.currentTimeMillis();
        prune(now);
        return ACTIVE_CLIENTS.size();
    }

    public static int dispatchRateHz() {
        synchronized (WINDOW_LOCK) {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= 1000L) {
                lastDispatchRate = windowReplies;
                windowReplies = 0;
                windowStartMs = now;
            }
            return lastDispatchRate;
        }
    }

    public static double averageScanMicros() {
        long count = SCAN_COUNT.get();
        return count == 0L ? 0.0 : SCAN_NANOS_TOTAL.get() / (double) count / 1000.0;
    }

    public static void setMinRequestIntervalTicks(int ticks) {
        MIN_INTERVAL_TICKS.set(Math.max(0, Math.min(200, ticks)));
    }

    public static int getMinRequestIntervalTicks() {
        return MIN_INTERVAL_TICKS.get();
    }

    public static boolean allowInterval(UUID playerId, long now) {
        if (playerId == null) {
            return false;
        }
        long intervalMs = getMinRequestIntervalTicks() * 50L;
        if (intervalMs <= 0L) {
            return true;
        }
        Long last = LAST_REQUEST_TIME.get(playerId);
        if (last == null || now - last >= intervalMs) {
            LAST_REQUEST_TIME.put(playerId, now);
            return true;
        }
        return false;
    }

    private static void prune(long now) {
        if (ACTIVE_CLIENTS.size() > 64 || (now % 1000L) < 20L) {
            ACTIVE_CLIENTS.entrySet().removeIf(entry -> now - entry.getValue() > ACTIVE_CLIENT_TIMEOUT_MS);
            LAST_REQUEST_TIME.entrySet().removeIf(entry -> now - entry.getValue() > ACTIVE_CLIENT_TIMEOUT_MS);
        }
    }
}
