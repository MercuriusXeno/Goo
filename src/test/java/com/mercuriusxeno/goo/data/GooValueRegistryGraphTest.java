package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for registry-level graph operations in GooValueRegistry: SCC classification
 * and end-to-end cycle detection. Pure algorithm tests live in DirectedGraphUtilsTest.
 */
class GooValueRegistryGraphTest {

    private GooValueRegistry registry;

    /** Creates a fresh registry before each test. */
    @BeforeEach
    void setUp() {
        registry = new GooValueRegistry();
    }

    /** Sets base values on the registry, copying to effective. */
    private void setBaseValues(Map<Identifier, GooValue> values) {
        registry.baseValues.clear();
        registry.baseValues.putAll(values);
        registry.effectiveValues.clear();
        registry.effectiveValues.putAll(values);
    }

    // ── SCC Classification ──────────────────────────────────────────────

    @Nested
    class SccClassification {

        /** SCC with a directly base-valued member is classified with that anchor. */
        @Test
        void directAnchorIsSet() {
            Set<Identifier> scc = Set.of(id("a"), id("b"));
            Set<Identifier> anchoredNodes = Set.of(id("a"), id("b"));
            Set<Identifier> baseValueKeys = Set.of(id("a"));

            var cycle = SccClassifier.classifyScc(scc, anchoredNodes, baseValueKeys);
            assertEquals(id("a"), cycle.anchor());
            assertTrue(cycle.hasAnchor());
        }

        /** SCC reachable from anchored nodes but no direct base value. */
        @Test
        void reachableButNoDirectAnchor() {
            Set<Identifier> scc = Set.of(id("x"), id("y"));
            Set<Identifier> anchoredNodes = Set.of(id("x")); // reachable from outside
            Set<Identifier> baseValueKeys = Set.of(); // no direct base values

            var cycle = SccClassifier.classifyScc(scc, anchoredNodes, baseValueKeys);
            assertNull(cycle.anchor());
            assertTrue(cycle.hasAnchor());
        }

        /** SCC with no anchored members is dead (hasAnchor=false). */
        @Test
        void deadSccHasNoAnchor() {
            Set<Identifier> scc = Set.of(id("x"), id("y"));
            Set<Identifier> anchoredNodes = Set.of(); // nothing anchored
            Set<Identifier> baseValueKeys = Set.of();

            var cycle = SccClassifier.classifyScc(scc, anchoredNodes, baseValueKeys);
            assertNull(cycle.anchor());
            assertFalse(cycle.hasAnchor());
        }
    }

    // ── Integration: detectCycles via recipes ───────────────────────────

    @Nested
    class CycleDetectionIntegration {

        /** Cycle detected through recipe derivation. */
        @Test
        void cycleThroughRecipes() {
            setBaseValues(Map.of());
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")),
                recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertFalse(registry.diagnostics().cycles().isEmpty());
        }

        /** No cycles in a clean recipe chain. */
        @Test
        void noCyclesInCleanChain() {
            setBaseValues(Map.of(
                id("raw"), goo(GooType.METAL, 5)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("raw")),
                recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.diagnostics().cycles().isEmpty());
        }
    }
}
