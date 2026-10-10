package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.compat.infohud.InfoHudDetector;
import com.alikuxac.machinemetrics.compat.mekanism.MekanismChemicalInspector;
import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.telemetry.TelemetryData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HudPositionScreen extends Screen {

    private boolean isDragging = false;
    private int dragMouseStartX = 0;
    private int dragMouseStartY = 0;
    private int dragInitialOffsetX = 0;
    private int dragInitialOffsetY = 0;

    private Button anchorButton;
    private Button presetButton;
    private Button titleToggleButton;

    private enum PresetType {
        FULL_DIAGNOSTIC("machinemetrics.preset.full_diagnostic"),
        MINIMALIST_BAR("machinemetrics.preset.minimalist_bar"),
        CREATE_ONLY("machinemetrics.preset.create_only"),
        CHEMICALS_ONLY("machinemetrics.preset.chemicals_only"),
        ENERGY_ONLY("machinemetrics.preset.energy_only"),
        ITEMS_ONLY("machinemetrics.preset.items_only"),
        FLUIDS_ONLY("machinemetrics.preset.fluids_only");

        final String key;
        PresetType(String key) {
            this.key = key;
        }
    }

    private final Screen lastScreen;
    private final List<PresetType> availablePresets = new ArrayList<>();
    private int currentPresetIndex = 0;

    public HudPositionScreen() {
        this(null);
    }

    public HudPositionScreen(Screen lastScreen) {
        super(Component.translatable("machinemetrics.screen.hud_position"));
        this.lastScreen = lastScreen;
        refreshAvailablePresets();
    }

    private void refreshAvailablePresets() {
        availablePresets.clear();
        availablePresets.add(PresetType.FULL_DIAGNOSTIC);
        availablePresets.add(PresetType.MINIMALIST_BAR);

        // Only offer 'Create Only' preset if Create mod is present
        if (CreateKineticInspector.isCreateLoaded()) {
            availablePresets.add(PresetType.CREATE_ONLY);
        }

        // Only offer 'Chemicals Only' preset if Mekanism mod is present
        if (MekanismChemicalInspector.isMekanismLoaded()) {
            availablePresets.add(PresetType.CHEMICALS_ONLY);
        }

        availablePresets.add(PresetType.ENERGY_ONLY);
        availablePresets.add(PresetType.ITEMS_ONLY);
        availablePresets.add(PresetType.FLUIDS_ONLY);

        currentPresetIndex = detectMatchingPresetIndex();
    }

    private int detectMatchingPresetIndex() {
        for (int i = 0; i < availablePresets.size(); i++) {
            if (matchesPreset(availablePresets.get(i))) {
                return i;
            }
        }
        return -1; // Custom configuration
    }

    private boolean matchesPreset(PresetType preset) {
        boolean isCompact = ClientConfig.COMPACT_MODE != null && ClientConfig.COMPACT_MODE.get();
        if (preset == PresetType.MINIMALIST_BAR) {
            return isCompact;
        }
        if (isCompact) {
            return false;
        }

        boolean title = ClientConfig.SHOW_MACHINE_TITLE == null || ClientConfig.SHOW_MACHINE_TITLE.get();
        boolean badge = ClientConfig.SHOW_STATUS_BADGE == null || ClientConfig.SHOW_STATUS_BADGE.get();
        boolean items = ClientConfig.SHOW_ITEMS_THROUGHPUT == null || ClientConfig.SHOW_ITEMS_THROUGHPUT.get();
        boolean fluids = ClientConfig.SHOW_FLUIDS_THROUGHPUT == null || ClientConfig.SHOW_FLUIDS_THROUGHPUT.get();
        boolean chemicals = ClientConfig.SHOW_CHEMICALS_THROUGHPUT == null || ClientConfig.SHOW_CHEMICALS_THROUGHPUT.get();
        boolean speed = ClientConfig.SHOW_SPEED == null || ClientConfig.SHOW_SPEED.get();
        boolean dir = ClientConfig.SHOW_ROTATION_DIRECTION == null || ClientConfig.SHOW_ROTATION_DIRECTION.get();
        boolean stress = ClientConfig.SHOW_NETWORK_STRESS == null || ClientConfig.SHOW_NETWORK_STRESS.get();
        boolean headroom = ClientConfig.SHOW_HEADROOM == null || ClientConfig.SHOW_HEADROOM.get();
        boolean localStress = ClientConfig.SHOW_LOCAL_IMPACT != null && ClientConfig.SHOW_LOCAL_IMPACT.get();
        boolean energy = ClientConfig.SHOW_ENERGY_FE == null || ClientConfig.SHOW_ENERGY_FE.get();
        boolean sides = ClientConfig.SHOW_SIDES == null || ClientConfig.SHOW_SIDES.get();

        boolean hasCreate = CreateKineticInspector.isCreateLoaded();
        boolean hasMek = MekanismChemicalInspector.isMekanismLoaded();

        return switch (preset) {
            case FULL_DIAGNOSTIC -> title && badge && items && fluids &&
                    (!hasMek || chemicals) &&
                    (!hasCreate || (speed && dir && stress && headroom)) &&
                    !localStress && energy && sides;
            case CREATE_ONLY -> title && badge && !items && !fluids && !chemicals &&
                    speed && dir && stress && headroom && !localStress && !energy && !sides;
            case CHEMICALS_ONLY -> title && badge && !items && !fluids && chemicals &&
                    !speed && !dir && !stress && !headroom && !localStress && !energy && sides;
            case ENERGY_ONLY -> title && badge && !items && !fluids && !chemicals &&
                    !speed && !dir && !stress && !headroom && !localStress && energy && !sides;
            case ITEMS_ONLY -> title && badge && items && !fluids && !chemicals &&
                    !speed && !dir && !stress && !headroom && !localStress && !energy && sides;
            case FLUIDS_ONLY -> title && badge && !items && fluids && !chemicals &&
                    !speed && !dir && !stress && !headroom && !localStress && !energy && sides;
            default -> false;
        };
    }

    private Component getAnchorButtonText(ClientConfig.HudAnchor anchor) {
        String key = switch (anchor) {
            case TOP_LEFT -> "machinemetrics.hud.anchor.top_left";
            case TOP_RIGHT -> "machinemetrics.hud.anchor.top_right";
            case BOTTOM_LEFT -> "machinemetrics.hud.anchor.bottom_left";
            case BOTTOM_RIGHT -> "machinemetrics.hud.anchor.bottom_right";
            case CROSSHAIR -> "machinemetrics.hud.anchor.crosshair";
        };
        return Component.translatable("machinemetrics.hud.anchor", Component.translatable(key).getString());
    }

    private Component getPresetButtonText() {
        if (currentPresetIndex >= 0 && currentPresetIndex < availablePresets.size()) {
            String presetKey = availablePresets.get(currentPresetIndex).key;
            return Component.translatable("machinemetrics.hud.preset", Component.translatable(presetKey).getString());
        }
        return Component.translatable("machinemetrics.hud.preset", Component.translatable("machinemetrics.preset.custom").getString());
    }

    @Override
    protected void init() {
        refreshAvailablePresets();

        int buttonWidth = 140;
        int buttonHeight = 20;

        // Button: Anchor Switcher
        ClientConfig.HudAnchor currentAnchor = ClientConfig.HUD_ANCHOR != null ? ClientConfig.HUD_ANCHOR.get() : ClientConfig.HudAnchor.TOP_LEFT;
        anchorButton = Button.builder(getAnchorButtonText(currentAnchor), b -> {
            cycleAnchor();
        }).bounds((this.width / 2) - buttonWidth - 5, this.height - 30, buttonWidth, buttonHeight).build();
        addRenderableWidget(anchorButton);

        // Button: Quick Preset Cycler
        presetButton = Button.builder(getPresetButtonText(), b -> {
            cyclePreset();
        }).bounds((this.width / 2) + 5, this.height - 30, buttonWidth, buttonHeight).build();
        addRenderableWidget(presetButton);

        // Button: Toggle Block Name Display
        boolean showTitle = ClientConfig.SHOW_MACHINE_TITLE == null || ClientConfig.SHOW_MACHINE_TITLE.get();
        titleToggleButton = Button.builder(getTitleButtonText(showTitle), b -> {
            toggleMachineTitle();
        }).bounds(10, 8, 130, 20).build();
        addRenderableWidget(titleToggleButton);

        // Button: Reset Offset to Default (10, 10 or 0, 0 for crosshair)
        addRenderableWidget(Button.builder(Component.translatable("machinemetrics.hud.reset_button"), b -> {
            resetPosition();
        }).bounds(145, 8, 120, 20).build());

        // Button: Done / Close
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            onClose();
        }).bounds(this.width - 70, 8, 60, 20).build());
    }

    private Component getTitleButtonText(boolean showTitle) {
        return showTitle
                ? Component.translatable("machinemetrics.hud.toggle_title_on")
                : Component.translatable("machinemetrics.hud.toggle_title_off");
    }

    private void toggleMachineTitle() {
        boolean current = ClientConfig.SHOW_MACHINE_TITLE == null || ClientConfig.SHOW_MACHINE_TITLE.get();
        boolean next = !current;
        if (ClientConfig.SHOW_MACHINE_TITLE != null) {
            ClientConfig.SHOW_MACHINE_TITLE.set(next);
        }
        if (titleToggleButton != null) {
            titleToggleButton.setMessage(getTitleButtonText(next));
        }
        currentPresetIndex = detectMatchingPresetIndex();
        if (presetButton != null) {
            presetButton.setMessage(getPresetButtonText());
        }
    }

    private void resetPosition() {
        ClientConfig.HudAnchor current = ClientConfig.HUD_ANCHOR != null ? ClientConfig.HUD_ANCHOR.get() : ClientConfig.HudAnchor.TOP_LEFT;
        int defaultX = current == ClientConfig.HudAnchor.CROSSHAIR ? 0 : 10;
        int defaultY = current == ClientConfig.HudAnchor.CROSSHAIR ? 0 : 10;
        if (ClientConfig.HUD_X_OFFSET != null) {
            ClientConfig.HUD_X_OFFSET.set(defaultX);
        }
        if (ClientConfig.HUD_Y_OFFSET != null) {
            ClientConfig.HUD_Y_OFFSET.set(defaultY);
        }
    }

    private void cycleAnchor() {
        ClientConfig.HudAnchor current = ClientConfig.HUD_ANCHOR != null ? ClientConfig.HUD_ANCHOR.get() : ClientConfig.HudAnchor.TOP_LEFT;
        ClientConfig.HudAnchor[] values = ClientConfig.HudAnchor.values();
        int nextIndex = (current.ordinal() + 1) % values.length;
        ClientConfig.HudAnchor next = values[nextIndex];

        if (ClientConfig.HUD_ANCHOR != null) {
            ClientConfig.HUD_ANCHOR.set(next);
        }
        // When changing anchor to/from CROSSHAIR or corner, reset offsets to clean defaults
        int defaultOffset = (next == ClientConfig.HudAnchor.CROSSHAIR) ? 0 : 10;
        if (ClientConfig.HUD_X_OFFSET != null) {
            ClientConfig.HUD_X_OFFSET.set(defaultOffset);
        }
        if (ClientConfig.HUD_Y_OFFSET != null) {
            ClientConfig.HUD_Y_OFFSET.set(defaultOffset);
        }

        if (anchorButton != null) {
            anchorButton.setMessage(getAnchorButtonText(next));
        }
    }

    private void cyclePreset() {
        if (availablePresets.isEmpty()) return;
        currentPresetIndex = (currentPresetIndex + 1) % availablePresets.size();
        applyPreset(availablePresets.get(currentPresetIndex));
        if (presetButton != null) {
            presetButton.setMessage(getPresetButtonText());
        }
        if (titleToggleButton != null) {
            boolean showTitle = ClientConfig.SHOW_MACHINE_TITLE == null || ClientConfig.SHOW_MACHINE_TITLE.get();
            titleToggleButton.setMessage(getTitleButtonText(showTitle));
        }
    }

    private void applyPreset(PresetType preset) {
        switch (preset) {
            case FULL_DIAGNOSTIC -> {
                setSafe(ClientConfig.COMPACT_MODE, false);
                setSafe(ClientConfig.SHOW_MACHINE_TITLE, true);
                setSafe(ClientConfig.SHOW_STATUS_BADGE, true);
                setSafe(ClientConfig.SHOW_ITEMS_THROUGHPUT, true);
                setSafe(ClientConfig.SHOW_FLUIDS_THROUGHPUT, true);
                setSafe(ClientConfig.SHOW_CHEMICALS_THROUGHPUT, MekanismChemicalInspector.isMekanismLoaded());
                setSafe(ClientConfig.SHOW_SPEED, CreateKineticInspector.isCreateLoaded());
                setSafe(ClientConfig.SHOW_ROTATION_DIRECTION, CreateKineticInspector.isCreateLoaded());
                setSafe(ClientConfig.SHOW_NETWORK_STRESS, CreateKineticInspector.isCreateLoaded());
                setSafe(ClientConfig.SHOW_HEADROOM, CreateKineticInspector.isCreateLoaded());
                setSafe(ClientConfig.SHOW_LOCAL_IMPACT, false);
                setSafe(ClientConfig.SHOW_ENERGY_FE, true);
                setSafe(ClientConfig.SHOW_SIDES, true);
            }
            case MINIMALIST_BAR -> {
                setSafe(ClientConfig.COMPACT_MODE, true);
            }
            case CREATE_ONLY -> {
                setSafe(ClientConfig.COMPACT_MODE, false);
                setSafe(ClientConfig.SHOW_MACHINE_TITLE, true);
                setSafe(ClientConfig.SHOW_STATUS_BADGE, true);
                setSafe(ClientConfig.SHOW_ITEMS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_FLUIDS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_CHEMICALS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_SPEED, true);
                setSafe(ClientConfig.SHOW_ROTATION_DIRECTION, true);
                setSafe(ClientConfig.SHOW_NETWORK_STRESS, true);
                setSafe(ClientConfig.SHOW_HEADROOM, true);
                setSafe(ClientConfig.SHOW_LOCAL_IMPACT, false);
                setSafe(ClientConfig.SHOW_ENERGY_FE, false);
                setSafe(ClientConfig.SHOW_SIDES, false);
            }
            case CHEMICALS_ONLY -> {
                setSafe(ClientConfig.COMPACT_MODE, false);
                setSafe(ClientConfig.SHOW_MACHINE_TITLE, true);
                setSafe(ClientConfig.SHOW_STATUS_BADGE, true);
                setSafe(ClientConfig.SHOW_ITEMS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_FLUIDS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_CHEMICALS_THROUGHPUT, true);
                setSafe(ClientConfig.SHOW_SPEED, false);
                setSafe(ClientConfig.SHOW_ROTATION_DIRECTION, false);
                setSafe(ClientConfig.SHOW_NETWORK_STRESS, false);
                setSafe(ClientConfig.SHOW_HEADROOM, false);
                setSafe(ClientConfig.SHOW_LOCAL_IMPACT, false);
                setSafe(ClientConfig.SHOW_ENERGY_FE, false);
                setSafe(ClientConfig.SHOW_SIDES, true);
            }
            case ENERGY_ONLY -> {
                setSafe(ClientConfig.COMPACT_MODE, false);
                setSafe(ClientConfig.SHOW_MACHINE_TITLE, true);
                setSafe(ClientConfig.SHOW_STATUS_BADGE, true);
                setSafe(ClientConfig.SHOW_ITEMS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_FLUIDS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_CHEMICALS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_SPEED, false);
                setSafe(ClientConfig.SHOW_ROTATION_DIRECTION, false);
                setSafe(ClientConfig.SHOW_NETWORK_STRESS, false);
                setSafe(ClientConfig.SHOW_HEADROOM, false);
                setSafe(ClientConfig.SHOW_LOCAL_IMPACT, false);
                setSafe(ClientConfig.SHOW_ENERGY_FE, true);
                setSafe(ClientConfig.SHOW_SIDES, false);
            }
            case ITEMS_ONLY -> {
                setSafe(ClientConfig.COMPACT_MODE, false);
                setSafe(ClientConfig.SHOW_MACHINE_TITLE, true);
                setSafe(ClientConfig.SHOW_STATUS_BADGE, true);
                setSafe(ClientConfig.SHOW_ITEMS_THROUGHPUT, true);
                setSafe(ClientConfig.SHOW_FLUIDS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_CHEMICALS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_SPEED, false);
                setSafe(ClientConfig.SHOW_ROTATION_DIRECTION, false);
                setSafe(ClientConfig.SHOW_NETWORK_STRESS, false);
                setSafe(ClientConfig.SHOW_HEADROOM, false);
                setSafe(ClientConfig.SHOW_LOCAL_IMPACT, false);
                setSafe(ClientConfig.SHOW_ENERGY_FE, false);
                setSafe(ClientConfig.SHOW_SIDES, true);
            }
            case FLUIDS_ONLY -> {
                setSafe(ClientConfig.COMPACT_MODE, false);
                setSafe(ClientConfig.SHOW_MACHINE_TITLE, true);
                setSafe(ClientConfig.SHOW_STATUS_BADGE, true);
                setSafe(ClientConfig.SHOW_ITEMS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_FLUIDS_THROUGHPUT, true);
                setSafe(ClientConfig.SHOW_CHEMICALS_THROUGHPUT, false);
                setSafe(ClientConfig.SHOW_SPEED, false);
                setSafe(ClientConfig.SHOW_ROTATION_DIRECTION, false);
                setSafe(ClientConfig.SHOW_NETWORK_STRESS, false);
                setSafe(ClientConfig.SHOW_HEADROOM, false);
                setSafe(ClientConfig.SHOW_LOCAL_IMPACT, false);
                setSafe(ClientConfig.SHOW_ENERGY_FE, false);
                setSafe(ClientConfig.SHOW_SIDES, true);
            }
        }
    }

    private static void setSafe(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue config, boolean val) {
        if (config != null) config.set(val);
    }

    private TelemetryData createSampleTelemetryData() {
        boolean hasCreate = CreateKineticInspector.isCreateLoaded();
        boolean hasMek = MekanismChemicalInspector.isMekanismLoaded();

        float[] sampleSides = new float[18];
        sampleSides[net.minecraft.core.Direction.DOWN.ordinal()] = 8.5f;       // Item output on DOWN
        sampleSides[6 + net.minecraft.core.Direction.NORTH.ordinal()] = 250.0f; // Fluid input on NORTH

        return new TelemetryData(
                12.5f, true,
                250.0f, true,
                1500.0f, true,
                hasMek ? 50.0f : 0.0f, hasMek, hasMek ? "Hydrogen" : "", hasMek ? 500L : 0L, hasMek ? 1000L : 0L,
                hasCreate ? 128.0f : 0.0f, hasCreate ? 8192.0f : 0.0f, hasCreate ? 65536.0f : 0.0f, hasCreate ? 256.0f : 0.0f, hasCreate,
                CreateKineticInspector.RotationDirection.CLOCKWISE,
                TelemetryData.BottleneckState.OPTIMAL,
                new IntArrayList(), new IntArrayList(),
                sampleSides
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("machinemetrics.hud.drag_hint"), this.width / 2, 20, 0xAAAAAA);

        // Build sample preview lines based on user config toggles
        TelemetryData sampleData = createSampleTelemetryData();
        List<TelemetryHudRenderer.HudLine> lines = TelemetryHudRenderer.buildHudLines("Sample Generator", sampleData);
        if (lines.isEmpty()) {
            lines = List.of(new TelemetryHudRenderer.HudLine(Component.translatable("machinemetrics.hud.all_hidden"), 0xAAAAAA));
        }

        Font font = this.font;
        int maxLineWidth = 0;
        for (TelemetryHudRenderer.HudLine line : lines) {
            int w = font.width(line.text());
            if (w > maxLineWidth) maxLineWidth = w;
        }

        int paddingX = 8;
        int paddingY = 6;
        int lineHeight = font.lineHeight + 3;
        int boxWidth = maxLineWidth + (paddingX * 2);
        int boxHeight = (lines.size() * lineHeight) + (paddingY * 2) - 2;

        int[] coords = TelemetryHudRenderer.calculateRenderCoordinates(boxWidth, boxHeight, this.width, this.height);
        int renderX = coords[0];
        int renderY = coords[1];

        int bgColor = ClientConfig.parseHexColor(ClientConfig.BACKGROUND_COLOR, 0x90000000);
        int borderColor = isDragging ? 0xFFFFFFFF : ClientConfig.parseHexColor(ClientConfig.BORDER_COLOR, 0xFF00FFCC);

        // Draw preview box
        graphics.fill(renderX, renderY, renderX + boxWidth, renderY + boxHeight, bgColor);
        graphics.renderOutline(renderX, renderY, boxWidth, boxHeight, borderColor);

        // Render preview lines
        int currentY = renderY + paddingY;
        for (TelemetryHudRenderer.HudLine line : lines) {
            graphics.drawString(font, line.text(), renderX + paddingX, currentY, line.color());
            currentY += lineHeight;
        }

        // Hint under box if hovered
        if (isMouseOverBox(mouseX, mouseY, renderX, renderY, boxWidth, boxHeight)) {
            graphics.drawString(font, Component.translatable("machinemetrics.hud.drag_action"), renderX, renderY - 10, 0xFFFF55);
        }
    }

    private boolean isMouseOverBox(int mouseX, int mouseY, int boxX, int boxY, int boxWidth, int boxHeight) {
        return mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check if clicking inside HUD box
            TelemetryData sampleData = createSampleTelemetryData();
            List<TelemetryHudRenderer.HudLine> lines = TelemetryHudRenderer.buildHudLines("Sample Generator", sampleData);
            if (lines.isEmpty()) {
                lines = List.of(new TelemetryHudRenderer.HudLine(Component.translatable("machinemetrics.hud.all_hidden"), 0xAAAAAA));
            }
            int maxLineWidth = 0;
            for (TelemetryHudRenderer.HudLine line : lines) {
                int w = this.font.width(line.text());
                if (w > maxLineWidth) maxLineWidth = w;
            }
            int paddingX = 8;
            int paddingY = 6;
            int lineHeight = this.font.lineHeight + 3;
            int boxWidth = maxLineWidth + (paddingX * 2);
            int boxHeight = (lines.size() * lineHeight) + (paddingY * 2) - 2;

            int[] coords = TelemetryHudRenderer.calculateRenderCoordinates(boxWidth, boxHeight, this.width, this.height);
            int renderX = coords[0];
            int renderY = coords[1];

            if (isMouseOverBox((int) mouseX, (int) mouseY, renderX, renderY, boxWidth, boxHeight)) {
                isDragging = true;
                dragMouseStartX = (int) mouseX;
                dragMouseStartY = (int) mouseY;
                dragInitialOffsetX = ClientConfig.HUD_X_OFFSET != null ? ClientConfig.HUD_X_OFFSET.get() : 10;
                dragInitialOffsetY = ClientConfig.HUD_Y_OFFSET != null ? ClientConfig.HUD_Y_OFFSET.get() : 10;
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
            int deltaX = (int) mouseX - dragMouseStartX;
            int deltaY = (int) mouseY - dragMouseStartY;

            ClientConfig.HudAnchor anchor = ClientConfig.HUD_ANCHOR != null ? ClientConfig.HUD_ANCHOR.get() : ClientConfig.HudAnchor.TOP_LEFT;

            int newOffsetX = dragInitialOffsetX;
            int newOffsetY = dragInitialOffsetY;

            switch (anchor) {
                case TOP_LEFT -> {
                    newOffsetX = dragInitialOffsetX + deltaX;
                    newOffsetY = dragInitialOffsetY + deltaY;
                }
                case TOP_RIGHT -> {
                    newOffsetX = dragInitialOffsetX - deltaX;
                    newOffsetY = dragInitialOffsetY + deltaY;
                }
                case BOTTOM_LEFT -> {
                    newOffsetX = dragInitialOffsetX + deltaX;
                    newOffsetY = dragInitialOffsetY - deltaY;
                }
                case BOTTOM_RIGHT -> {
                    newOffsetX = dragInitialOffsetX - deltaX;
                    newOffsetY = dragInitialOffsetY - deltaY;
                }
                case CROSSHAIR -> {
                    newOffsetX = dragInitialOffsetX + deltaX;
                    newOffsetY = dragInitialOffsetY + deltaY;
                }
            }

            if (ClientConfig.HUD_X_OFFSET != null) {
                ClientConfig.HUD_X_OFFSET.set(newOffsetX);
            }
            if (ClientConfig.HUD_Y_OFFSET != null) {
                ClientConfig.HUD_Y_OFFSET.set(newOffsetY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void onClose() {
        if (ClientConfig.SPEC != null) {
            try {
                ClientConfig.SPEC.save();
            } catch (Throwable ignored) {
            }
        }
        if (this.minecraft != null && this.lastScreen != null) {
            this.minecraft.setScreen(this.lastScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level == null) {
            this.renderMenuBackground(graphics);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
