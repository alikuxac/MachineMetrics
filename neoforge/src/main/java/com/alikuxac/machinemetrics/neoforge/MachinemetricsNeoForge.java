package com.alikuxac.machinemetrics.neoforge;

import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.neoforge.client.NeoForgeClientEventHandler;
import com.alikuxac.machinemetrics.neoforge.client.NeoForgeHudRenderer;
import com.alikuxac.machinemetrics.neoforge.telemetry.ServerTelemetrySampler;
import com.alikuxac.machinemetrics.network.NetworkHandler;
import com.alikuxac.machinemetrics.platform.Services;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod("machinemetrics")
public class MachinemetricsNeoForge {

    public static final Logger LOGGER = LogUtils.getLogger();

    public MachinemetricsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Services.SERVER_SAMPLER = ServerTelemetrySampler::sampleTargetBlock;
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(NetworkHandler::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
            modEventBus.addListener(this::registerKeyMappings);
            NeoForge.EVENT_BUS.register(NeoForgeClientEventHandler.class);
            NeoForge.EVENT_BUS.register(NeoForgeHudRenderer.class);
        }

        LOGGER.info("Machinemetrics NeoForge telemetry mod initialized.");
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_HUD_KEY);
    }
}
