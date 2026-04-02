package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for crucible pure logic: exponent-based extraction rate,
 * proportional drain shares, GooValue-to-GooContents bridge,
 * GooContents operations, and platform movement math.
 */
@ExtendWith(MockitoExtension.class)
class CrucibleBlockEntityTest {

    // -- extractionRate (exponent-based) ------------------------------------

    /** Zero remaining always yields minimum rate of 1 mB/t. */
    @Test
    void zeroRemainingYieldsMinimumRate() {
        assertEquals(1L, CrucibleMath.extractionRate(0, 0));
    }

    /** 1 mB remaining with no matrices: floor(1^0.25) = 1. */
    @Test
    void oneRemainingNoMatricesYieldsOne() {
        assertEquals(1L, CrucibleMath.extractionRate(1, 0));
    }

    /** 100 mB remaining, no matrices: floor(100^0.25) = floor(3.16) = 3. */
    @Test
    void hundredRemainingNoMatricesYieldsThree() {
        assertEquals(3L, CrucibleMath.extractionRate(100, 0));
    }

    /** 1000 mB remaining, no matrices: floor(1000^0.25) = floor(5.62) = 5. */
    @Test
    void thousandRemainingNoMatricesYieldsFive() {
        assertEquals(5L, CrucibleMath.extractionRate(1000, 0));
    }

    /** 10,000 mB remaining, no matrices: floor(10000^0.25) = 10. */
    @Test
    void tenThousandRemainingNoMatricesYieldsTen() {
        assertEquals(10L, CrucibleMath.extractionRate(10_000, 0));
    }

    /** 1,000,000 mB remaining, no matrices: floor(1e6^0.25) = floor(31.6) = 31. */
    @Test
    void millionRemainingNoMatricesYieldsThirtyOne() {
        assertEquals(31L, CrucibleMath.extractionRate(1_000_000, 0));
    }

    /** 100 mB remaining, 5 matrices (exponent 0.50): floor(100^0.5) = 10. */
    @Test
    void hundredRemainingFiveMatricesYieldsTen() {
        assertEquals(10L, CrucibleMath.extractionRate(100, 5));
    }

    /** 1000 mB remaining, 5 matrices: floor(1000^0.5) = floor(31.6) = 31. */
    @Test
    void thousandRemainingFiveMatricesYieldsThirtyOne() {
        assertEquals(31L, CrucibleMath.extractionRate(1000, 5));
    }

    /** 1,000,000 mB remaining, 5 matrices: floor(1e6^0.5) = 1000. */
    @Test
    void millionRemainingFiveMatricesYieldsThousand() {
        assertEquals(1000L, CrucibleMath.extractionRate(1_000_000, 5));
    }

    /** Negative remaining clamps to minimum rate of 1. */
    @Test
    void negativeRemainingYieldsMinimumRate() {
        assertEquals(1L, CrucibleMath.extractionRate(-100, 0));
    }

    /** Negative matrix count clamps to zero matrices. */
    @Test
    void negativeMatricesClampToZero() {
        assertEquals(3L, CrucibleMath.extractionRate(100, -1));
    }

    /** Matrix count above 5 clamps to 5. */
    @Test
    void excessMatricesClampToFive() {
        assertEquals(10L, CrucibleMath.extractionRate(100, 10));
    }

    // -- computeExponent ----------------------------------------------------

    /** Base exponent with no matrices. */
    @Test
    void baseExponentNoMatrices() {
        assertEquals(0.25, CrucibleMath.computeExponent(0), 0.001);
    }

    /** Exponent with 5 matrices. */
    @Test
    void exponentWithFiveMatrices() {
        assertEquals(0.50, CrucibleMath.computeExponent(5), 0.001);
    }

    /** Exponent with 3 matrices. */
    @Test
    void exponentWithThreeMatrices() {
        assertEquals(0.40, CrucibleMath.computeExponent(3), 0.001);
    }

    // -- GooValue.toGooContents ------------------------------------------

    /** Empty GooValue produces EMPTY GooContents. */
    @Test
    void emptyGooValueProducesEmptyGooContents() {
        GooContents result = GooValue.EMPTY.toGooContents();
        assertTrue(result.isEmpty());
    }

    /** Single-type GooValue widens int to long correctly. */
    @Test
    void singleTypeGooValueConvertsToGooContents() {
        GooValue value = new GooValue(Map.of(GooType.ROCK, 500));
        GooContents result = value.toGooContents();
        assertEquals(500L, result.getVolume(GooType.ROCK));
        assertEquals(1, result.typeCount());
    }

    /** Multi-type GooValue preserves all types with widened amounts. */
    @Test
    void multiTypeGooValuePreservesAllTypes() {
        GooValue value = new GooValue(Map.of(
            GooType.ROCK, 100,
            GooType.METAL, 250,
            GooType.VITAL, 50
        ));
        GooContents result = value.toGooContents();
        assertEquals(100L, result.getVolume(GooType.ROCK));
        assertEquals(250L, result.getVolume(GooType.METAL));
        assertEquals(50L, result.getVolume(GooType.VITAL));
        assertEquals(3, result.typeCount());
    }

    // -- GooContents.largestType -----------------------------------------

    /** Empty GooContents has no largest type. */
    @Test
    void emptyGooContentsHasNoLargestType() {
        assertNull(GooContents.EMPTY.largestType());
    }

    /** Single-type GooContents returns that type as largest. */
    @Test
    void singleTypeGooContentsReturnsThatType() {
        GooContents gc = new GooContents(Map.of(GooType.BLAZE, 100L));
        assertEquals(GooType.BLAZE, gc.largestType());
    }

    /** Multi-type GooContents returns the highest-volume type. */
    @Test
    void multiTypeGooContentsReturnsHighestVolume() {
        GooContents gc = new GooContents(Map.of(
            GooType.ROCK, 100L,
            GooType.METAL, 500L,
            GooType.VITAL, 200L
        ));
        assertEquals(GooType.METAL, gc.largestType());
    }

    // -- GooContents.mergeWith -------------------------------------------

    /** Merging empty with non-empty returns the non-empty. */
    @Test
    void mergeEmptyWithNonEmptyReturnsNonEmpty() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100L));
        GooContents result = GooContents.EMPTY.mergeWith(gc);
        assertEquals(100L, result.getVolume(GooType.ROCK));
    }

    /** Merging two contents sums matching types. */
    @Test
    void mergeWithSumsMatchingTypes() {
        GooContents a = new GooContents(Map.of(GooType.ROCK, 100L, GooType.METAL, 50L));
        GooContents b = new GooContents(Map.of(GooType.ROCK, 200L, GooType.VITAL, 75L));
        GooContents result = a.mergeWith(b);
        assertEquals(300L, result.getVolume(GooType.ROCK));
        assertEquals(50L, result.getVolume(GooType.METAL));
        assertEquals(75L, result.getVolume(GooType.VITAL));
    }

    // -- computeDrainShares (proportional distribution) -------------------------

    /** Single type gets the entire rate budget. */
    @Test
    void singleTypeDrainShareGetsFullRate() {
        GooContents pool = new GooContents(Map.of(GooType.ROCK, 1000L));
        Map<GooType, Long> shares = CrucibleMath.computeDrainShares(pool, 10);
        assertEquals(10L, shares.get(GooType.ROCK));
    }

    /** Two equal types split the rate evenly. */
    @Test
    void equalTypesSplitEvenly() {
        GooContents pool = new GooContents(Map.of(
            GooType.ROCK, 500L, GooType.METAL, 500L));
        Map<GooType, Long> shares = CrucibleMath.computeDrainShares(pool, 10);
        assertEquals(5L, shares.get(GooType.ROCK));
        assertEquals(5L, shares.get(GooType.METAL));
    }

    /** Unequal types split proportionally with remainder to largest. */
    @Test
    void unequalTypesSplitProportionally() {
        GooContents pool = new GooContents(Map.of(
            GooType.ROCK, 750L, GooType.METAL, 250L));
        Map<GooType, Long> shares = CrucibleMath.computeDrainShares(pool, 10);
        // ROCK: floor(10 * 750/1000) = 7, METAL: floor(10 * 250/1000) = 2
        // remainder 1 goes to ROCK (largest) -> 8
        assertEquals(8L, shares.get(GooType.ROCK));
        assertEquals(2L, shares.get(GooType.METAL));
    }

    /** Tiny type gets minimum 1 mB per tick. */
    @Test
    void tinyTypeGetsMinimumOneMb() {
        GooContents pool = new GooContents(Map.of(
            GooType.ROCK, 9999L, GooType.METAL, 1L));
        Map<GooType, Long> shares = CrucibleMath.computeDrainShares(pool, 5);
        // METAL: floor(5 * 1/10000) = 0, clamped to min(1, available=1) = 1
        assertEquals(1L, shares.get(GooType.METAL));
    }

    /** Share never exceeds available volume for a type. */
    @Test
    void shareNeverExceedsAvailable() {
        GooContents pool = new GooContents(Map.of(
            GooType.ROCK, 3L, GooType.METAL, 3L));
        Map<GooType, Long> shares = CrucibleMath.computeDrainShares(pool, 100);
        assertEquals(3L, shares.get(GooType.ROCK));
        assertEquals(3L, shares.get(GooType.METAL));
    }

    /** Total shares across all types sum to at most the rate budget. */
    @Test
    void totalSharesDoNotExceedRate() {
        GooContents pool = new GooContents(Map.of(
            GooType.ROCK, 600L, GooType.METAL, 300L, GooType.VITAL, 100L));
        Map<GooType, Long> shares = CrucibleMath.computeDrainShares(pool, 10);
        long total = shares.values().stream().mapToLong(Long::longValue).sum();
        assertTrue(total <= 10, "Total shares " + total + " should not exceed rate 10");
    }

}
