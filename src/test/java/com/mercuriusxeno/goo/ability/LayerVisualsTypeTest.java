package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the LayerVisualsType registry resolves rock-dust, blaze-flame,
 * and a none/no-op entry by name. Particle emission requires a
 * ServerLevel and is exercised through gametests.
 */
class LayerVisualsTypeTest {

    @Test
    void rockDust_resolvesByName() {
        assertNotNull(LayerVisualsType.byName("rock_dust"));
    }

    @Test
    void blazeFlame_resolvesByName() {
        assertNotNull(LayerVisualsType.byName("blaze_flame"));
    }

    @Test
    void none_resolvesByName() {
        assertNotNull(LayerVisualsType.byName("none"));
    }

    @Test
    void registry_returnsSingletonInstance() {
        assertSame(LayerVisualsType.byName("rock_dust"),
                LayerVisualsType.byName("rock_dust"));
    }

    @Test
    void distinctNames_returnDistinctVisuals() {
        assertNotSame(LayerVisualsType.byName("rock_dust"),
                LayerVisualsType.byName("blaze_flame"));
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> LayerVisualsType.byName("does_not_exist"));
    }
}
