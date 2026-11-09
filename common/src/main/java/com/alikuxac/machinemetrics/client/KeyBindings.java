package com.alikuxac.machinemetrics.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("machinemetrics", "main"));

    public static final KeyMapping TOGGLE_HUD_KEY = new KeyMapping(
            "key.machinemetrics.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    public static final KeyMapping PIN_TARGET_KEY = new KeyMapping(
            "key.machinemetrics.pin_target",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
    );

    public static final KeyMapping KEY_PIN_TARGET = PIN_TARGET_KEY;

    public static boolean hudEnabled = true;
}
