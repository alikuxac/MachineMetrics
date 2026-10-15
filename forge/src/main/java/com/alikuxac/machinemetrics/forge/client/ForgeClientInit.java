package com.alikuxac.machinemetrics.forge.client;

import com.alikuxac.machinemetrics.client.HudPositionScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.config.ClientConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public class ForgeClientInit {

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, screen) -> new HudPositionScreen(screen)
        );
        modEventBus.addListener(ForgeClientInit::registerKeyMappings);
        NeoForge.EVENT_BUS.register(NeoForgeClientEventHandler.class);
        NeoForge.EVENT_BUS.register(NeoForgeHudRenderer.class);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_HUD_KEY);
    }
}
