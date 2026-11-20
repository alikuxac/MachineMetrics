package com.alikuxac.machinemetrics.neoforge.command;

import com.alikuxac.machinemetrics.command.MachineMetricsCommands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "machinemetrics")
public final class NeoForgeCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MachineMetricsCommands.register(event.getDispatcher());
    }

    private NeoForgeCommands() {
    }
}
