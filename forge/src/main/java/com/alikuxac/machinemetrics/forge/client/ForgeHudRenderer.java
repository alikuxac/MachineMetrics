package com.alikuxac.machinemetrics.forge.client;

import com.alikuxac.machinemetrics.client.TelemetryHudRenderer;
import com.alikuxac.machinemetrics.forge.telemetry.ForgeClientTelemetrySampler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

public class ForgeHudRenderer {

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("machinemetrics", "hud");

    public static void onAddLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(
                ForgeLayeredDraw.CROSSHAIR,
                LAYER,
                (guiGraphics, deltaTracker) -> TelemetryHudRenderer.renderHud(guiGraphics, ForgeClientTelemetrySampler::sampleTargetBlock)
        );
    }
}