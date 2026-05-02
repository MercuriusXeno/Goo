package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the LayerAudioType registry resolves stone-break,
 * generic-explode, and a none/no-op entry by name. Sound emission
 * requires a ServerLevel and is exercised through gametests.
 */
class LayerAudioTypeTest {

    @Test
    void stoneBreak_resolvesByName() {
        assertNotNull(LayerAudioType.byName("stone_break"));
    }

    @Test
    void genericExplode_resolvesByName() {
        assertNotNull(LayerAudioType.byName("generic_explode"));
    }

    @Test
    void none_resolvesByName() {
        assertNotNull(LayerAudioType.byName("none"));
    }

    @Test
    void registry_returnsSingletonInstance() {
        assertSame(LayerAudioType.byName("stone_break"),
                LayerAudioType.byName("stone_break"));
    }

    @Test
    void distinctNames_returnDistinctAudio() {
        assertNotSame(LayerAudioType.byName("stone_break"),
                LayerAudioType.byName("generic_explode"));
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> LayerAudioType.byName("does_not_exist"));
    }
}
