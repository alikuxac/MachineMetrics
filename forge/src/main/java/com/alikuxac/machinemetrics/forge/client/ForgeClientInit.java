package com.alikuxac.machinemetrics.forge.client;

import com.alikuxac.machinemetrics.client.HudPositionScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.forge.platform.ForgeConfigFactory;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class ForgeClientInit {

    public static void init(FMLJavaModLoadingContext context, ModContainer modContainer) {
        ForgeConfigFactory.install();

        context.registerConfig(ModConfig.Type.CLIENT, (ForgeConfigSpec) ClientConfig.SPEC.nativeSpec());
        modContainer.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new HudPositionScreen(parent))
        );

        IEventBus modBus = context.getModEventBus();
        modBus.addListener(ForgeClientInit::registerKeyMappings);
        modBus.addListener(ForgeHudRenderer::onAddLayers);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_HUD_KEY);
    }
}