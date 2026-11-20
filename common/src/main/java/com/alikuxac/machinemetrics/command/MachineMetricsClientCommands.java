package com.alikuxac.machinemetrics.command;

import com.alikuxac.machinemetrics.client.HudPositionScreen;
import com.alikuxac.machinemetrics.client.KeyBindings;
import com.alikuxac.machinemetrics.client.MetricResolver;
import com.alikuxac.machinemetrics.client.PinnedTargetManager;
import com.alikuxac.machinemetrics.network.RequestTelemetryPayload;
import com.alikuxac.machinemetrics.platform.network.NetworkPlatform;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public final class MachineMetricsClientCommands {

    private MachineMetricsClientCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("machinemetrics"));
        dispatcher.register(root("mm"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .then(Commands.literal("hud")
                        .then(Commands.literal("toggle").executes(MachineMetricsClientCommands::hudToggle))
                        .then(Commands.literal("position").executes(MachineMetricsClientCommands::hudPosition)))
                .then(Commands.literal("pin")
                        .then(Commands.literal("clear").executes(MachineMetricsClientCommands::pinClear))
                        .then(Commands.literal("pos")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(MachineMetricsClientCommands::pinPos))))));
    }

    private static int hudToggle(CommandContext<CommandSourceStack> ctx) {
        KeyBindings.hudEnabled = !KeyBindings.hudEnabled;
        Component message = Component.translatable(KeyBindings.hudEnabled
                        ? "machinemetrics.command.hud.enabled" : "machinemetrics.command.hud.disabled")
                .withStyle(KeyBindings.hudEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, true);
        } else {
            ctx.getSource().sendSuccess(() -> message, false);
        }
        return 1;
    }

    private static int hudPosition(CommandContext<CommandSourceStack> ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new HudPositionScreen(mc.screen));
        return 1;
    }

    private static int pinClear(CommandContext<CommandSourceStack> ctx) {
        PinnedTargetManager.unpin();
        Component message = Component.translatable("machinemetrics.command.pin.cleared").withStyle(ChatFormatting.YELLOW);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, true);
        } else {
            ctx.getSource().sendSuccess(() -> message, false);
        }
        return 1;
    }

    private static int pinPos(CommandContext<CommandSourceStack> ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0;
        }
        BlockPos pos = new BlockPos(
                IntegerArgumentType.getInteger(ctx, "x"),
                IntegerArgumentType.getInteger(ctx, "y"),
                IntegerArgumentType.getInteger(ctx, "z")
        );
        String name = MetricResolver.getBlockOrMachineName(mc.level, pos);
        PinnedTargetManager.pin(pos, Direction.UP, mc.level, name);
        try {
            NetworkPlatform.sendToServer(new RequestTelemetryPayload(pos, Direction.UP));
        } catch (Throwable ignored) {
        }
        mc.player.displayClientMessage(Component.translatable("machinemetrics.command.pin.pinned",
                        name, pos.toShortString()).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }
}
