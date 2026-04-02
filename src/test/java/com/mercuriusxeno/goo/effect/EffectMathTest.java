package com.mercuriusxeno.goo.effect;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EffectMath pure functions: range formulas for chain effects.
 */
class EffectMathTest {

    // ── Blaze: explosion radius = 1 + 2n ──────────────────────────────────

    @Nested
    class ExplosionRadius {

        @Test
        void stack1Is3() {
            assertEquals(3.0f, EffectMath.computeExplosionRadius(1));
        }

        @Test
        void stack2Is5() {
            assertEquals(5.0f, EffectMath.computeExplosionRadius(2));
        }

        @Test
        void stack3Is7() {
            assertEquals(7.0f, EffectMath.computeExplosionRadius(3));
        }

        @Test
        void stack4Is9() {
            assertEquals(9.0f, EffectMath.computeExplosionRadius(4));
        }
    }

    // ── Frost: freeze radius = 3 + 2n ─────────────────────────────────────

    @Nested
    class FreezeRadius {

        @Test
        void stack1Is5() {
            assertEquals(5, EffectMath.computeFreezeRadius(1));
        }

        @Test
        void stack2Is7() {
            assertEquals(7, EffectMath.computeFreezeRadius(2));
        }

        @Test
        void stack3Is9() {
            assertEquals(9, EffectMath.computeFreezeRadius(3));
        }
    }

    // ── Nether: conversion radius = 1 + 2n ────────────────────────────────

    @Nested
    class NetherRadius {

        @Test
        void stack1Is3() {
            assertEquals(3, EffectMath.computeNetherRadius(1));
        }

        @Test
        void stack2Is5() {
            assertEquals(5, EffectMath.computeNetherRadius(2));
        }

        @Test
        void stack3Is7() {
            assertEquals(7, EffectMath.computeNetherRadius(3));
        }

        @Test
        void stack4Is9() {
            assertEquals(9, EffectMath.computeNetherRadius(4));
        }
    }

    // ── Rock: implosion depth = n² ─────────────────────────────────────────

    @Nested
    class ImplosionDepth {

        @Test
        void stack1Is1() {
            assertEquals(1, EffectMath.computeImplosionDepth(1));
        }

        @Test
        void stack2Is4() {
            assertEquals(4, EffectMath.computeImplosionDepth(2));
        }

        @Test
        void stack3Is9() {
            assertEquals(9, EffectMath.computeImplosionDepth(3));
        }

        @Test
        void stack4Is16() {
            assertEquals(16, EffectMath.computeImplosionDepth(4));
        }

        @Test
        void stack5Is25() {
            assertEquals(25, EffectMath.computeImplosionDepth(5));
        }
    }
}
