package com.alikuxac.machinemetrics.neoforge;

import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.neoforge.client.NeoForgeClientInit;
import com.alikuxac.machinemetrics.neoforge.telemetry.ServerTelemetrySampler;
import com.alikuxac.machinemetrics.network.NetworkHandler;
import com.alikuxac.machinemetrics.platform.Services;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod("machinemetrics")
public class MachinemetricsNeoForge {

    public static final Logger LOGGER = LogUtils.getLogger();

    public MachinemetricsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Services.SERVER_SAMPLER = ServerTelemetrySampler::sampleTargetBlock;
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(NetworkHandler::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeClientInit.init(modEventBus, modContainer);
        }

        LOGGER.info("Machinemetrics NeoForge telemetry mod initialized.");
    }
}
