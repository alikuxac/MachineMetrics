package com.alikuxac.machinemetrics.forge;

import com.alikuxac.machinemetrics.config.ServerConfig;
import com.alikuxac.machinemetrics.forge.client.ForgeClientInit;
import com.alikuxac.machinemetrics.forge.platform.ForgeConfigFactory;
import com.alikuxac.machinemetrics.forge.platform.ForgeNetworkPlatform;
import com.alikuxac.machinemetrics.forge.platform.ForgeStorageLookup;
import com.alikuxac.machinemetrics.forge.telemetry.ForgeServerTelemetrySampler;
import com.alikuxac.machinemetrics.network.NetworkHandler;
import com.alikuxac.machinemetrics.platform.Services;
import com.alikuxac.machinemetrics.platform.network.NetworkPlatform;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod("machinemetrics")
public class MachinemetricsForge {

    public static final Logger LOGGER = LogUtils.getLogger();

    public MachinemetricsForge(FMLJavaModLoadingContext context) {
        ForgeConfigFactory.install();
        ForgeStorageLookup.install();
        NetworkPlatform.setRegistrationHook(NetworkHandler::register);

        Services.SERVER_SAMPLER = ForgeServerTelemetrySampler::sampleTargetBlock;
        ForgeNetworkPlatform.install();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, (ForgeConfigSpec) ServerConfig.SPEC.nativeSpec());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientInit.init(context, context.getContainer());
        }

        LOGGER.info("Machinemetrics Forge telemetry mod initialized.");
    }
}