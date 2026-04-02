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
        assertEquals(0L, GooContents.EMPTY.totalVolume());
        assertEquals(0, GooContents.EMPTY.typeCount());
    }

    @Test
    void singleTypeNotEmpty() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100L));
        assertFalse(gc.isEmpty());
        assertEquals(100L, gc.totalVolume());
        assertEquals(1, gc.typeCount());
    }

    @Test
    void multiTypeVolumeSumsAll() {
        GooContents gc = new GooContents(Map.of(
            GooType.ROCK, 100L, GooType.METAL, 200L, GooType.VITAL, 50L));
        assertEquals(350L, gc.totalVolume());
        assertEquals(3, gc.typeCount());
    }

    // -- isSingleType / getSingleType --

    @Test
    void isSingleType_true() {
        GooContents gc = new GooContents(Map.of(GooType.BLAZE, 500L));
        assertTrue(gc.isSingleType());
        assertEquals(GooType.BLAZE, gc.getSingleType());
    }

    @Test
    void isSingleType_falseForMulti() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100L, GooType.FROST, 200L));
        assertFalse(gc.isSingleType());
    }

    @Test
    void isSingleType_falseForEmpty() {
        assertFalse(GooContents.EMPTY.isSingleType());
    }

    // -- getVolume --

    @Test
    void getVolume_presentType() {
        GooContents gc = new GooContents(Map.of(GooType.HEX, 777L));
        assertEquals(777L, gc.getVolume(GooType.HEX));
    }

    @Test
    void getVolume_absentType() {
        GooContents gc = new GooContents(Map.of(GooType.HEX, 777L));
        assertEquals(0L, gc.getVolume(GooType.ROCK));
    }

    // -- largestType --

    @Test
    void largestType_empty() {
        assertNull(GooContents.EMPTY.largestType());
    }

    @Test
    void largestType_multiType() {
        GooContents gc = new GooContents(Map.of(
            GooType.ROCK, 100L, GooType.METAL, 500L, GooType.VITAL, 200L));
        assertEquals(GooType.METAL, gc.largestType());
    }

    // -- withAdded --

    @Test
    void withAdded_newType() {
        GooContents gc = GooContents.EMPTY.withAdded(GooType.LEAF, 100L);
        assertEquals(100L, gc.getVolume(GooType.LEAF));
    }

    @Test
    void withAdded_existingType() {
        GooContents gc = new GooContents(Map.of(GooType.LEAF, 100L));
        GooContents result = gc.withAdded(GooType.LEAF, 50L);
        assertEquals(150L, result.getVolume(GooType.LEAF));
    }

    // -- withRemoved --

    @Test
    void withRemoved_partial() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 500L));
        GooContents result = gc.withRemoved(GooType.ROCK, 200L);
        assertEquals(300L, result.getVolume(GooType.ROCK));
    }

    @Test
    void withRemoved_full() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 500L));
        GooContents result = gc.withRemoved(GooType.ROCK, 500L);
        assertTrue(result.isEmpty());
    }

    @Test
    void withRemoved_clampsToZero() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100L));
        GooContents result = gc.withRemoved(GooType.ROCK, 999L);
        assertEquals(0L, result.getVolume(GooType.ROCK));
        assertTrue(result.isEmpty());
    }

    // -- mergeWith --

    @Test
    void mergeWith_combinesTypes() {
        GooContents a = new GooContents(Map.of(GooType.ROCK, 100L));
        GooContents b = new GooContents(Map.of(GooType.METAL, 200L));
        GooContents result = a.mergeWith(b);
        assertEquals(100L, result.getVolume(GooType.ROCK));
        assertEquals(200L, result.getVolume(GooType.METAL));
    }

    @Test
    void mergeWith_sumsOverlapping() {
        GooContents a = new GooContents(Map.of(GooType.ROCK, 100L));
        GooContents b = new GooContents(Map.of(GooType.ROCK, 200L));
        GooContents result = a.mergeWith(b);
        assertEquals(300L, result.getVolume(GooType.ROCK));
    }

    // -- withCappedAdd --

    @Test
    void withCappedAdd_underCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100L));
        GooContents result = gc.withCappedAdd(GooType.METAL, 200L, 1000L);
        assertEquals(100L, result.getVolume(GooType.ROCK));
        assertEquals(200L, result.getVolume(GooType.METAL));
    }

    @Test
    void withCappedAdd_atCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 900L));
        GooContents result = gc.withCappedAdd(GooType.METAL, 500L, 1000L);
        assertEquals(900L, result.getVolume(GooType.ROCK));
        assertEquals(100L, result.getVolume(GooType.METAL));
    }

    @Test
    void withCappedAdd_alreadyFull() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 1000L));
        GooContents result = gc.withCappedAdd(GooType.METAL, 500L, 1000L);
        assertEquals(1000L, result.getVolume(GooType.ROCK));
        assertEquals(0L, result.getVolume(GooType.METAL));
    }

    // -- cappedAddAmount --

    @Test
    void cappedAddAmount_underCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 100L));
        assertEquals(200L, gc.cappedAddAmount(200L, 1000L));
    }

    @Test
    void cappedAddAmount_overCapacity() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 900L));
        assertEquals(100L, gc.cappedAddAmount(500L, 1000L));
    }

    @Test
    void cappedAddAmount_alreadyFull() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 1000L));
        assertEquals(0L, gc.cappedAddAmount(500L, 1000L));
    }

    // -- zero/negative filtering --

    @Test
    void constructorFiltersZeroValues() {
        GooContents gc = new GooContents(Map.of(GooType.ROCK, 0L));
        assertTrue(gc.isEmpty());
    }
}
