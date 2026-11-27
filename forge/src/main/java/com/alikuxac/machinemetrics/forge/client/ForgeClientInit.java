package com.alikuxac.machinemetrics.forge.client;

import com.alikuxac.machinemetrics.client.MachineMetricsConfigScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.forge.platform.ForgeConfigFactory;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
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
                        (mc, parent) -> new MachineMetricsConfigScreen(parent))
        );

        IEventBus modBus = context.getModEventBus();
        modBus.addListener(ForgeClientInit::registerKeyMappings);
        modBus.addListener(ForgeHudRenderer::onAddLayers);

        MinecraftForge.EVENT_BUS.addListener(ForgeHighlightHandler::onRenderHighlight);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEventHandler::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEventHandler::onPlayerClone);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_HUD_KEY);
        event.register(KeyBindings.PIN_TARGET_KEY);
    }
}
