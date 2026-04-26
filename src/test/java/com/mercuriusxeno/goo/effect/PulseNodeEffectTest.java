package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.ability.AbilityMath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EffectMath.computePulseInterval: interval halving with stack count.
 * Formula: 320 / 2^(min(stackCount, 4) - 1) ticks.
 */
class PulseNodeEffectTest {

    @Nested
    class ComputePulseInterval {

        /** Stack 1 → 320 ticks (16 seconds). */
        @Test
        void stack1Is320Ticks() {
            assertEquals(320, AbilityMath.computePulseInterval(1));
        }

        /** Stack 2 → 160 ticks (8 seconds). */
        @Test
        void stack2Is160Ticks() {
            assertEquals(160, AbilityMath.computePulseInterval(2));
        }

        /** Stack 3 → 80 ticks (4 seconds). */
        @Test
        void stack3Is80Ticks() {
            assertEquals(80, AbilityMath.computePulseInterval(3));
        }

        /** Stack 4 → 40 ticks (2 seconds, minimum). */
        @Test
        void stack4Is40Ticks() {
            assertEquals(40, AbilityMath.computePulseInterval(4));
        }

        /** Stack 5+ is clamped to 4 → still 40 ticks. */
        @Test
        void stack5ClampedTo40Ticks() {
            assertEquals(40, AbilityMath.computePulseInterval(5));
        }

        /** Stack 10 is clamped to 4 → still 40 ticks. */
        @Test
        void stack10ClampedTo40Ticks() {
            assertEquals(40, AbilityMath.computePulseInterval(10));
        }
    }
}
