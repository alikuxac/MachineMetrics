package mekanism.api.chemical;

/**
 * Compile-only stub of Mekanism's chemical handler capability.
 */
public interface IChemicalHandler {

    int getChemicalTanks();

    ChemicalStack getChemicalInTank(int tank);

    long getChemicalTankCapacity(int tank);
}
