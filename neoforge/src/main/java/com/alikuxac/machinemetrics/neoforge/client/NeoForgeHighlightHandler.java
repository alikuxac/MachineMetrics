package com.alikuxac.machinemetrics.neoforge.client;

import com.alikuxac.machinemetrics.client.InWorldHighlightRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = "machinemetrics", value = Dist.CLIENT)
public class NeoForgeHighlightHandler {

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (InWorldHighlightRenderer.renderHighlight(
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getCamera(),
                event.getTarget())) {
            event.setCanceled(true);
        }
    }
}
