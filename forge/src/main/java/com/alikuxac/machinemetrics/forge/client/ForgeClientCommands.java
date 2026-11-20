package com.alikuxac.machinemetrics.forge.client;

import com.alikuxac.machinemetrics.command.MachineMetricsClientCommands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "machinemetrics", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        MachineMetricsClientCommands.register(event.getDispatcher());
    }

    private ForgeClientCommands() {
    }
}
