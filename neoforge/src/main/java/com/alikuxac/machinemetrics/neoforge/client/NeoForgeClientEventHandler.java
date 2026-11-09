package com.alikuxac.machinemetrics.neoforge.client;

import com.alikuxac.machinemetrics.client.HudPositionScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.client.PinnedTargetManager;
import com.mojang.blaze3d.platform.InputConstants;
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
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "machinemetrics", value = Dist.CLIENT)
public class NeoForgeClientEventHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (KeyBindings.PIN_TARGET_KEY.consumeClick()) {
            PinnedTargetManager.togglePinCurrentTarget(mc);
        } else if (KeyBindings.TOGGLE_HUD_KEY.consumeClick()) {
            if (hasShiftDown(event)) {
                PinnedTargetManager.togglePinCurrentTarget(mc);
            } else {
                KeyBindings.hudEnabled = !KeyBindings.hudEnabled;
                if (mc.player != null) {
                    Component msg = KeyBindings.hudEnabled
                            ? Component.translatable("machinemetrics.hud.enabled").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("machinemetrics.hud.disabled").withStyle(ChatFormatting.RED);
                    mc.player.sendOverlayMessage(msg);
                }
            }
        }
    }

    private static boolean hasShiftDown(InputEvent.Key event) {
        if ((event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        }
        return false;
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
