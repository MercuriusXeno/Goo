package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;

/**
 * Read/write access to a multi-type goo reservoir.
 * Shared contract between Crucible (producer) and Vat (bulk storage).
 */
public interface IGooReservoir {

    /** Returns the current goo contents as an immutable snapshot. */
    GooContents getReservoir();

    /**
     * Inserts goo of the given type and volume into the reservoir.
     *
     * @param type   the goo type to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    long insertGoo(GooType type, long volume);

    /**
     * Extracts up to the given amount of a specific goo type.
     *
     * @param type   the goo type to extract
     * @param amount maximum volume to extract in microblobs
     * @return the amount actually extracted
     */
    long extractGoo(GooType type, long amount);
}
