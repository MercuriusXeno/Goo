package com.mercuriusxeno.goo.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EffectMath.isFuseLive: fuse state checks.
 */
class ChainMarkerEffectTest {

    /** Fuse at 1 tick remaining is still live. */
    @Test
    void fuseAtOneIsLive() {
        assertTrue(EffectMath.isFuseLive(1));
    }

    /** Fuse at 0 ticks is expired. */
    @Test
    void fuseAtZeroIsExpired() {
        assertFalse(EffectMath.isFuseLive(0));
    }

    /** Fuse at negative ticks is expired. */
    @Test
    void fuseNegativeIsExpired() {
        assertFalse(EffectMath.isFuseLive(-1));
    }

    /** Fuse at full duration (10 ticks) is live. */
    @Test
    void fuseAtFullDurationIsLive() {
        assertTrue(EffectMath.isFuseLive(10));
    }
}
