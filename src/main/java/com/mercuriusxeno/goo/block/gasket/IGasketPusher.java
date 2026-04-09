package com.mercuriusxeno.goo.block.gasket;

/**
 * Pushes goo from a reservoir to a gasket partner on a timed interval.
 * Owns the push timer, endpoint cache, and dispatch logic.
 */
public interface IGasketPusher {

    /** Advances the push timer; pushes when the interval elapses. */
    void tick();

    /** Rebuilds the endpoint cache (call when partner or level changes). */
    void rebuildCache();

    /** Releases the forced chunk ticket and clears the endpoint cache. */
    void dispose();
}
