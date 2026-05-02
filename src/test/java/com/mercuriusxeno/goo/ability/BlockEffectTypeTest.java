package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the BlockEffectType registry resolves the three core effects
 * by name and rejects unknown names. The effects themselves mutate the
 * world and are exercised through gametests; here we only assert the
 * named-lookup seam exists.
 */
class BlockEffectTypeTest {

    @Test
    void silkBreak_resolvesByName() {
        BlockEffect effect = BlockEffectType.byName("silk_break");
        assertNotNull(effect);
    }

    @Test
    void fortuneSmeltBreak_resolvesByName() {
        BlockEffect effect = BlockEffectType.byName("fortune_smelt_break");
        assertNotNull(effect);
    }

    @Test
    void freeze_resolvesByName() {
        BlockEffect effect = BlockEffectType.byName("freeze");
        assertNotNull(effect);
    }

    @Test
    void registry_returnsSingletonInstance() {
        assertSame(BlockEffectType.byName("silk_break"),
                BlockEffectType.byName("silk_break"));
    }

    @Test
    void distinctNames_returnDistinctEffects() {
        assertNotSame(BlockEffectType.byName("silk_break"),
                BlockEffectType.byName("fortune_smelt_break"));
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BlockEffectType.byName("does_not_exist"));
    }
}
