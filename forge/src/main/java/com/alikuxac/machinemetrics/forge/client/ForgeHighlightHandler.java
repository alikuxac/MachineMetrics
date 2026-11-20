package com.alikuxac.machinemetrics.forge.client;

import com.alikuxac.machinemetrics.client.InWorldHighlightRenderer;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ForgeHighlightHandler {

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
