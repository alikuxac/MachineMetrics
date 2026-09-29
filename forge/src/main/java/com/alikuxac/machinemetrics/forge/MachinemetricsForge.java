package com.alikuxac.machinemetrics.forge;

import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.config.TelemetryConfig;
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
public class MachinemetricsForge {

    public static final Logger LOGGER = LogUtils.getLogger();

    public MachinemetricsForge(IEventBus modEventBus, ModContainer modContainer) {
        com.alikuxac.machinemetrics.platform.Services.SERVER_SAMPLER = com.alikuxac.machinemetrics.forge.telemetry.ServerTelemetrySampler::sampleTargetBlock;
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, TelemetryConfig.SPEC);
        modEventBus.addListener(com.alikuxac.machinemetrics.network.NetworkHandler::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerKeyMappings);
        }

        LOGGER.info("Machinemetrics Forge telemetry mod initialized.");
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_HUD_KEY);
    }
}
