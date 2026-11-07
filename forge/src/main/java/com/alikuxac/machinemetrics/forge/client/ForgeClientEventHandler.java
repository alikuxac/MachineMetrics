package com.alikuxac.machinemetrics.forge.client;

import com.alikuxac.machinemetrics.client.HudPositionScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.client.PinnedTargetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ForgeClientEventHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (KeyBindings.PIN_TARGET_KEY.consumeClick()) {
            PinnedTargetManager.togglePinCurrentTarget(mc);
        } else if (KeyBindings.TOGGLE_HUD_KEY.consumeClick()) {
            if (Screen.hasShiftDown()) {
                PinnedTargetManager.togglePinCurrentTarget(mc);
            } else {
                KeyBindings.hudEnabled = !KeyBindings.hudEnabled;
                if (mc.player != null) {
                    Component msg = KeyBindings.hudEnabled
                            ? Component.translatable("machinemetrics.hud.enabled").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("machinemetrics.hud.disabled").withStyle(ChatFormatting.RED);
                    mc.player.displayClientMessage(msg, true);
                }
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
                Minecraft.getInstance().setScreen(new HudPositionScreen(screen));
            }).bounds(x, y, buttonWidth, buttonHeight).build();

            event.addListener(configBtn);
        }
    }
}
