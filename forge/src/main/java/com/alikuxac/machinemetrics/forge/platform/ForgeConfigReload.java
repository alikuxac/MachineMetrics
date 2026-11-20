package com.alikuxac.machinemetrics.forge.platform;

import com.alikuxac.machinemetrics.platform.config.ConfigReload;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgeConfigReload {

    public static void install() {
        ConfigReload.setHandler(new ConfigReload.Handler() {
            @Override
            public void reloadClient() {
                java.nio.file.Path dir = FMLPaths.CONFIGDIR.get();
                ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.CLIENT, dir);
                ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, dir);
            }

            @Override
            public void reloadServer() {
                java.nio.file.Path dir = FMLPaths.CONFIGDIR.get();
                ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.SERVER, dir);
                ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, dir);
            }
        });
    }

    private ForgeConfigReload() {
    }
}
