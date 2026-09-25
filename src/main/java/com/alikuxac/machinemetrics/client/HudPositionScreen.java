package com.alikuxac.machinemetrics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudPositionScreen extends Screen {

    public static int posX = 10;
    public static int posY = 10;
    public static int boxWidth = 140;
    public static int boxHeight = 50;

    private static final int MIN_WIDTH = 100;
    private static final int MIN_HEIGHT = 35;
    private static final int RESIZE_HANDLE_SIZE = 8;

    private boolean isDragging = false;
    private boolean isResizing = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public HudPositionScreen() {
        super(Component.translatable("machinemetrics.screen.hud_position"));
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int buttonHeight = 20;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            onClose();
        }).bounds((this.width - buttonWidth) / 2, this.height - 30, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("machinemetrics.hud.drag_hint"), this.width / 2, 25, 0xAAAAAA);

        // Container background & border
        graphics.fill(posX, posY, posX + boxWidth, posY + boxHeight, 0x90000000);
        graphics.renderOutline(posX, posY, boxWidth, boxHeight, 0xFF00FFCC);

        // Resize handle at bottom-right corner
        graphics.fill(posX + boxWidth - RESIZE_HANDLE_SIZE, posY + boxHeight - RESIZE_HANDLE_SIZE, posX + boxWidth, posY + boxHeight, 0xFF00FFCC);

        graphics.drawString(this.font, Component.literal("HUD Preview"), posX + 8, posY + 6, 0x00FFCC);
        if (boxHeight >= 45) {
            graphics.drawString(this.font, Component.literal("Items: 12.5/s"), posX + 8, posY + 18, 0xFFFFFF);
            graphics.drawString(this.font, Component.literal("FE: +120 FE/t"), posX + 8, posY + 30, 0x55FF55);
        } else {
            graphics.drawString(this.font, Component.literal("12.5/s | +120 FE/t"), posX + 8, posY + 18, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check if clicking resize handle (bottom-right corner)
            if (mouseX >= posX + boxWidth - RESIZE_HANDLE_SIZE && mouseX <= posX + boxWidth
                    && mouseY >= posY + boxHeight - RESIZE_HANDLE_SIZE && mouseY <= posY + boxHeight) {
                isResizing = true;
                return true;
            }

            // Check if clicking inside box for dragging
            if (mouseX >= posX && mouseX <= posX + boxWidth && mouseY >= posY && mouseY <= posY + boxHeight) {
                isDragging = true;
                dragOffsetX = (int) mouseX - posX;
                dragOffsetY = (int) mouseY - posY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
            isResizing = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isResizing) {
            int newWidth = (int) mouseX - posX;
            int newHeight = (int) mouseY - posY;
            boxWidth = Math.max(MIN_WIDTH, Math.min(this.width - posX, newWidth));
            boxHeight = Math.max(MIN_HEIGHT, Math.min(this.height - posY, newHeight));
            return true;
        }

        if (isDragging) {
            posX = Math.max(0, Math.min(this.width - boxWidth, (int) mouseX - dragOffsetX));
            posY = Math.max(0, Math.min(this.height - boxHeight, (int) mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
