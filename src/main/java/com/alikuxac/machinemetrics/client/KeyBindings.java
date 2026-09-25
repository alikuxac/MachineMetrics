package com.alikuxac.machinemetrics.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY = "key.categories.machinemetrics";

    public static final KeyMapping TOGGLE_HUD_KEY = new KeyMapping(
            "key.machinemetrics.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY
    );

    public static boolean hudEnabled = true;
}
