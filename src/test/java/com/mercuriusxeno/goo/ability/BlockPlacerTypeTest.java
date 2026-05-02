package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the BlockPlacerType registry resolves the glow-crystal
 * placer by name and rejects unknown names. Block-state derivation
 * runs against a ServerLevel and is exercised via gametests.
 */
class BlockPlacerTypeTest {

    @Test
    void glowCrystal_resolvesByName() {
        assertNotNull(BlockPlacerType.byName("glow_crystal"));
    }

    @Test
    void registry_returnsSingletonInstance() {
        assertSame(BlockPlacerType.byName("glow_crystal"),
                BlockPlacerType.byName("glow_crystal"));
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BlockPlacerType.byName("does_not_exist"));
    }
}
