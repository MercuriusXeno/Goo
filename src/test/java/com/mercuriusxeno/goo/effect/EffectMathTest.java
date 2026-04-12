package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EffectMath pure functions: range formulas for chain effects.
 */
class EffectMathTest {

    // ── Frost: freeze radius = 2 + n ──────────────────────────────────────

    @Nested
    class FreezeRadius {

        @Test
        void stack1Is3() {
            assertEquals(3, EffectMath.computeFreezeRadius(1));
        }

        @Test
        void stack2Is4() {
            assertEquals(4, EffectMath.computeFreezeRadius(2));
        }

        @Test
        void stack3Is5() {
            assertEquals(5, EffectMath.computeFreezeRadius(3));
        }

        @Test
        void stack4Is6() {
            assertEquals(6, EffectMath.computeFreezeRadius(4));
        }
    }

    // ── Frost: duration = 4 * radius * 20 ticks ────────────────────────────

    @Nested
    class FrostDuration {

        @Test
        void radius3Is240() {
            assertEquals(240, EffectMath.computeFrostDuration(3));
        }

        @Test
        void radius4Is320() {
            assertEquals(320, EffectMath.computeFrostDuration(4));
        }

        @Test
        void radius5Is400() {
            assertEquals(400, EffectMath.computeFrostDuration(5));
        }

        @Test
        void radius6Is480() {
            assertEquals(480, EffectMath.computeFrostDuration(6));
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

    // ── Rock: isRockCompatible (majority rule) ────────────────────────────

    @Nested
    class RockCompatible {

        @Test
        void rockOnlyIsCompatible() {
            GooValue value = new GooValue(Map.of(GooType.ROCK, 3));
            assertTrue(EffectMath.isRockCompatible(value));
        }

        @Test
        void rockAndCrystalIsCompatible() {
            GooValue value = new GooValue(Map.of(GooType.ROCK, 2, GooType.CRYSTAL, 1));
            assertTrue(EffectMath.isRockCompatible(value));
        }

        @Test
        void crystalOnlyIsCompatible() {
            GooValue value = new GooValue(Map.of(GooType.CRYSTAL, 2));
            assertTrue(EffectMath.isRockCompatible(value));
        }

        @Test
        void rockMajorityWithMinorityMetalIsCompatible() {
            GooValue value = new GooValue(Map.of(GooType.ROCK, 3, GooType.METAL, 1));
            assertTrue(EffectMath.isRockCompatible(value));
        }

        @Test
        void rockMinorityIsNotCompatible() {
            GooValue value = new GooValue(Map.of(GooType.ROCK, 1, GooType.METAL, 3));
            assertFalse(EffectMath.isRockCompatible(value));
        }

        @Test
        void exactHalfIsNotCompatible() {
            GooValue value = new GooValue(Map.of(GooType.ROCK, 2, GooType.METAL, 2));
            assertFalse(EffectMath.isRockCompatible(value));
        }

        @Test
        void emptyIsNotCompatible() {
            assertFalse(EffectMath.isRockCompatible(GooValue.EMPTY));
        }

        @Test
        void nullIsNotCompatible() {
            assertFalse(EffectMath.isRockCompatible(null));
        }
    }

}
