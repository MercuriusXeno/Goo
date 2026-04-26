package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.ability.AbilityMath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EffectMath.canStack: stacking cap enforcement.
 */
class GooWorldEffectTest {

    @Nested
    class CanStack {

        /** First stack succeeds when maxStacks > 1. */
        @Test
        void firstStackSucceeds() {
            assertTrue(AbilityMath.canStack(1, 4));
        }

        /** Stack at max is rejected. */
        @Test
        void atMaxIsRejected() {
            assertFalse(AbilityMath.canStack(4, 4));
        }

        /** Stack above max is rejected. */
        @Test
        void aboveMaxIsRejected() {
            assertFalse(AbilityMath.canStack(5, 4));
        }

        /** Stack at one below max succeeds. */
        @Test
        void oneBelowMaxSucceeds() {
            assertTrue(AbilityMath.canStack(3, 4));
        }

        /** MaxStacks of 1 means no stacking at all (already at 1). */
        @Test
        void maxOneRejectsStack() {
            assertFalse(AbilityMath.canStack(1, 1));
        }

        /** Zero stacks can always stack if maxStacks >= 1. */
        @Test
        void zeroStacksCanStack() {
            assertTrue(AbilityMath.canStack(0, 1));
        }
    }
}
