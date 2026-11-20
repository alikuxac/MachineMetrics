package com.alikuxac.machinemetrics.neoforge.client;

import com.alikuxac.machinemetrics.command.MachineMetricsClientCommands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = "machinemetrics", value = Dist.CLIENT)
public final class NeoForgeClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        MachineMetricsClientCommands.register(event.getDispatcher());
    }

    private NeoForgeClientCommands() {
    }
}
