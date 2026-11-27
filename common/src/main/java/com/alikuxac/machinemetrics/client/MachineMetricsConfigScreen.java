package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.config.ClientConfig;
import com.alikuxac.machinemetrics.platform.config.Config;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class MachineMetricsConfigScreen extends Screen {

    private static final String ON_KEY = "machinemetrics.hud.value.on";
    private static final String OFF_KEY = "machinemetrics.hud.value.off";

    private final Screen parent;
    private final List<ToggleSetting> settings;

    public MachineMetricsConfigScreen(Screen parent) {
        super(Component.translatable("machinemetrics.screen.config"));
        this.parent = parent;
        this.settings = List.of(
                new ToggleSetting("machinemetrics.hud.detailed_title", ClientConfig.HUD_ENABLED),
                new ToggleSetting("machinemetrics.config.show_machine_title", ClientConfig.SHOW_MACHINE_TITLE),
                new ToggleSetting("machinemetrics.config.show_status_badge", ClientConfig.SHOW_STATUS_BADGE),
                new ToggleSetting("machinemetrics.config.compact_mode", ClientConfig.COMPACT_MODE),
                new ToggleSetting("machinemetrics.hud.compact_numbers", ClientConfig.COMPACT_NUMBERS, true),
                new ToggleSetting("machinemetrics.metrics.items", ClientConfig.SHOW_ITEMS_THROUGHPUT),
                new ToggleSetting("machinemetrics.metrics.fluids", ClientConfig.SHOW_FLUIDS_THROUGHPUT),
                new ToggleSetting("machinemetrics.metrics.chemicals", ClientConfig.SHOW_CHEMICALS_THROUGHPUT),
                new ToggleSetting("machinemetrics.metrics.energy", ClientConfig.SHOW_ENERGY_FE),
                new ToggleSetting("machinemetrics.metrics.speed", ClientConfig.SHOW_SPEED),
                new ToggleSetting("machinemetrics.metrics.network", ClientConfig.SHOW_NETWORK_STRESS),
                new ToggleSetting("machinemetrics.hud.storage.capacity", ClientConfig.SHOW_STORAGE_CAPACITY),
                new ToggleSetting("machinemetrics.config.show_time_to_full", ClientConfig.SHOW_TIME_TO_FULL),
                new ToggleSetting("machinemetrics.config.show_targeted_face_io", ClientConfig.SHOW_TARGETED_FACE_IO),
                new ToggleSetting("machinemetrics.metrics.redstone", ClientConfig.SHOW_REDSTONE),
                new ToggleSetting("machinemetrics.metrics.ae_power", ClientConfig.SHOW_AE2_POWER)
        );
    }

    @Override
    protected void init() {
        int columns = 2;
        int rows = (settings.size() + columns - 1) / columns;
        int margin = 10;
        int gap = 8;
        int buttonWidth = Math.max(1, (width - margin * 2 - gap) / columns);
        int top = 42;
        int bottom = height - 42;
        int rowSpacing = Math.max(18, Math.min(28, (bottom - top) / rows));
        int buttonHeight = Math.min(20, rowSpacing - 2);

        for (int index = 0; index < settings.size(); index++) {
            ToggleSetting setting = settings.get(index);
            int column = index / rows;
            int row = index % rows;
            int x = margin + column * (buttonWidth + gap);
            int y = top + row * rowSpacing;
            addRenderableWidget(Button.builder(setting.getMessage(), button -> {
                setting.value.set(!setting.value.get());
                button.setMessage(setting.getMessage());
            }).bounds(x, y, buttonWidth, buttonHeight).build());
        }

        int doneWidth = 60;
        int layoutWidth = Math.min(190, Math.max(100, width - margin * 2 - doneWidth - gap));
        int footerWidth = layoutWidth + gap + doneWidth;
        int footerX = (width - footerWidth) / 2;
        int footerY = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("machinemetrics.config.open_position_editor"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(new HudPositionScreen(this));
            }
        }).bounds(footerX, footerY, layoutWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(footerX + layoutWidth + gap, footerY, doneWidth, 20)
                .build());
    }

    @Override
    public void onClose() {
        persistConfig();
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        super.removed();
        persistConfig();
    }

    private static void persistConfig() {
        if (ClientConfig.SPEC != null) {
            try {
                ClientConfig.SPEC.save();
            } catch (Throwable ignored) {
            }
        }
    }

    private record ToggleSetting(String labelKey, Config.Value<Boolean> value, boolean valueAsArgument) {
        private ToggleSetting(String labelKey, Config.Value<Boolean> value) {
            this(labelKey, value, false);
        }

        private Component getMessage() {
            boolean enabled = value.get();
            Component state = Component.translatable(enabled ? ON_KEY : OFF_KEY);
            if (valueAsArgument) {
                return Component.translatable(labelKey, state);
            }
            return Component.translatable(labelKey).append(": ").append(state);
        }
    }
}
