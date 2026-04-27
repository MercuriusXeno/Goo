package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.ability.AbilityMath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for EffectMath.isFuseLive: fuse state checks.
 */
class ChainMarkerEffectTest {

    /**
     * Fuse at 1 tick remaining is still live.
     */
    @Test
    void fuseAtOneIsLive() {
        assertTrue(AbilityMath.isFuseLive(1));
    }

    /**
     * Fuse at 0 ticks is expired.
     */
    @Test
    void fuseAtZeroIsExpired() {
        assertFalse(AbilityMath.isFuseLive(0));
    }

    /**
     * Fuse at negative ticks is expired.
     */
    @Test
    void fuseNegativeIsExpired() {
        assertFalse(AbilityMath.isFuseLive(-1));
    }

    /**
     * Fuse at full duration (10 ticks) is live.
     */
    @Test
    void fuseAtFullDurationIsLive() {
        assertTrue(AbilityMath.isFuseLive(10));
    }
}
