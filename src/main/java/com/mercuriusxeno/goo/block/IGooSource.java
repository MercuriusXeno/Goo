package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.GooContents;

/**
 * Read/write access to a goo reservoir for push operations.
 * Implemented by {@link GooFluidHandler} and testable without NeoForge bootstrapping.
 */
public interface IGooSource {

    /** Returns true if the source contains no goo. */
    boolean isEmpty();

    /** Returns a snapshot of the current goo contents. */
    GooContents toGooContents();

    /** Loads contents from a snapshot, replacing current state. */
    void loadFrom(GooContents contents);
}
