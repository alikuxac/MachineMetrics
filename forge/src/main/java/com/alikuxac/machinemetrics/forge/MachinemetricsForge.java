package com.alikuxac.machinemetrics.forge;

import com.alikuxac.machinemetrics.client.HudPositionScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.forge.client.NeoForgeClientEventHandler;
import com.alikuxac.machinemetrics.forge.client.NeoForgeHudRenderer;
import com.alikuxac.machinemetrics.forge.telemetry.ServerTelemetrySampler;
import com.alikuxac.machinemetrics.network.NetworkHandler;
import com.alikuxac.machinemetrics.platform.Services;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod("machinemetrics")
public class MachinemetricsForge {

    public static final Logger LOGGER = LogUtils.getLogger();

    public MachinemetricsForge(IEventBus modEventBus, ModContainer modContainer) {
        Services.SERVER_SAMPLER = ServerTelemetrySampler::sampleTargetBlock;
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(NetworkHandler::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    (container, screen) -> new HudPositionScreen(screen)
            );
            modEventBus.addListener(this::registerKeyMappings);
            NeoForge.EVENT_BUS.register(NeoForgeClientEventHandler.class);
            NeoForge.EVENT_BUS.register(NeoForgeHudRenderer.class);
        }

        LOGGER.info("Machinemetrics Forge telemetry mod initialized.");
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_HUD_KEY);
    }
}
