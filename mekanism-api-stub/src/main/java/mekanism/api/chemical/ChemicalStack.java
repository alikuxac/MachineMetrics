package mekanism.api.chemical;

/**
 * Compile-only stub of Mekanism's chemical stack.
 */
public abstract class ChemicalStack {

    public abstract boolean isEmpty();

    public abstract long getAmount();

    public abstract Chemical getChemical();

    public abstract String getTranslationKey();
}
