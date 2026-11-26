package com.alikuxac.machinemetrics.client;

public final class HudDisplayFormatting {
    private HudDisplayFormatting() {}

    public static int applyOpacity(int color, int opacity) {
        int alpha = Math.max(0, Math.min(255, opacity));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
