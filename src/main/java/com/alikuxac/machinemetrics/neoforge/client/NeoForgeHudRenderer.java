package com.alikuxac.machinemetrics.neoforge.client;

import com.alikuxac.machinemetrics.client.TelemetryHudRenderer;
import com.alikuxac.machinemetrics.neoforge.telemetry.NeoForgeTelemetrySampler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "machinemetrics", value = Dist.CLIENT)
public class NeoForgeHudRenderer {

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            return;
        }

        TelemetryHudRenderer.renderHud(event.getGuiGraphics(), NeoForgeTelemetrySampler::sampleTargetBlock);
    }
}
