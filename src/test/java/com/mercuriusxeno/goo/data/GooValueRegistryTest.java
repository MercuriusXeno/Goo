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
 * Tests for GooValueRegistry derivation, conflict detection, and effective value resolution.
 * All tests use the pure-logic layer  - no Minecraft server required.
 */
class GooValueRegistryTest {

    private GooValueRegistry registry;

    /** Creates a fresh registry before each test. */
    @BeforeEach
    void setUp() {
        registry = new GooValueRegistry();
    }

    // ── LCD Derivation ──────────────────────────────────────────────────

    @Nested
    class LcdDerivation {

        /** A single recipe with all ingredients valued produces a derived value. */
        @Test
        void singleRecipeDerivesValue() {
            registry.setBaseValues(Map.of(
                id("minecraft:iron_ingot"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:iron_block", 1,
                    slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                    slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                    slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                    slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                    slot("minecraft:iron_ingot"))
            );

            int derived = registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, derived);
            GooValue val = registry.lookup(id("minecraft:iron_block"));
            assertNotNull(val);
            assertEquals(90, val.get(GooType.METAL)); // 9 * 10
        }

        /** Multiple recipes for the same item: cheapest wins (LCD rule). */
        @Test
        void multipleRecipesCheapestWins() {
            registry.setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 10),
                id("b"), goo(GooType.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("x", 1, slot("a")),          // cost 10
                recipe("x", 1, slot("b"), slot("b")) // cost 6
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("x"));
            assertNotNull(val);
            assertEquals(6, val.get(GooType.METAL)); // cheaper recipe wins
        }

        /** Multi-pass: recipe B needs A's value, A derives on pass 1, B on pass 2. */
        @Test
        void multiPassDerivation() {
            registry.setBaseValues(Map.of(
                id("raw"), goo(GooType.ROCK, 5)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("intermediate", 1, slot("raw"), slot("raw")),  // pass 1: 10
                recipe("final", 1, slot("intermediate"))               // pass 2: 10
            );

            int derived = registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(2, derived);
            assertEquals(10, registry.lookup(id("final")).get(GooType.ROCK));
        }

        /** Ingredient with alternatives: cheapest alternative is chosen. */
        @Test
        void ingredientAlternativesCheapestChosen() {
            registry.setBaseValues(Map.of(
                id("cheap"), goo(GooType.LEAF, 2),
                id("expensive"), goo(GooType.LEAF, 20)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 1, slot("cheap", "expensive"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(2, registry.lookup(id("output")).get(GooType.LEAF));
        }

        /** Result count > 1 divides the total value. */
        @Test
        void resultCountDividesValue() {
            registry.setBaseValues(Map.of(
                id("planks"), goo(GooType.LEAF, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("sticks", 4, slot("planks"), slot("planks")) // 20 / 4 = 5
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(5, registry.lookup(id("sticks")).get(GooType.LEAF));
        }

        /** Missing ingredient value causes recipe to be skipped. */
        @Test
        void missingIngredientSkipsRecipe() {
            registry.setBaseValues(Map.of(
                id("known"), goo(GooType.METAL, 5)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 1, slot("known"), slot("unknown"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertNull(registry.lookup(id("output")));
        }

        /** Pass limit terminates derivation even with circular dependencies. */
        @Test
        void passLimitTerminates() {
            // No base values, recipes form a cycle  - should terminate without error
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")),
                recipe("b", 1, slot("a"))
            );

            assertDoesNotThrow(() -> registry.deriveFromRecipeInputs(recipes, false));
            assertEquals(0, registry.derivedSize());
        }

        /** Recipe with no ingredients produces no derived value. */
        @Test
        void noIngredientsProducesNothing() {
            List<RecipeInput> recipes = List.of(
                new RecipeInput(id("output"), 1, List.of())
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertNull(registry.lookup(id("output")));
        }
    }

    // ── Conflict Detection ──────────────────────────────────────────────

    @Nested
    class ConflictDetection {

        /** No conflict when derived equals base. */
        @Test
        void noConflictWhenEqual() {
            registry.setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 10),
                id("b"), goo(GooType.METAL, 5)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b"), slot("b")) // cost 10 == base 10
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.getLastConflicts().isEmpty());
        }

        /** Recipe cheaper than base is flagged as conflict. */
        @Test
        void recipeCheaperThanBaseIsConflict() {
            registry.setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 20),
                id("b"), goo(GooType.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")) // cost 3 < base 20
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.getLastConflicts().size());
            assertTrue(registry.getLastConflicts().get(0).isRecipeCheaper());
        }

        /** Base cheaper than recipe is also flagged as conflict (value mismatch). */
        @Test
        void baseCheaperThanRecipeIsConflict() {
            registry.setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 5),
                id("b"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")) // cost 10 > base 5
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.getLastConflicts().size());
            assertFalse(registry.getLastConflicts().get(0).isRecipeCheaper());
        }
    }

    // ── Effective Values ────────────────────────────────────────────────

    @Nested
    class EffectiveValues {

        /** Derived-only item: effective equals derived. */
        @Test
        void derivedOnlyItemUsesDerivation() {
            registry.setBaseValues(Map.of(
                id("base_item"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("derived_only", 1, slot("base_item"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue eff = registry.lookup(id("derived_only"));
            assertNotNull(eff);
            assertEquals(10, eff.get(GooType.METAL));
        }

        /** Base-only item: effective equals base. */
        @Test
        void baseOnlyItemUsesBase() {
            registry.setBaseValues(Map.of(
                id("base_only"), goo(GooType.LEAF, 7)
            ));
            registry.deriveFromRecipeInputs(List.of(), false);

            GooValue eff = registry.lookup(id("base_only"));
            assertNotNull(eff);
            assertEquals(7, eff.get(GooType.LEAF));
        }

        /** Both exist, recipe cheaper, override off: effective = derived. */
        @Test
        void cheaperRecipeWinsWithoutOverride() {
            registry.setBaseValues(Map.of(
                id("item"), goo(GooType.METAL, 20),
                id("cheap"), goo(GooType.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("item", 1, slot("cheap"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(3, registry.lookup(id("item")).get(GooType.METAL));
        }

        /** Both exist, recipe cheaper, override on: effective = base. */
        @Test
        void baseWinsWithOverride() {
            registry.setBaseValues(Map.of(
                id("item"), goo(GooType.METAL, 20),
                id("cheap"), goo(GooType.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("item", 1, slot("cheap"))
            );

            registry.deriveFromRecipeInputs(recipes, true);
            assertEquals(20, registry.lookup(id("item")).get(GooType.METAL));
        }
    }

    // ── Divisibility Loss ───────────────────────────────────────────────

    @Nested
    class DivisibilityLossDetection {

        /** Evenly divisible recipe produces no loss. */
        @Test
        void evenlyDivisibleNoLoss() {
            registry.setBaseValues(Map.of(
                id("input"), goo(GooType.ROCK, 12)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 4, slot("input")) // 12 / 4 = 3, no remainder
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.getLastDivisibilityLosses().isEmpty());
        }

        /** Recipe with remainder detects loss with correct amounts. */
        @Test
        void remainderDetectsLoss() {
            registry.setBaseValues(Map.of(
                id("input"), goo(GooType.ROCK, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 3, slot("input")) // 10 / 3 = 3 remainder 1
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.getLastDivisibilityLosses().size());
            var loss = registry.getLastDivisibilityLosses().get(0);
            assertEquals(10, loss.inputTotal());
            assertEquals(3, loss.outputCount());
            assertEquals(3, loss.perItemValue());
            assertEquals(1, loss.lostBlobs());
        }

        /** Result count 1 is never checked for divisibility loss. */
        @Test
        void resultCountOneSkipped() {
            registry.setBaseValues(Map.of(
                id("input"), goo(GooType.ROCK, 7)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 1, slot("input"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.getLastDivisibilityLosses().isEmpty());
        }
    }

    // ── Deny List ────────────────────────────────────────────────────────

    @Nested
    class DenyList {

        /** Denied items never receive a derived value, even with valid recipes. */
        @Test
        void deniedItemNeverDerives() {
            registry.setBaseValues(Map.of(
                id("raw"), goo(GooType.METAL, 10)
            ));
            registry.setDeniedItems(Set.of(id("ore_block")));
            List<RecipeInput> recipes = List.of(
                recipe("ore_block", 1, slot("raw"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertNull(registry.lookup(id("ore_block")));
        }

        /** Denied items can still be used as recipe inputs for other items. */
        @Test
        void deniedItemWorksAsInput() {
            registry.setBaseValues(Map.of(
                id("ore_block"), goo(GooType.METAL, 20)
            ));
            registry.setDeniedItems(Set.of(id("ore_block")));
            List<RecipeInput> recipes = List.of(
                recipe("ingot", 1, slot("ore_block"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            // ore_block is denied as output, but its base value still works as input
            GooValue val = registry.lookup(id("ingot"));
            assertNotNull(val);
            assertEquals(20, val.get(GooType.METAL));
        }

        /** Denied items don't appear in effective values even if they have a base value. */
        @Test
        void deniedItemBaseValueStillInEffective() {
            // Deny list only blocks derivation output, not base values
            registry.setBaseValues(Map.of(
                id("ore"), goo(GooType.METAL, 10)
            ));
            registry.setDeniedItems(Set.of(id("ore")));
            registry.deriveFromRecipeInputs(List.of(), false);

            // Base value should still be present  - deny blocks derivation, not base
            assertNotNull(registry.lookup(id("ore")));
        }
    }

    // ── Container Subtraction ────────────────────────────────────────────

    @Nested
    class ContainerSubtraction {

        /** Bucket returned: net cost = ingredient value minus bucket value. */
        @Test
        void bucketReturnedSubtractsContainerValue() {
            registry.setBaseValues(Map.of(
                id("minecraft:milk_bucket"), goo(GooType.VITAL, 30),
                id("minecraft:bucket"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 1,
                    Map.of("minecraft:milk_bucket", "minecraft:bucket"),
                    slot("minecraft:milk_bucket"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("output"));
            assertNotNull(val);
            // 30 vital - 0 vital from bucket = 30 vital; 0 metal - 10 metal = no subtraction on missing type
            assertEquals(30, val.get(GooType.VITAL));
            assertEquals(0, val.get(GooType.METAL)); // bucket's metal not in milk_bucket
        }

        /** Container has no known value: full ingredient cost is used. */
        @Test
        void containerNoValueUsesFullCost() {
            registry.setBaseValues(Map.of(
                id("minecraft:milk_bucket"), goo(GooType.VITAL, 30)
                // no value for bucket
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 1,
                    Map.of("minecraft:milk_bucket", "minecraft:bucket"),
                    slot("minecraft:milk_bucket"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("output"));
            assertNotNull(val);
            assertEquals(30, val.get(GooType.VITAL));
        }

        /** Multiple container ingredients: each slot subtracts independently. */
        @Test
        void multipleContainerIngredients() {
            registry.setBaseValues(Map.of(
                id("milk_bucket"), goo(GooType.VITAL, 20, GooType.METAL, 10),
                id("bucket"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("cake", 1,
                    Map.of("milk_bucket", "bucket"),
                    slot("milk_bucket"), slot("milk_bucket"), slot("milk_bucket"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("cake"));
            assertNotNull(val);
            // Each slot: 20 vital + 10 metal - 10 metal = 20 vital + 0 metal
            // 3 slots: 60 vital
            assertEquals(60, val.get(GooType.VITAL));
            assertEquals(0, val.get(GooType.METAL)); // bucket metal cancels ingredient metal
        }

        /** Container worth more than ingredient: gross cost is used (guard case). */
        @Test
        void containerWorthMoreThanIngredientUsesGross() {
            registry.setBaseValues(Map.of(
                id("cheap_item"), goo(GooType.LEAF, 5),
                id("expensive_container"), goo(GooType.LEAF, 50)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 1,
                    Map.of("cheap_item", "expensive_container"),
                    slot("cheap_item"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("output"));
            assertNotNull(val);
            // 5 - 50 = -45 → empty → guard returns gross (5)
            assertEquals(5, val.get(GooType.LEAF));
        }

        /** Existing tests still pass with the 3-arg constructor (empty container map). */
        @Test
        void backwardCompatibleNoContainers() {
            registry.setBaseValues(Map.of(
                id("iron"), goo(GooType.METAL, 10)
            ));
            // Uses the 3-arg RecipeInput constructor (no container map)
            List<RecipeInput> recipes = List.of(
                new RecipeInput(id("block"), 1,
                    List.of(Set.of(id("iron")), Set.of(id("iron"))))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(20, registry.lookup(id("block")).get(GooType.METAL));
        }
    }

    // ── Cycle Detection ─────────────────────────────────────────────────

    @Nested
    class CycleDetection {

        /** Two-item cycle is detected as a single SCC. */
        @Test
        void twoItemCycleDetected() {
            registry.setBaseValues(Map.of());
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")),
                recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.getLastCycles();
            assertEquals(1, cycles.size());
            assertTrue(cycles.get(0).items().containsAll(List.of(id("a"), id("b"))));
        }

        /** Anchored cycle: one member has a base value. */
        @Test
        void anchoredCycleDetected() {
            registry.setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")),
                recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.getLastCycles();
            assertEquals(1, cycles.size());
            assertTrue(cycles.get(0).hasAnchor());
            assertEquals(id("a"), cycles.get(0).anchor());
        }

        /** Non-cyclic chain produces no cycles. */
        @Test
        void noCyclesInLinearChain() {
            registry.setBaseValues(Map.of(
                id("raw"), goo(GooType.ROCK, 5)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("mid", 1, slot("raw")),
                recipe("final", 1, slot("mid"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.getLastCycles().isEmpty());
        }

        /** Unanchored cycle: no member has a base value or is reachable from one. */
        @Test
        void unanchoredCycleDetected() {
            registry.setBaseValues(Map.of());
            List<RecipeInput> recipes = List.of(
                recipe("x", 1, slot("y")),
                recipe("y", 1, slot("x"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.getLastCycles();
            assertEquals(1, cycles.size());
            assertFalse(cycles.get(0).hasAnchor());
            assertNull(cycles.get(0).anchor());
        }
    }

    // ── Group By Output ─────────────────────────────────────────────────

    @Nested
    class GroupByOutput {

        /** Recipes with the same output are grouped together. */
        @Test
        void recipesGroupedByOutput() {
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("x")),
                recipe("a", 1, slot("y")),
                recipe("b", 1, slot("z"))
            );

            Map<Identifier, List<RecipeInput>> grouped = GooValueDerivation.groupByOutput(recipes);
            assertEquals(2, grouped.size());
            assertEquals(2, grouped.get(id("a")).size());
            assertEquals(1, grouped.get(id("b")).size());
        }

        /** Empty recipe list produces empty map. */
        @Test
        void emptyRecipesProduceEmptyMap() {
            Map<Identifier, List<RecipeInput>> grouped = GooValueDerivation.groupByOutput(List.of());
            assertTrue(grouped.isEmpty());
        }
    }

    // ── Lookup For Derivation ───────────────────────────────────────────

    @Nested
    class LookupForDerivation {

        /** Base-only value is returned when no derived value exists. */
        @Test
        void baseOnlyReturnsBase() {
            Map<Identifier, GooValue> base = Map.of(id("item"), goo(GooType.METAL, 10));
            GooValue result = GooValueDerivation.lookupForDerivation(id("item"), base, Map.of());
            assertNotNull(result);
            assertEquals(10, result.get(GooType.METAL));
        }

        /** Unknown item returns null. */
        @Test
        void unknownItemReturnsNull() {
            assertNull(GooValueDerivation.lookupForDerivation(id("unknown"), Map.of(), Map.of()));
        }

        /** When both exist, cheaper value wins. */
        @Test
        void cheaperOfBaseAndDerivedWins() {
            Map<Identifier, GooValue> base = Map.of(id("item"), goo(GooType.METAL, 20));
            Map<Identifier, GooValue> derived = Map.of(id("item"), goo(GooType.METAL, 3));
            GooValue result = GooValueDerivation.lookupForDerivation(id("item"), base, derived);
            assertNotNull(result);
            assertEquals(3, result.get(GooType.METAL));
        }
    }

    // ── Multi-type value handling ────────────────────────────────────────

    @Nested
    class MultiTypeValues {

        /** Multi-type ingredients sum correctly. */
        @Test
        void multiTypeIngredientsSumCorrectly() {
            registry.setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 5, GooType.CRYSTAL, 3),
                id("b"), goo(GooType.METAL, 2, GooType.LEAF, 4)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("combo", 1, slot("a"), slot("b"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("combo"));
            assertNotNull(val);
            assertEquals(7, val.get(GooType.METAL));   // 5 + 2
            assertEquals(3, val.get(GooType.CRYSTAL));  // 3 + 0
            assertEquals(4, val.get(GooType.LEAF));     // 0 + 4
        }

        /** Multi-type value divided correctly. */
        @Test
        void multiTypeDivision() {
            registry.setBaseValues(Map.of(
                id("block"), goo(GooType.METAL, 9, GooType.CRYSTAL, 6)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("nugget", 3, slot("block"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("nugget"));
            assertNotNull(val);
            assertEquals(3, val.get(GooType.METAL));   // 9 / 3
            assertEquals(2, val.get(GooType.CRYSTAL)); // 6 / 3
        }
    }

    // ── findCheapestAmong ────────────────────────────────────────────────

    @Nested
    class FindCheapestAmong {

        /** Returns the candidate with fewest total blobs. */
        @Test
        void returnsLowestTotalBlobs() {
            Map<Identifier, GooValue> values = Map.of(
                id("a"), goo(GooType.METAL, 10),
                id("b"), goo(GooType.METAL, 3),
                id("c"), goo(GooType.METAL, 7)
            );
            Identifier result = GooValueRegistry.findCheapestAmong(
                Set.of(id("a"), id("b"), id("c")), values::get);
            assertEquals(id("b"), result);
        }

        /** Null lookup results are skipped. */
        @Test
        void nullLookupSkipped() {
            Map<Identifier, GooValue> values = Map.of(
                id("known"), goo(GooType.METAL, 5)
            );
            Identifier result = GooValueRegistry.findCheapestAmong(
                Set.of(id("known"), id("missing")), values::get);
            assertEquals(id("known"), result);
        }

        /** Empty GooValue is skipped even if lookup returns non-null. */
        @Test
        void emptyValueSkipped() {
            Map<Identifier, GooValue> values = Map.of(
                id("empty"), GooValue.EMPTY,
                id("real"), goo(GooType.METAL, 5)
            );
            Identifier result = GooValueRegistry.findCheapestAmong(
                Set.of(id("empty"), id("real")), values::get);
            assertEquals(id("real"), result);
        }

        /** Returns null if no candidate has a non-empty value. */
        @Test
        void allNullReturnsNull() {
            Identifier result = GooValueRegistry.findCheapestAmong(
                Set.of(id("a"), id("b")), id -> null);
            assertNull(result);
        }
    }
}
