package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GooValue: construction, arithmetic, and query methods.
 */
class GooValueTest {

    // ── Construction ────────────────────────────────────────────────────

    /** Zero amounts are filtered out, but negatives are preserved. */
    @Test
    void constructorFiltersZeroButKeepsNegative() {
        Map<GooType, Integer> map = new EnumMap<>(GooType.class);
        map.put(GooType.METAL, 0);
        map.put(GooType.CRYSTAL, -5);
        map.put(GooType.LEAF, 10);

        GooValue val = new GooValue(map);
        assertEquals(0, val.get(GooType.METAL));
        assertEquals(-5, val.get(GooType.CRYSTAL));
        assertEquals(10, val.get(GooType.LEAF));
    }

    /** Empty map produces an empty GooValue. */
    @Test
    void emptyMapProducesEmptyValue() {
        GooValue val = new GooValue(Map.of());
        assertTrue(val.isEmpty());
        assertEquals(0, val.totalBlobs());
    }

    /** EMPTY singleton is truly empty. */
    @Test
    void emptySingletonIsEmpty() {
        assertTrue(GooValue.EMPTY.isEmpty());
        assertEquals(0, GooValue.EMPTY.totalBlobs());
        assertNull(GooValue.EMPTY.largestType());
    }

    // ── totalBlobs ──────────────────────────────────────────────────────

    /** Total blobs sums all types. */
    @Test
    void totalBlobsSumsAllTypes() {
        GooValue val = goo(GooType.METAL, 5, GooType.CRYSTAL, 3);
        assertEquals(8, val.totalBlobs());
    }

    /** Single type total blobs equals that type's amount. */
    @Test
    void singleTypeTotalBlobs() {
        GooValue val = goo(GooType.VITAL, 42);
        assertEquals(42, val.totalBlobs());
    }

    // ── largestType ──────────────────────────────────────────────────

    /** Largest type is the one with the highest amount. */
    @Test
    void largestTypeIsHighestAmount() {
        GooValue val = goo(GooType.BLAZE, 10, GooType.LEAF, 5);
        assertEquals(GooType.BLAZE, val.largestType());
    }

    /** Single-type value returns that type as largest. */
    @Test
    void singleTypeLargest() {
        GooValue val = goo(GooType.ENDER, 1);
        assertEquals(GooType.ENDER, val.largestType());
    }

    // ── add ─────────────────────────────────────────────────────────────

    /** Adding two values sums their types. */
    @Test
    void addSumsTypes() {
        GooValue a = goo(GooType.METAL, 5);
        GooValue b = goo(GooType.METAL, 3);
        GooValue result = a.add(b, 1);
        assertEquals(8, result.get(GooType.METAL));
    }

    /** Adding with multiplier scales the added value. */
    @Test
    void addWithMultiplier() {
        GooValue a = goo(GooType.ROCK, 2);
        GooValue b = goo(GooType.ROCK, 3);
        GooValue result = a.add(b, 4);
        assertEquals(14, result.get(GooType.ROCK)); // 2 + 3*4
    }

    /** Adding introduces new types. */
    @Test
    void addIntroducesNewType() {
        GooValue a = goo(GooType.METAL, 5);
        GooValue b = goo(GooType.CRYSTAL, 3);
        GooValue result = a.add(b, 1);
        assertEquals(5, result.get(GooType.METAL));
        assertEquals(3, result.get(GooType.CRYSTAL));
    }

    /** Adding to EMPTY gives the added value. */
    @Test
    void addToEmpty() {
        GooValue b = goo(GooType.GLOW, 7);
        GooValue result = GooValue.EMPTY.add(b, 1);
        assertEquals(7, result.get(GooType.GLOW));
    }

    /** Adding a value with negatives subtracts those types (exposed copper use case). */
    @Test
    void addWithNegativeSubtractsType() {
        GooValue copper = goo(GooType.METAL, 100, GooType.ROCK, 20);
        Map<GooType, Integer> exposedMap = new EnumMap<>(GooType.class);
        exposedMap.put(GooType.AEON, 32);
        exposedMap.put(GooType.METAL, -64);
        GooValue exposed = new GooValue(exposedMap);

        GooValue result = copper.add(exposed, 1);
        assertEquals(36, result.get(GooType.METAL));  // 100 - 64
        assertEquals(32, result.get(GooType.AEON));    // 0 + 32
        assertEquals(20, result.get(GooType.ROCK));    // unchanged
    }

    /** Adding negatives that exceed the positive amount produces a negative result. */
    @Test
    void addWithNegativeCanGoNegative() {
        GooValue small = goo(GooType.METAL, 10);
        Map<GooType, Integer> bigDrain = new EnumMap<>(GooType.class);
        bigDrain.put(GooType.METAL, -50);
        GooValue drain = new GooValue(bigDrain);

        GooValue result = small.add(drain, 1);
        assertEquals(-40, result.get(GooType.METAL));
    }

    /** floorZero clamps all negative types to zero. */
    @Test
    void floorZeroClampsNegatives() {
        Map<GooType, Integer> map = new EnumMap<>(GooType.class);
        map.put(GooType.METAL, -40);
        map.put(GooType.AEON, 32);
        map.put(GooType.ROCK, 0);
        GooValue val = new GooValue(map);

        GooValue floored = val.floorZero();
        assertEquals(0, floored.get(GooType.METAL));
        assertEquals(32, floored.get(GooType.AEON));
        assertTrue(floored.getAll().containsKey(GooType.AEON));
        assertFalse(floored.getAll().containsKey(GooType.METAL));
    }

    // ── subtract ────────────────────────────────────────────────────────

    /** Subtracting per-type produces the difference. */
    @Test
    void subtractPerType() {
        GooValue a = goo(GooType.METAL, 10, GooType.CRYSTAL, 8);
        GooValue b = goo(GooType.METAL, 3, GooType.CRYSTAL, 2);
        GooValue result = a.subtract(b);
        assertEquals(7, result.get(GooType.METAL));
        assertEquals(6, result.get(GooType.CRYSTAL));
    }

    /** Subtracting more than available goes negative. */
    @Test
    void subtractCanGoNegative() {
        GooValue a = goo(GooType.METAL, 5);
        GooValue b = goo(GooType.METAL, 10);
        GooValue result = a.subtract(b);
        assertEquals(-5, result.get(GooType.METAL));
    }

    /** Subtracting from EMPTY produces negative values. */
    @Test
    void subtractFromEmptyGoesNegative() {
        GooValue b = goo(GooType.LEAF, 5);
        GooValue result = GooValue.EMPTY.subtract(b);
        assertEquals(-5, result.get(GooType.LEAF));
    }

    /** Subtracting a type not present in the source introduces a negative. */
    @Test
    void subtractMissingTypeGoesNegative() {
        GooValue a = goo(GooType.METAL, 10);
        GooValue b = goo(GooType.CRYSTAL, 5);
        GooValue result = a.subtract(b);
        assertEquals(10, result.get(GooType.METAL));
        assertEquals(-5, result.get(GooType.CRYSTAL));
    }

    // ── divide ──────────────────────────────────────────────────────────

    /** Dividing evenly produces exact result. */
    @Test
    void divideEvenly() {
        GooValue val = goo(GooType.METAL, 10);
        GooValue result = val.divide(2);
        assertEquals(5, result.get(GooType.METAL));
    }

    /** Dividing with remainder floors the result. */
    @Test
    void divideWithRemainder() {
        GooValue val = goo(GooType.METAL, 7);
        GooValue result = val.divide(3);
        assertEquals(2, result.get(GooType.METAL)); // 7/3 = 2
    }

    /** Dividing by 1 returns the same value. */
    @Test
    void divideByOneReturnsSame() {
        GooValue val = goo(GooType.BLAZE, 15);
        GooValue result = val.divide(1);
        assertSame(val, result);
    }

    /** Dividing can zero out a type (excluded from result). */
    @Test
    void divideCanZeroOutType() {
        GooValue val = goo(GooType.METAL, 1);
        GooValue result = val.divide(2);
        assertTrue(result.isEmpty()); // 1/2 = 0 → empty
    }

    /** Multi-type divide divides each type independently. */
    @Test
    void multiTypeDivide() {
        GooValue val = goo(GooType.METAL, 10, GooType.CRYSTAL, 6);
        GooValue result = val.divide(3);
        assertEquals(3, result.get(GooType.METAL)); // 10/3
        assertEquals(2, result.get(GooType.CRYSTAL)); // 6/3
    }

    // ── scale ──────────────────────────────────────────────────────────

    /** Scaling by 0.5 halves all types (rounded). */
    @Test
    void scaleByHalf() {
        GooValue val = goo(GooType.BLAZE, 100, GooType.METAL, 50);
        GooValue result = val.scale(0.5);
        assertEquals(50, result.get(GooType.BLAZE));
        assertEquals(25, result.get(GooType.METAL));
    }

    /** Scaling rounds to nearest integer. */
    @Test
    void scaleRoundsToNearest() {
        GooValue val = goo(GooType.BLAZE, 7);
        GooValue result = val.scale(0.5);
        assertEquals(4, result.get(GooType.BLAZE)); // round(3.5) = 4
    }

    /** Scaling by zero returns EMPTY. */
    @Test
    void scaleByZeroReturnsEmpty() {
        GooValue val = goo(GooType.VITAL, 100);
        GooValue result = val.scale(0.0);
        assertTrue(result.isEmpty());
    }

    /** Scaling by 1.0 returns same instance. */
    @Test
    void scaleByOneReturnsSame() {
        GooValue val = goo(GooType.LEAF, 42);
        GooValue result = val.scale(1.0);
        assertSame(val, result);
    }

    /** Scaling can zero out small types (excluded from result). */
    @Test
    void scaleCanZeroOutSmallType() {
        GooValue val = goo(GooType.METAL, 1);
        GooValue result = val.scale(0.3);
        assertTrue(result.isEmpty()); // round(0.3) = 0
    }

    // ── hasNegative ──────────────────────────────────────────────────────

    /** A value with all positive types has no negatives. */
    @Test
    void allPositiveHasNoNegative() {
        GooValue val = goo(GooType.METAL, 10, GooType.CRYSTAL, 5);
        assertFalse(val.hasNegative());
    }

    /** A value with a negative type reports hasNegative. */
    @Test
    void negativeTypeDetected() {
        Map<GooType, Integer> map = new EnumMap<>(GooType.class);
        map.put(GooType.AEON, 32);
        map.put(GooType.METAL, -64);
        GooValue val = new GooValue(map);
        assertTrue(val.hasNegative());
    }

    /** EMPTY has no negatives. */
    @Test
    void emptyHasNoNegative() {
        assertFalse(GooValue.EMPTY.hasNegative());
    }

    // ── isEmpty ─────────────────────────────────────────────────────────

    /** A value with at least one positive type is not empty. */
    @Test
    void nonEmptyValue() {
        GooValue val = goo(GooType.HEX, 1);
        assertFalse(val.isEmpty());
    }

    // ── toString ────────────────────────────────────────────────────────

    /** Empty value toString returns "none". */
    @Test
    void toStringEmpty() {
        assertEquals("none", GooValue.EMPTY.toString());
    }

    /** Non-empty toString contains the type id and amount. */
    @Test
    void toStringContainsTypeAndAmount() {
        GooValue val = goo(GooType.VITAL, 5);
        String s = val.toString();
        assertTrue(s.contains("vital"));
        assertTrue(s.contains("5"));
    }
}
