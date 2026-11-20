package com.alikuxac.machinemetrics.neoforge.platform;

import com.alikuxac.machinemetrics.platform.config.ConfigReload;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.loading.FMLPaths;

public final class NeoForgeConfigReload {

    public static void install() {
        ConfigReload.setHandler(new ConfigReload.Handler() {
            @Override
            public void reloadClient() {
                ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.CLIENT);
                ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, FMLPaths.CONFIGDIR.get());
            }

            @Override
            public void reloadServer() {
                ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.SERVER);
                ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, FMLPaths.CONFIGDIR.get());
            }
        });
    }

    private NeoForgeConfigReload() {
    }
}
