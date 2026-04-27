package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GooContents: multi-type add/remove/merge,
 * capped add, empty detection, totalVolume, typeCount, largestType.
 */
class GooContentsTest {

    // -- empty / basic --

    @Test
    void emptyContentsIsEmpty() {
        assertTrue(GooContents.EMPTY.isEmpty());
        assertEquals(0, GooContents.EMPTY.totalVolume());
        assertEquals(0, GooContents.EMPTY.typeCount());
    }

    @Test
    void singleTypeNotEmpty() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100));
        assertFalse(gc.isEmpty());
        assertEquals(100, gc.totalVolume());
        assertEquals(1, gc.typeCount());
    }

    @Test
    void multiTypeVolumeSumsAll() {
        GooContents gc = new GooContents(Map.of(
                GooType.ROCK, 100, GooType.METAL, 200, GooType.VITAL, 50));
        assertEquals(350, gc.totalVolume());
        assertEquals(3, gc.typeCount());
    }

    // -- isSingleType / getSingleType --

    @Test
    void isSingleType_true() {
        GooContents gc = new GooContents(Map.of(GooType.BLAZE, 500));
        assertTrue(gc.isSingleType());
        assertEquals(GooType.BLAZE, gc.getSingleType());
    }

    @Test
    void isSingleType_falseForMulti() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100, GooType.FROST, 200));
        assertFalse(gc.isSingleType());
    }

    @Test
    void isSingleType_falseForEmpty() {
        assertFalse(GooContents.EMPTY.isSingleType());
    }

    // -- getVolume --

    @Test
    void getVolume_presentType() {
        GooContents gc = new GooContents(Map.of(GooType.HEX, 777));
        assertEquals(777, gc.getVolume(GooType.HEX));
    }

    @Test
    void getVolume_absentType() {
        GooContents gc = new GooContents(Map.of(GooType.HEX, 777));
        assertEquals(0, gc.getVolume(GooType.ROCK));
    }

    // -- largestType --

    @Test
    void largestType_empty() {
        assertNull(GooContents.EMPTY.largestType());
    }

    @Test
    void largestType_multiType() {
        GooContents gc = new GooContents(Map.of(
                GooType.ROCK, 100, GooType.METAL, 500, GooType.VITAL, 200));
        assertEquals(GooType.METAL, gc.largestType());
    }

    // -- withAdded --

    @Test
    void withAdded_newType() {
        GooContents gc = GooContents.EMPTY.withAdded(GooType.LEAF, 100);
        assertEquals(100, gc.getVolume(GooType.LEAF));
    }

    @Test
    void withAdded_existingType() {
        GooContents gc = new GooContents(Map.of(GooType.LEAF, 100));
        GooContents result = gc.withAdded(GooType.LEAF, 50);
        assertEquals(150, result.getVolume(GooType.LEAF));
    }

    // -- withRemoved --

    @Test
    void withRemoved_partial() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 500));
        GooContents result = gc.withRemoved(GooType.ROCK, 200);
        assertEquals(300, result.getVolume(GooType.ROCK));
    }

    @Test
    void withRemoved_full() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 500));
        GooContents result = gc.withRemoved(GooType.ROCK, 500);
        assertTrue(result.isEmpty());
    }

    @Test
    void withRemoved_clampsToZero() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100));
        GooContents result = gc.withRemoved(GooType.ROCK, 999);
        assertEquals(0, result.getVolume(GooType.ROCK));
        assertTrue(result.isEmpty());
    }

    // -- mergeWith --

    @Test
    void mergeWith_combinesTypes() {
        GooContents a = new GooContents(Map.of(GooType.ROCK, 100));
        GooContents b = new GooContents(Map.of(GooType.METAL, 200));
        GooContents result = a.mergeWith(b);
        assertEquals(100, result.getVolume(GooType.ROCK));
        assertEquals(200, result.getVolume(GooType.METAL));
    }

    @Test
    void mergeWith_sumsOverlapping() {
        GooContents a = new GooContents(Map.of(GooType.ROCK, 100));
        GooContents b = new GooContents(Map.of(GooType.ROCK, 200));
        GooContents result = a.mergeWith(b);
        assertEquals(300, result.getVolume(GooType.ROCK));
    }

    // -- withCappedAdd --

    @Test
    void withCappedAdd_underCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100));
        GooContents result = gc.withCappedAdd(GooType.METAL, 200, 1000);
        assertEquals(100, result.getVolume(GooType.ROCK));
        assertEquals(200, result.getVolume(GooType.METAL));
    }

    @Test
    void withCappedAdd_atCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 900));
        GooContents result = gc.withCappedAdd(GooType.METAL, 500, 1000);
        assertEquals(900, result.getVolume(GooType.ROCK));
        assertEquals(100, result.getVolume(GooType.METAL));
    }

    @Test
    void withCappedAdd_alreadyFull() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 1000));
        GooContents result = gc.withCappedAdd(GooType.METAL, 500, 1000);
        assertEquals(1000, result.getVolume(GooType.ROCK));
        assertEquals(0, result.getVolume(GooType.METAL));
    }

    // -- cappedAddAmount --

    @Test
    void cappedAddAmount_underCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100));
        assertEquals(200, gc.cappedAddAmount(200, 1000));
    }

    @Test
    void cappedAddAmount_overCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 900));
        assertEquals(100, gc.cappedAddAmount(500, 1000));
    }

    @Test
    void cappedAddAmount_alreadyFull() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 1000));
        assertEquals(0, gc.cappedAddAmount(500, 1000));
    }

    // -- zero/negative filtering --

    @Test
    void constructorFiltersZeroValues() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 0));
        assertTrue(gc.isEmpty());
    }
}
