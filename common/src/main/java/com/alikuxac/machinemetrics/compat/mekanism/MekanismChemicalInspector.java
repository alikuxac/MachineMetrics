package com.alikuxac.machinemetrics.compat.mekanism;

import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.MekanismAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

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

            if (be instanceof IChemicalHandler handler) {
                return processHandler(handler);
            }
        } catch (Throwable ignored) {
            // Guard against any soft-dependency reflection or class loading issues
        }

        return Optional.empty();
    }

    private static Boolean mekanismLoadedCache = null;

    public static boolean isMekanismLoaded() {
        if (mekanismLoadedCache == null) {
            try {
                Class.forName("mekanism.api.chemical.IChemicalHandler");
                mekanismLoadedCache = true;
            } catch (ClassNotFoundException e) {
                mekanismLoadedCache = false;
            }
        }
        return mekanismLoadedCache;
    }


    private static Optional<ChemicalMetricData> processHandler(IChemicalHandler handler) {
        int tanks = handler.getChemicalTanks();
        if (tanks <= 0) {
            return Optional.empty();
        }

        for (int i = 0; i < tanks; i++) {
            ChemicalStack stack = handler.getChemicalInTank(i);
            long capacity = handler.getChemicalTankCapacity(i);
            if (!stack.isEmpty()) {
                String name = stack.getChemical().getTextComponent().getString();
                String translationKey = stack.getTranslationKey();
                long amount = stack.getAmount();
                return Optional.of(new ChemicalMetricData(name, translationKey, amount, capacity));
            }
        }

        return Optional.empty();
    }
}
