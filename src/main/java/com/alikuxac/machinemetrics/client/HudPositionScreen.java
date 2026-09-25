package com.alikuxac.machinemetrics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudPositionScreen extends Screen {

    public static int posX = 10;
    public static int posY = 10;

    private boolean isDragging = false;
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

        int boxWidth = 140;
        int boxHeight = 50;

        graphics.fill(posX, posY, posX + boxWidth, posY + boxHeight, 0x90000000);
        graphics.renderOutline(posX, posY, boxWidth, boxHeight, 0xFF00FFCC);

        graphics.drawString(this.font, Component.literal("HUD Preview"), posX + 8, posY + 8, 0x00FFCC);
        graphics.drawString(this.font, Component.literal("Items: 12.5/s"), posX + 8, posY + 22, 0xFFFFFF);
        graphics.drawString(this.font, Component.literal("FE: +120 FE/t"), posX + 8, posY + 34, 0x55FF55);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int boxWidth = 140;
            int boxHeight = 50;
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
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging) {
            posX = Math.max(0, Math.min(this.width - 140, (int) mouseX - dragOffsetX));
            posY = Math.max(0, Math.min(this.height - 50, (int) mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
