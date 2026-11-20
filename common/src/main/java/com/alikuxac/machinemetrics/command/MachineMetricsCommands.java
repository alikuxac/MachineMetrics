package com.alikuxac.machinemetrics.command;

import com.alikuxac.machinemetrics.compat.CompatRegistry;
import com.alikuxac.machinemetrics.compat.ae2.AE2NetworkInspector;
import com.alikuxac.machinemetrics.compat.create.CreateKineticInspector;
import com.alikuxac.machinemetrics.compat.mekanism.MekanismChemicalInspector;
import com.alikuxac.machinemetrics.compat.mekanism.MekanismTransporterInspector;
import com.alikuxac.machinemetrics.compat.storage.DrawerStorageAdapter;
import com.alikuxac.machinemetrics.compat.storage.SophisticatedStorageAdapter;
import com.alikuxac.machinemetrics.platform.config.ConfigReload;
import com.alikuxac.machinemetrics.platform.storage.EnergyStorage;
import com.alikuxac.machinemetrics.platform.storage.FluidStorage;
import com.alikuxac.machinemetrics.platform.storage.ItemStorage;
import com.alikuxac.machinemetrics.platform.storage.StorageAccess;
import com.alikuxac.machinemetrics.telemetry.TelemetryRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MachineMetricsCommands {

    private static final SimpleCommandExceptionType ERROR_NO_TARGET =
            new SimpleCommandExceptionType(Component.translatable("machinemetrics.command.error.no_target"));
    private static final SimpleCommandExceptionType ERROR_NOT_PLAYER =
            new SimpleCommandExceptionType(Component.translatable("machinemetrics.command.error.not_player"));

    private MachineMetricsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("machinemetrics"));
        dispatcher.register(root("mm"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspect(ctx, null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> inspect(ctx, null))
                                .then(Commands.argument("side", StringArgumentType.word())
                                        .executes(ctx -> inspect(ctx, ctx.getArgument("side", String.class))))))
                .then(Commands.literal("compat").executes(MachineMetricsCommands::compat))
                .then(Commands.literal("reload").executes(MachineMetricsCommands::reload))
                .then(Commands.literal("server")
                        .then(Commands.literal("stats").executes(MachineMetricsCommands::serverStats))
                        .then(Commands.literal("ratelimit")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 200))
                                        .executes(MachineMetricsCommands::serverRatelimit))));
    }

    private static int inspect(CommandContext<CommandSourceStack> ctx, String sideName) {
        try {
            CommandSourceStack source = ctx.getSource();
            ServerLevel level = source.getLevel();
            BlockPos pos = resolvePos(ctx);
            Direction face = parseDirection(sideName);
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);

            send(source, Component.translatable("machinemetrics.command.inspect.header")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            send(source, "machinemetrics.command.inspect.block", state.getBlock().getDescriptionId(),
                    pos.toShortString(), face != null ? face.getName() : "-");
            send(source, "machinemetrics.command.inspect.block_entity",
                    be != null ? be.getClass().getName() : "-");

            ItemStorage items = StorageAccess.item(level, pos, face);
            if (items != null && items.getSlots() > 0) {
                int stored = 0;
                int capacity = 0;
                for (int i = 0; i < items.getSlots(); i++) {
                    ItemStack stack = items.getStackInSlot(i);
                    stored += stack.isEmpty() ? 0 : stack.getCount();
                    capacity += items.getSlotLimit(i);
                }
                send(source, Component.translatable("machinemetrics.command.inspect.items",
                        items.getSlots(), stored, capacity,
                        String.valueOf(items.canInsert()), String.valueOf(items.canExtract()))
                        .withStyle(ChatFormatting.YELLOW));
            }

            FluidStorage fluids = StorageAccess.fluid(level, pos, face);
            if (fluids != null && fluids.getTanks() > 0) {
                long stored = 0;
                long capacity = 0;
                for (int i = 0; i < fluids.getTanks(); i++) {
                    stored += fluids.getTankAmount(i);
                    capacity += fluids.getTankCapacity(i);
                }
                send(source, Component.translatable("machinemetrics.command.inspect.fluids",
                        fluids.getTanks(), stored, capacity,
                        String.valueOf(fluids.canFill()), String.valueOf(fluids.canDrain()))
                        .withStyle(ChatFormatting.AQUA));
            }

            EnergyStorage energy = StorageAccess.energy(level, pos, face);
            if (energy != null && energy.getMaxEnergyStored() > 0) {
                send(source, Component.translatable("machinemetrics.command.inspect.energy",
                        energy.getEnergyStored(), energy.getMaxEnergyStored(),
                        String.valueOf(energy.canReceive()), String.valueOf(energy.canExtract()))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            addAdapterLines(source, level, pos, face, be);
            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        } catch (Throwable e) {
            ctx.getSource().sendFailure(Component.translatable("machinemetrics.command.error.failed", String.valueOf(e)));
            return 0;
        }
    }

    private static void addAdapterLines(CommandSourceStack source, ServerLevel level, BlockPos pos,
                                        Direction face, BlockEntity be) {
        if (be == null) {
            return;
        }
        try {
            if (MekanismChemicalInspector.isMekanismLoaded()) {
                Optional<MekanismChemicalInspector.ChemicalMetricData> chem =
                        MekanismChemicalInspector.inspectChemicals(level, pos, face);
                if (chem.isPresent() && !chem.get().isEmpty()) {
                    send(source, "machinemetrics.command.inspect.mekanism_chemical",
                            chem.get().chemicalName(), chem.get().amount(), chem.get().capacity());
                }
                MekanismTransporterInspector.TransporterMetricData transporter = MekanismTransporterInspector.inspect(be).orElse(null);
                if (transporter != null && transporter.isTransporter()) {
                    send(source, "machinemetrics.command.inspect.mekanism_transporter",
                            transporter.currentItems(), transporter.maxCapacity(),
                            String.format(Locale.ROOT, "%.2f", transporter.throughputRate()));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (CreateKineticInspector.isCreateLoaded()) {
                Optional<CreateKineticInspector.KineticData> kinetic = CreateKineticInspector.inspectKinetic(level, pos, face);
                if (kinetic.isPresent() && !kinetic.get().isEmpty()) {
                    CreateKineticInspector.KineticData data = kinetic.get();
                    send(source, "machinemetrics.command.inspect.kinetic",
                            String.format(Locale.ROOT, "%.1f", data.speed()),
                            String.format(Locale.ROOT, "%.1f", data.stress()),
                            String.format(Locale.ROOT, "%.1f", data.capacity()));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (AE2NetworkInspector.isAE2Loaded()) {
                Optional<AE2NetworkInspector.AE2MetricData> ae = AE2NetworkInspector.inspectNetwork(level, pos, face);
                if (ae.isPresent() && !ae.get().isEmpty()) {
                    AE2NetworkInspector.AE2MetricData data = ae.get();
                    send(source, "machinemetrics.command.inspect.ae2",
                            String.format(Locale.ROOT, "%.1f", data.powerRate()),
                            String.format(Locale.ROOT, "%.1f", data.powerDrain()),
                            data.usedChannels(), data.maxChannels(),
                            AE2NetworkInspector.AE2StabilityState.fromOrdinal(data.stabilityState()));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (DrawerStorageAdapter.isFunctionalStorageLoaded() || DrawerStorageAdapter.isStorageDrawersLoaded()) {
                Optional<DrawerStorageAdapter.DrawerMetricData> drawer = DrawerStorageAdapter.inspectDrawer(level, pos);
                if (drawer.isPresent() && !drawer.get().isEmpty()) {
                    send(source, "machinemetrics.command.inspect.drawer",
                            drawer.get().storedItems(), drawer.get().maxCapacity(),
                            String.valueOf(drawer.get().isInfinite()));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (CompatRegistry.isSophisticatedStorageBlock(level, pos)) {
                Optional<SophisticatedStorageAdapter.StorageMetricData> storage =
                        SophisticatedStorageAdapter.inspectStorage(level, pos, face);
                if (storage.isPresent() && !storage.get().isEmpty()) {
                    send(source, "machinemetrics.command.inspect.sophisticated",
                            storage.get().storedItems(), storage.get().maxCapacity());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int compat(CommandContext<CommandSourceStack> ctx) {
        List<Component> active = new ArrayList<>();
        addIf(active, Component.translatable("machinemetrics.compat.mod.ae2"),
                CompatRegistry.isModLoaded("ae2") || AE2NetworkInspector.isAE2Loaded());
        addIf(active, Component.translatable("machinemetrics.compat.mod.create"),
                CompatRegistry.isModLoaded("create") || CreateKineticInspector.isCreateLoaded());
        addIf(active, Component.translatable("machinemetrics.compat.mod.mekanism"),
                CompatRegistry.isModLoaded("mekanism") || MekanismChemicalInspector.isMekanismLoaded());
        addIf(active, Component.translatable("machinemetrics.compat.mod.sophisticatedstorage"),
                CompatRegistry.isModLoaded("sophisticatedstorage"));
        addIf(active, Component.translatable("machinemetrics.compat.mod.functionalstorage"),
                DrawerStorageAdapter.isFunctionalStorageLoaded());
        addIf(active, Component.translatable("machinemetrics.compat.mod.storagedrawers"),
                DrawerStorageAdapter.isStorageDrawersLoaded());

        CommandSourceStack source = ctx.getSource();
        send(source, Component.translatable("machinemetrics.command.compat.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (active.isEmpty()) {
            send(source, Component.translatable("machinemetrics.command.compat.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            for (Component name : active) {
                send(source, Component.translatable("machinemetrics.command.compat.entry", name)
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        return active.size();
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ConfigReload.reloadServer();
        ConfigReload.reloadClient();
        ctx.getSource().sendSuccess(() -> Component.translatable("machinemetrics.command.reload.success")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int serverStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        send(source, Component.translatable("machinemetrics.command.server.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        send(source, "machinemetrics.command.server.clients", TelemetryRuntime.activeClientCount());
        send(source, "machinemetrics.command.server.dispatch", TelemetryRuntime.dispatchRateHz());
        send(source, "machinemetrics.command.server.scan",
                String.format(Locale.ROOT, "%.1f", TelemetryRuntime.averageScanMicros()));
        send(source, "machinemetrics.command.server.interval", TelemetryRuntime.getMinRequestIntervalTicks());
        return 1;
    }

    private static int serverRatelimit(CommandContext<CommandSourceStack> ctx) {
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        TelemetryRuntime.setMinRequestIntervalTicks(ticks);
        int effective = TelemetryRuntime.getMinRequestIntervalTicks();
        ctx.getSource().sendSuccess(() -> Component.translatable("machinemetrics.command.server.ratelimit", effective)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static void addIf(List<Component> list, Component name, boolean condition) {
        if (condition) {
            list.add(name);
        }
    }

    private static BlockPos resolvePos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        try {
            return BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        } catch (IllegalArgumentException ignored) {
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            throw ERROR_NOT_PLAYER.create();
        }
        HitResult hit = player.pick(12.0D, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        throw ERROR_NO_TARGET.create();
    }

    private static Direction parseDirection(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            if (direction.getName().equalsIgnoreCase(name)) {
                return direction;
            }
        }
        return null;
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void send(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args), false);
    }
}
