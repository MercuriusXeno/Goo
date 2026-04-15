package com.mercuriusxeno.goo;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GooType enum: count, uniqueness, and serialization contract.
 */
class GooTypeTest {

    /** Verifies that exactly 16 goo types are defined. */
    @Test
    void sixteenTypesExist() {
        assertEquals(16, GooType.values().length);
    }

    /** Verifies that all type IDs are unique. */
    @Test
    void allIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (GooType type : GooType.values()) {
            assertTrue(ids.add(type.getId()), "Duplicate ID: " + type.getId());
        }
    }

    /** Verifies that getSerializedName matches getId for all types. */
    @Test
    void serializedNameMatchesId() {
        for (GooType type : GooType.values()) {
            assertEquals(type.getId(), type.getSerializedName());
        }
    }

    /** Verifies that all color values are non-negative. */
    @Test
    void allColorsNonNegative() {
        for (GooType type : GooType.values()) {
            assertTrue(type.getColor() >= 0, type.getId() + " has negative color");
        }
    }

    /** Verifies that translation keys follow the expected pattern. */
    @Test
    void translationKeyFormat() {
        for (GooType type : GooType.values()) {
            assertEquals("goo.type." + type.getId(), type.getTranslationKey());
        }
    }
}
