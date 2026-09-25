package com.alikuxac.machinemetrics.neoforge;

import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.neoforge.client.NeoForgeClientEventHandler;
import com.alikuxac.machinemetrics.neoforge.client.NeoForgeHudRenderer;
import com.mojang.logging.LogUtils;
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
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, com.alikuxac.machinemetrics.config.TelemetryConfig.SPEC);
        modEventBus.addListener(com.alikuxac.machinemetrics.network.NetworkHandler::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
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
