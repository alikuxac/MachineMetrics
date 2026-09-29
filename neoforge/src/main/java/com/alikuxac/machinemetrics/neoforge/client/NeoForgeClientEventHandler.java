package com.alikuxac.machinemetrics.neoforge.client;

import com.alikuxac.machinemetrics.client.HudPositionScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = "machinemetrics", value = Dist.CLIENT)
public class NeoForgeClientEventHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (KeyBindings.TOGGLE_HUD_KEY.consumeClick()) {
            KeyBindings.hudEnabled = !KeyBindings.hudEnabled;
            if (mc.player != null) {
                Component msg = KeyBindings.hudEnabled
                        ? Component.translatable("machinemetrics.hud.enabled").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("machinemetrics.hud.disabled").withStyle(ChatFormatting.RED);
                mc.player.displayClientMessage(msg, true);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen screen) {
            int buttonWidth = 120;
            int buttonHeight = 20;
            int x = screen.width - buttonWidth - 8;
            int y = 8;

            Button configBtn = Button.builder(Component.translatable("machinemetrics.hud.button"), b -> {
                Minecraft.getInstance().setScreen(new HudPositionScreen());
            }).bounds(x, y, buttonWidth, buttonHeight).build();

            event.addListener(configBtn);
        }
    }
}
