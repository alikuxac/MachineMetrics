package com.alikuxac.machinemetrics.compat.mekanism;

import com.alikuxac.machinemetrics.compat.Reflect;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

public class MekanismTransporterInspector {

    public record TransporterMetricData(
            boolean isTransporter,
            int currentItems,
            int maxCapacity,
            float throughputRate,
            int[] slotCounts,
            float[] sideItemCounts
    ) {
        public static final TransporterMetricData EMPTY = new TransporterMetricData(false, 0, 0, 0f, new int[0], new float[6]);
    }

    private static Boolean isMekanismLoaded = null;
    private static Class<?> transporterBaseClass = null;
    private static Method getTransmitterMethod = null;
    private static Method getTransitMethod = null;
    private static Field tierField = null;
    private static Method getSpeedMethod = null;
    private static Method getPullAmountMethod = null;
    private static Field itemStackField = null;
    private static Method getSideMethod = null;
    private static boolean initialized = false;

    private static synchronized void initReflection() {
        if (initialized) return;
        initialized = true;

        try {
            ClassLoader cl = MekanismTransporterInspector.class.getClassLoader();
            transporterBaseClass = Class.forName("mekanism.common.tile.transmitter.TileEntityLogisticalTransporterBase", false, cl);
            getTransmitterMethod = Reflect.findMethod(transporterBaseClass, "getTransmitter");

            Class<?> logisticalTransporterClass = Class.forName("mekanism.common.content.network.transmitter.LogisticalTransporterBase", false, cl);
            getTransitMethod = Reflect.findMethod(logisticalTransporterClass, "getTransit");
            tierField = Reflect.findField(logisticalTransporterClass, "tier");

            Class<?> tierClass = Class.forName("mekanism.common.tier.TransporterTier", false, cl);
            getSpeedMethod = Reflect.findMethod(tierClass, "getSpeed");
            getPullAmountMethod = Reflect.findMethod(tierClass, "getPullAmount");

            Class<?> stackClass = Class.forName("mekanism.common.content.transporter.TransporterStack", false, cl);
            itemStackField = Reflect.findField(stackClass, "itemStack", "stack");

            getSideMethod = Reflect.findMethodTyped(stackClass, new Class<?>[] { logisticalTransporterClass }, "getSide");

            isMekanismLoaded = true;
        } catch (Throwable t) {
            isMekanismLoaded = false;
        }
    }

    public static boolean isTransporter(BlockEntity be) {
        if (be == null) return false;
        initReflection();
        if (isMekanismLoaded == null || !isMekanismLoaded || transporterBaseClass == null) {
            return false;
        }
        return transporterBaseClass.isInstance(be);
    }

    public static Optional<TransporterMetricData> inspect(BlockEntity be) {
        if (!isTransporter(be)) {
            return Optional.empty();
        }

        try {
            Object transmitter = getTransmitterMethod.invoke(be);
            if (transmitter == null) {
                return Optional.empty();
            }

            Object tierObj = tierField.get(transmitter);
            int speed = 5;
            int pullAmount = 16;
            if (tierObj != null) {
                try {
                    speed = (int) getSpeedMethod.invoke(tierObj);
                } catch (Throwable ignored) {
                }
                try {
                    pullAmount = (int) getPullAmountMethod.invoke(tierObj);
                } catch (Throwable ignored) {
                }
            }
            if (speed <= 0) speed = 5;
            if (pullAmount <= 0) pullAmount = 16;

            Object transitObj = getTransitMethod.invoke(transmitter);
            int currentItems = 0;
            IntArrayList slotCounts = new IntArrayList();
            float[] sideCounts = new float[6];

            if (transitObj instanceof Collection<?> transitCollection) {
                for (Object stackObj : transitCollection) {
                    if (stackObj == null) continue;
                    try {
                        ItemStack stack = (ItemStack) itemStackField.get(stackObj);
                        if (stack != null && !stack.isEmpty()) {
                            int count = stack.getCount();
                            currentItems += count;
                            slotCounts.add(count);

                            if (getSideMethod != null) {
                                try {
                                    Direction side = (Direction) getSideMethod.invoke(stackObj, transmitter);
                                    if (side != null) {
                                        sideCounts[side.ordinal()] += count;
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            if (slotCounts.isEmpty()) {
                slotCounts.add(0);
            }

            int maxCapacity = Math.max(Math.max(64, pullAmount), currentItems);

            float blocksPerSecond = (float) speed / 5.0f;
            float throughputRate = (float) currentItems * blocksPerSecond;

            return Optional.of(new TransporterMetricData(
                    true,
                    currentItems,
                    maxCapacity,
                    throughputRate,
                    slotCounts.toIntArray(),
                    sideCounts
            ));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }
}
