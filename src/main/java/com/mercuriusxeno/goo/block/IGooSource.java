package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.GooContents;

/**
 * Read/write access to a goo reservoir for push operations.
 * Implemented by {@link GooFluidHandler} and testable without NeoForge bootstrapping.
 */
public interface IGooSource {

    /** Returns true if the source contains no goo.
     *
     * @return true if empty
     */
    boolean isEmpty();

    /** Returns a snapshot of the current goo contents.
     *
     * @return the goo contents
     */
    GooContents toGooContents();

    /** Loads contents from a snapshot, replacing current state.
     *
     * @param contents the goo contents
     */
    void loadFrom(GooContents contents);
}
