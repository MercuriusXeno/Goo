package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.item.GooContents;

/**
 * Read/write access to a multi-type goo reservoir.
 * Shared contract between Crucible (producer) and Vat (bulk storage).
 * Implementors provide their backing handler via {@link #reservoirHandler()};
 * all three operations delegate to it by default.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface") // not a lambda target; sole abstract is a composed-state accessor
public interface IGooReservoir {

    /**
     * Returns the backing fluid handler for this reservoir.
     * Used by default method implementations; each machine returns its own field.
     *
     * @return the goo fluid handler
     */
    GooFluidHandler reservoirHandler();

    /** Returns the current goo contents as an immutable snapshot.
     *
     * @return the reservoir
     */
    default GooContents getReservoir() {
        return reservoirHandler().toGooContents();
    }

    /**
     * Inserts goo of the given type and volume into the reservoir.
     *
     * @param type   the goo type to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    default int insertGoo(GooType type, int volume) {
        return reservoirHandler().insertGoo(type, Math.min(volume, Integer.MAX_VALUE), false);
    }

    /**
     * Extracts up to the given amount of a specific goo type.
     *
     * @param type   the goo type to extract
     * @param amount maximum volume to extract in microblobs
     * @return the amount actually extracted
     */
    default int extractGoo(GooType type, int amount) {
        return reservoirHandler().extractGoo(type, Math.min(amount, Integer.MAX_VALUE), false);
    }
}
