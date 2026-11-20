package com.alikuxac.machinemetrics.forge.command;

import com.alikuxac.machinemetrics.command.MachineMetricsCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = "machinemetrics", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MachineMetricsCommands.register(event.getDispatcher());
    }

    private ForgeCommands() {
    }
}
