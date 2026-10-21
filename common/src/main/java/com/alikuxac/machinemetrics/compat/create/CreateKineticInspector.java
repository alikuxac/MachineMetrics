package com.alikuxac.machinemetrics.compat.create;

import com.alikuxac.machinemetrics.compat.Reflect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.Optional;

public class CreateKineticInspector {

    public enum RotationDirection {
        CLOCKWISE("CW \u27F3"),
        COUNTER_CLOCKWISE("CCW \u27F2"),
        STATIONARY("\u2014");

        private final String label;
        RotationDirection(String label) { this.label = label; }
        public String getLabel() { return label; }

        public static RotationDirection fromSpeed(float rawSpeed) {
            if (Math.abs(rawSpeed) <= 0.001f) return STATIONARY;
            return rawSpeed > 0 ? CLOCKWISE : COUNTER_CLOCKWISE;
        }
    }

    public record KineticData(
            float speed,
            float stress,
            float capacity,
            float blockStress,
            float blockCapacity,
            boolean isOverstressed,
            RotationDirection direction
    ) {
        public boolean isEmpty() {
            return Math.abs(speed) <= 0.001f && Math.abs(capacity) <= 0.001f && Math.abs(stress) <= 0.001f && Math.abs(blockStress) <= 0.001f && !isOverstressed;
        }

        public float headroom() {
            return Math.max(0f, capacity - stress);
        }
    }

    private static Boolean createLoadedCache = null;
    private static Class<?> kineticBlockEntityClass = null;

    private static Method getSpeedMethod = null;
    private static Method calculateAddedStressCapacityMethod = null;
    private static Method calculateStressAppliedMethod = null;
    private static Method isOverStressedMethod = null;
    private static Method getOrCreateNetworkMethod = null;
    private static Method getNetworkCapacityMethod = null;
    private static Method calculateNetworkStressMethod = null;
    private static Method getRotationSpeedModifierMethod = null;

    public static boolean isCreateLoaded() {
        if (createLoadedCache == null) {
            try {
                kineticBlockEntityClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
                createLoadedCache = true;

                getSpeedMethod = Reflect.findMethod(kineticBlockEntityClass, "getSpeed");
                calculateAddedStressCapacityMethod = Reflect.findMethod(kineticBlockEntityClass, 
                        "calculateAddedStressCapacity", "getStressCapacity", "getCapacity");
                calculateStressAppliedMethod = Reflect.findMethod(kineticBlockEntityClass, 
                        "calculateStressApplied", "getConsumptiveStress", "getStress");
                isOverStressedMethod = Reflect.findMethod(kineticBlockEntityClass, "isOverStressed", "isOverstressed");
                getOrCreateNetworkMethod = Reflect.findMethod(kineticBlockEntityClass, "getOrCreateNetwork", "getNetwork");

                try {
                    Class<?> networkClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticNetwork");
                    getNetworkCapacityMethod = Reflect.findMethod(networkClass, "getCapacity", "calculateCapacity", "getActualCapacity");
                    calculateNetworkStressMethod = Reflect.findMethod(networkClass, "calculateStress", "getStress", "calculateStressApplied");
                } catch (Throwable ignored) {}

                try {
                    getRotationSpeedModifierMethod = kineticBlockEntityClass.getMethod("getRotationSpeedModifier", net.minecraft.core.Direction.class);
                } catch (Throwable ignored) {
                    getRotationSpeedModifierMethod = Reflect.findMethod(kineticBlockEntityClass, "getRotationSpeedModifier");
                }

            } catch (Throwable e) {
                createLoadedCache = false;
            }
        }
        return createLoadedCache;
    }

    private static Method findMethod(Class<?> clazz, String... names) {
        return Reflect.findMethod(clazz, names);
    }

    private static Float invokeFloat(Method method, Object target) {
        return Reflect.invokeFloat(method, target);
    }

    private static Float getFieldValue(Object target, String... names) {
        return Reflect.getFloatField(target, names);
    }

    public static Optional<KineticData> inspectKinetic(Level level, BlockPos pos) {
        return inspectKinetic(level, pos, null);
    }

    public static Optional<KineticData> inspectKinetic(Level level, BlockPos pos, net.minecraft.core.Direction hitFace) {
        if (level == null || pos == null || !isCreateLoaded() || kineticBlockEntityClass == null) {
            return Optional.empty();
        }

        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || !kineticBlockEntityClass.isInstance(be)) {
                return Optional.empty();
            }

            Float rawSpeedObj = invokeFloat(getSpeedMethod, be);
            float baseSpeed = rawSpeedObj != null ? rawSpeedObj : 0f;

            float effectiveSpeed = baseSpeed;
            if (hitFace != null && getRotationSpeedModifierMethod != null) {
                try {
                    Object modObj = getRotationSpeedModifierMethod.invoke(be, hitFace);
                    if (modObj instanceof Number mod) {
                        float modifier = mod.floatValue();
                        if (modifier != 0f) {
                            effectiveSpeed = baseSpeed * modifier;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            float speed = Math.abs(effectiveSpeed);
            RotationDirection direction = RotationDirection.fromSpeed(effectiveSpeed);

            float finalCapacity = 0f;
            float finalStress = 0f;

            if (getOrCreateNetworkMethod != null) {
                Object network = getOrCreateNetworkMethod.invoke(be);
                if (network != null) {
                    Float netCap = invokeFloat(getNetworkCapacityMethod, network);
                    if (netCap == null) netCap = invokeFloat(findMethod(network.getClass(), "getCapacity", "calculateCapacity", "getActualCapacity", "capacity"), network);
                    if (netCap == null) netCap = getFieldValue(network, "actualCapacity", "capacity", "totalCapacity", "netCapacity");
                    if (netCap != null && netCap > 0f) finalCapacity = netCap;

                    Float netStr = invokeFloat(calculateNetworkStressMethod, network);
                    if (netStr == null) netStr = invokeFloat(findMethod(network.getClass(), "calculateStress", "getStress", "calculateStressApplied", "stress"), network);
                    if (netStr == null) netStr = getFieldValue(network, "actualStress", "stress", "totalStress", "netStress");
                    if (netStr != null && netStr >= 0f) finalStress = netStr;
                }
            }

            Float blockAddedCapObj = invokeFloat(calculateAddedStressCapacityMethod, be);
            float blockAddedCap = blockAddedCapObj != null ? Math.abs(blockAddedCapObj) : 0f;

            Float blockAppliedStressObj = invokeFloat(calculateStressAppliedMethod, be);
            float blockAppliedStress = blockAppliedStressObj != null ? Math.abs(blockAppliedStressObj) : 0f;

            if (finalCapacity <= 0f && blockAddedCap > 0f) {
                finalCapacity = blockAddedCap * speed;
            }
            if (finalStress <= 0f && blockAppliedStress > 0f) {
                finalStress = blockAppliedStress * speed;
            }

            boolean isOverstressed = false;
            if (isOverStressedMethod != null) {
                Object overObj = isOverStressedMethod.invoke(be);
                if (overObj instanceof Boolean b) {
                    isOverstressed = b;
                }
            } else if (finalCapacity > 0f && finalStress > finalCapacity) {
                isOverstressed = true;
            }

            float currentBlockStressImpact = blockAppliedStress * speed;
            float currentBlockCapacityImpact = blockAddedCap * speed;

            KineticData data = new KineticData(speed, finalStress, finalCapacity, currentBlockStressImpact, currentBlockCapacityImpact, isOverstressed, direction);
            return data.isEmpty() ? Optional.empty() : Optional.of(data);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }
}