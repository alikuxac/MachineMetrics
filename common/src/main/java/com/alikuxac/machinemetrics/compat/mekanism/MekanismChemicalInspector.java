package com.alikuxac.machinemetrics.compat.mekanism;

import com.alikuxac.machinemetrics.compat.Reflect;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

public class MekanismChemicalInspector {

    public record ChemicalMetricData(
            String chemicalName,
            String translationKey,
            long amount,
            long capacity
    ) {
        public boolean isEmpty() {
            return amount <= 0 || chemicalName == null || chemicalName.isEmpty();
        }

        public boolean hasCapacity() {
            return capacity > 0;
        }
    }

    private static final Direction[] ALL_SIDES = new Direction[] {
            null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static Boolean capabilityInitialized = null;
    private static Object chemicalCapability = null;

    private static synchronized Object resolveChemicalCapability() {
        if (capabilityInitialized != null) {
            return chemicalCapability;
        }
        capabilityInitialized = Boolean.TRUE;
        try {
            ClassLoader cl = MekanismChemicalInspector.class.getClassLoader();
            Class<?> capabilities = Class.forName("mekanism.common.capabilities.Capabilities", false, cl);
            Field field = Reflect.findField(capabilities, "CHEMICAL");
            if (field != null) {
                Object holder = field.get(null);
                chemicalCapability = unwrapBlockCapability(holder);
            }
        } catch (Throwable ignored) {
            chemicalCapability = null;
        }
        return chemicalCapability;
    }

    private static Object unwrapBlockCapability(Object holder) {
        if (holder == null) {
            return null;
        }
        // ponytail: NeoForge BlockCapability is already the capability; Forge's Capability too.
        String cn = holder.getClass().getName();
        if (cn.endsWith(".capabilities.BlockCapability") || cn.endsWith("common.capabilities.Capability")) {
            return holder;
        }
        try {
            Method block = holder.getClass().getMethod("block");
            Object unwrapped = block.invoke(holder);
            if (unwrapped != null) {
                return unwrapped;
            }
        } catch (Throwable ignored) {
        }
        return holder;
    }

    private static IChemicalHandler queryCapability(Level level, BlockPos pos, Direction side) {
        Object cap = resolveChemicalCapability();
        if (cap == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = findGetCapabilityMethod();
            if (method == null) {
                return null;
            }
            Object result = method.invoke(level, cap, pos, side);
            if (result instanceof IChemicalHandler handler) {
                return handler;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method getCapabilityMethod = null;
    private static boolean getCapabilityResolved = false;

    private static synchronized Method findGetCapabilityMethod() {
        if (getCapabilityResolved) {
            return getCapabilityMethod;
        }
        getCapabilityResolved = true;
        for (Method m : Level.class.getMethods()) {
            if (!m.getName().equals("getCapability") || m.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params[1] == BlockPos.class) {
                getCapabilityMethod = m;
                break;
            }
        }
        return getCapabilityMethod;
    }

    public static Optional<ChemicalMetricData> inspectChemicals(Level level, BlockPos pos, Direction side) {
        if (level == null || pos == null) {
            return Optional.empty();
        }

        if (!isMekanismLoaded()) {
            return Optional.empty();
        }

        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                return Optional.empty();
            }

            IChemicalHandler capabilityHandler = queryCapability(level, pos, side);
            if (capabilityHandler != null) {
                ChemicalMetricData data = processHandler(capabilityHandler);
                if (data != null) {
                    return Optional.of(data);
                }
            }

            if (be instanceof IChemicalHandler handler) {
                ChemicalMetricData data = processHandler(handler);
                if (data != null) {
                    return Optional.of(data);
                }
            }

            IChemicalHandler unsided = queryCapability(level, pos, null);
            if (unsided != null && unsided.getChemicalTanks() > 0) {
                ChemicalMetricData data = processHandler(unsided);
                if (data != null) {
                    return Optional.of(data);
                }
            }

            Set<IChemicalHandler> handlers = Collections.newSetFromMap(new IdentityHashMap<>());
            long totalAmount = 0L;
            long totalCapacity = 0L;
            String displayName = null;
            String displayKey = null;
            for (Direction candidate : ALL_SIDES) {
                IChemicalHandler handler = queryCapability(level, pos, candidate);
                if (handler != null && handlers.add(handler)) {
                    int tanks = handler.getChemicalTanks();
                    for (int i = 0; i < tanks; i++) {
                        long cap = handler.getChemicalTankCapacity(i);
                        if (cap > 0) {
                            totalCapacity += cap;
                        }
                        ChemicalStack stack = handler.getChemicalInTank(i);
                        if (!stack.isEmpty()) {
                            totalAmount += stack.getAmount();
                            if (displayName == null) {
                                displayName = stack.getChemical().getTextComponent().getString();
                                displayKey = stack.getTranslationKey();
                            }
                        }
                    }
                }
            }

            if (totalCapacity <= 0 && totalAmount <= 0) {
                return Optional.empty();
            }
            return Optional.of(new ChemicalMetricData(
                    displayName != null ? displayName : "",
                    displayKey != null ? displayKey : "",
                    totalAmount,
                    totalCapacity
            ));
        } catch (Throwable ignored) {

        }

        return Optional.empty();
    }

    private static Boolean mekanismLoadedCache = null;

    public static boolean isMekanismLoaded() {
        if (mekanismLoadedCache == null) {
            try {
                Class.forName("mekanism.api.chemical.IChemicalHandler", false, MekanismChemicalInspector.class.getClassLoader());
                mekanismLoadedCache = true;
            } catch (Throwable ignored) {
                mekanismLoadedCache = false;
            }
        }
        return mekanismLoadedCache;
    }


    private static ChemicalMetricData processHandler(IChemicalHandler handler) {
        int tanks = handler.getChemicalTanks();
        if (tanks <= 0) {
            return null;
        }

        long totalAmount = 0L;
        long totalCapacity = 0L;
        String displayName = null;
        String displayKey = null;

        for (int i = 0; i < tanks; i++) {
            long capacity = handler.getChemicalTankCapacity(i);
            if (capacity > 0) {
                totalCapacity += capacity;
            }
            ChemicalStack stack = handler.getChemicalInTank(i);
            if (!stack.isEmpty()) {
                totalAmount += stack.getAmount();
                if (displayName == null) {
                    displayName = stack.getChemical().getTextComponent().getString();
                    displayKey = stack.getTranslationKey();
                }
            }
        }

        if (totalCapacity <= 0 && totalAmount <= 0) {
            return null;
        }

        return new ChemicalMetricData(
                displayName != null ? displayName : "",
                displayKey != null ? displayKey : "",
                totalAmount,
                totalCapacity
        );
    }
}