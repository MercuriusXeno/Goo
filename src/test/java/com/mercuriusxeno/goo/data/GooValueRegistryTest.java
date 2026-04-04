package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

    // ── Group parsing ────────────────────────────────────────────────────

    @Nested
    class GroupParsing {

        /** Helper: parse a JSON string through the registry's base value loader. */
        private void loadJson(String json) throws IOException {
            registry.parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            registry.copyBaseToEffective();
        }

        /** A _groups entry assigns the same value to all items in the array. */
        @Test
        void groupAssignsValueToAllItems() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "logs": {
                            "value": { "leaf": 384 },
                            "items": ["minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log"]
                        }
                    }
                }
                """);
            assertEquals(384, registry.lookup(id("minecraft:oak_log")).get(GooType.LEAF));
            assertEquals(384, registry.lookup(id("minecraft:spruce_log")).get(GooType.LEAF));
            assertEquals(384, registry.lookup(id("minecraft:birch_log")).get(GooType.LEAF));
        }

        /** A denied group marks all items as denied. */
        @Test
        void groupDeniedMarksAllItems() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "ores": {
                            "value": "denied",
                            "items": ["minecraft:coal_ore", "minecraft:iron_ore"]
                        }
                    }
                }
                """);
            assertTrue(registry.isDenied(id("minecraft:coal_ore")));
            assertTrue(registry.isDenied(id("minecraft:iron_ore")));
        }

        /** Group values support $constant expressions. */
        @Test
        void groupValuesResolveConstants() throws IOException {
            loadJson("""
                {
                    "_constants": { "log": 384 },
                    "_groups": {
                        "logs": {
                            "value": { "leaf": "$log" },
                            "items": ["minecraft:oak_log", "minecraft:spruce_log"]
                        }
                    }
                }
                """);
            assertEquals(384, registry.lookup(id("minecraft:oak_log")).get(GooType.LEAF));
            assertEquals(384, registry.lookup(id("minecraft:spruce_log")).get(GooType.LEAF));
        }

        /** Multiple groups in the same _groups object all parse. */
        @Test
        void multipleGroupsAllParse() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "logs": {
                            "value": { "leaf": 384 },
                            "items": ["minecraft:oak_log"]
                        },
                        "stones": {
                            "value": { "rock": 240 },
                            "items": ["minecraft:stone", "minecraft:cobblestone"]
                        }
                    }
                }
                """);
            assertEquals(384, registry.lookup(id("minecraft:oak_log")).get(GooType.LEAF));
            assertEquals(240, registry.lookup(id("minecraft:stone")).get(GooType.ROCK));
            assertEquals(240, registry.lookup(id("minecraft:cobblestone")).get(GooType.ROCK));
        }

        /** Groups coexist with individual entries. */
        @Test
        void groupsAndIndividualEntriesCoexist() throws IOException {
            loadJson("""
                {
                    "minecraft:diamond": { "crystal": 12000 },
                    "_groups": {
                        "logs": {
                            "value": { "leaf": 384 },
                            "items": ["minecraft:oak_log"]
                        }
                    }
                }
                """);
            assertEquals(12000, registry.lookup(id("minecraft:diamond")).get(GooType.CRYSTAL));
            assertEquals(384, registry.lookup(id("minecraft:oak_log")).get(GooType.LEAF));
        }
    }

    // ── Item reference expressions ───────────────────────────────────────

    @Nested
    class ItemReferenceExpressions {

        private void loadJson(String json) throws IOException {
            registry.parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            registry.copyBaseToEffective();
        }

        /** Adding two items merges their goo types. */
        @Test
        void addTwoItems() throws IOException {
            loadJson("""
                {
                    "minecraft:copper_block": { "metal": 1440, "pulse": 1440 },
                    "minecraft:carved_pumpkin": { "leaf": 480, "vital": 120 },
                    "minecraft:copper_golem": "minecraft:copper_block + minecraft:carved_pumpkin"
                }
                """);
            GooValue golem = registry.lookup(id("minecraft:copper_golem"));
            assertEquals(1440, golem.get(GooType.METAL));
            assertEquals(1440, golem.get(GooType.PULSE));
            assertEquals(480, golem.get(GooType.LEAF));
            assertEquals(120, golem.get(GooType.VITAL));
        }

        /** Subtracting items does per-type subtraction. */
        @Test
        void subtractItems() throws IOException {
            loadJson("""
                {
                    "minecraft:iron_block": { "metal": 90 },
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:leftover": "minecraft:iron_block - minecraft:iron_ingot"
                }
                """);
            assertEquals(80, registry.lookup(id("minecraft:leftover")).get(GooType.METAL));
        }

        /** Multiplying an item by a scalar scales all types. */
        @Test
        void multiplyItemByScalar() throws IOException {
            loadJson("""
                {
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:iron_block": "minecraft:iron_ingot * 9"
                }
                """);
            assertEquals(90, registry.lookup(id("minecraft:iron_block")).get(GooType.METAL));
        }

        /** Dividing an item by a scalar divides all types. */
        @Test
        void divideItemByScalar() throws IOException {
            loadJson("""
                {
                    "minecraft:iron_block": { "metal": 90 },
                    "minecraft:iron_ingot": "minecraft:iron_block / 9"
                }
                """);
            assertEquals(10, registry.lookup(id("minecraft:iron_ingot")).get(GooType.METAL));
        }

        /** Scalar on the left side of multiplication works. */
        @Test
        void scalarTimesItem() throws IOException {
            loadJson("""
                {
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:iron_block": "9 * minecraft:iron_ingot"
                }
                """);
            assertEquals(90, registry.lookup(id("minecraft:iron_block")).get(GooType.METAL));
        }

        /** Parenthesized sub-expressions work. */
        @Test
        void parenthesizedExpression() throws IOException {
            loadJson("""
                {
                    "minecraft:copper_block": { "metal": 100, "pulse": 50 },
                    "minecraft:pumpkin": { "leaf": 200 },
                    "minecraft:thing": "(minecraft:copper_block + minecraft:pumpkin) * 2"
                }
                """);
            GooValue thing = registry.lookup(id("minecraft:thing"));
            assertEquals(200, thing.get(GooType.METAL));
            assertEquals(100, thing.get(GooType.PULSE));
            assertEquals(400, thing.get(GooType.LEAF));
        }

        /** $constants can be used as scalars in item expressions. */
        @Test
        void constantsAsScalars() throws IOException {
            loadJson("""
                {
                    "_constants": { "count": 9 },
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:iron_block": "minecraft:iron_ingot * $count"
                }
                """);
            assertEquals(90, registry.lookup(id("minecraft:iron_block")).get(GooType.METAL));
        }

        /** Operator precedence: multiply before add. */
        @Test
        void precedenceMultiplyBeforeAdd() throws IOException {
            loadJson("""
                {
                    "minecraft:a": { "metal": 10 },
                    "minecraft:b": { "rock": 5 },
                    "minecraft:c": "minecraft:a + minecraft:b * 3"
                }
                """);
            GooValue c = registry.lookup(id("minecraft:c"));
            assertEquals(10, c.get(GooType.METAL));
            assertEquals(15, c.get(GooType.ROCK));
        }

        /** Dot notation pulls a single type as a scalar, usable in expressions. */
        @Test
        void dotNotationExtractsSingleType() throws IOException {
            loadJson("""
                {
                    "minecraft:coal": { "rock": 48, "blaze": 336 },
                    "minecraft:thing": { "blaze": "minecraft:coal.blaze" }
                }
                """);
            assertEquals(336, registry.lookup(id("minecraft:thing")).get(GooType.BLAZE));
            assertEquals(0, registry.lookup(id("minecraft:thing")).get(GooType.ROCK));
        }

        /** Dot notation in arithmetic: minecraft:coal.blaze * 2. */
        @Test
        void dotNotationInArithmetic() throws IOException {
            loadJson("""
                {
                    "minecraft:coal": { "rock": 48, "blaze": 336 },
                    "minecraft:thing": { "blaze": "minecraft:coal.blaze * 2" }
                }
                """);
            assertEquals(672, registry.lookup(id("minecraft:thing")).get(GooType.BLAZE));
        }

        /** Dot notation as a scalar in item-level expression returns single-type GooValue. */
        @Test
        void dotNotationInItemExpression() throws IOException {
            loadJson("""
                {
                    "minecraft:coal": { "rock": 48, "blaze": 336 },
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:thing": "minecraft:iron_ingot + minecraft:coal.blaze"
                }
                """);
            GooValue thing = registry.lookup(id("minecraft:thing"));
            assertEquals(10, thing.get(GooType.METAL));
            assertEquals(336, thing.get(GooType.BLAZE));
            assertEquals(0, thing.get(GooType.ROCK));
        }

        /** Bare word item ref (no namespace) defaults to minecraft: and resolves. */
        @Test
        void bareWordItemRef() throws IOException {
            loadJson("""
                {
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:iron_block": "iron_ingot * 9"
                }
                """);
            assertEquals(90, registry.lookup(id("minecraft:iron_block")).get(GooType.METAL));
        }

        /** Tree constant added to item produces merged GooValue. */
        @Test
        void treeConstantAddedToItem() throws IOException {
            loadJson("""
                {
                    "_constants": { "stripped": { "nether": 50 } },
                    "minecraft:dark_oak_log": { "leaf": 384 },
                    "minecraft:stripped_dark_oak_log": "dark_oak_log + $stripped"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:stripped_dark_oak_log"));
            assertEquals(384, val.get(GooType.LEAF));
            assertEquals(50, val.get(GooType.NETHER));
        }

        /** Tree constant multiplied then added to item. */
        @Test
        void treeConstantMultipliedThenAdded() throws IOException {
            loadJson("""
                {
                    "_constants": { "bonus": { "crystal": 100 } },
                    "minecraft:obsidian": { "rock": 500, "nether": 200 },
                    "minecraft:fancy_obsidian": "obsidian + $bonus * 2"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:fancy_obsidian"));
            assertEquals(500, val.get(GooType.ROCK));
            assertEquals(200, val.get(GooType.NETHER));
            assertEquals(200, val.get(GooType.CRYSTAL));
        }

        /** Tree constant in parenthesized expression. */
        @Test
        void treeConstantInParens() throws IOException {
            loadJson("""
                {
                    "_constants": { "bonus": { "crystal": 10 } },
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:thing": "(iron_ingot + $bonus) * 3"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:thing"));
            assertEquals(30, val.get(GooType.METAL));
            assertEquals(30, val.get(GooType.CRYSTAL));
        }

        /** Implicit multiplication: "4 iron_ingot" == "iron_ingot * 4". */
        @Test
        void implicitMultiplication() throws IOException {
            loadJson("""
                {
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:iron_block": "9 iron_ingot"
                }
                """);
            assertEquals(90, registry.lookup(id("minecraft:iron_block")).get(GooType.METAL));
        }

        /** Implicit multiplication with tree constant: "2 $bonus". */
        @Test
        void implicitMultiplicationWithTreeConstant() throws IOException {
            loadJson("""
                {
                    "_constants": { "bonus": { "nether": 50 } },
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:thing": "iron_ingot + 2 $bonus"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:thing"));
            assertEquals(10, val.get(GooType.METAL));
            assertEquals(100, val.get(GooType.NETHER));
        }

        /** Implicit multiplication in complex expression: "obsidian + 4 lapis_lazuli". */
        @Test
        void implicitMultiplicationInAddition() throws IOException {
            loadJson("""
                {
                    "minecraft:lapis_lazuli": { "crystal": 20, "aeon": 10 },
                    "minecraft:obsidian": { "rock": 100 },
                    "minecraft:crying_obsidian": "obsidian + 4 lapis_lazuli"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:crying_obsidian"));
            assertEquals(100, val.get(GooType.ROCK));
            assertEquals(80, val.get(GooType.CRYSTAL));
            assertEquals(40, val.get(GooType.AEON));
        }
    }

    // ── Effective Cache ───────────────────────────────────────────────────

    @Nested
    class EffectiveCache {

        @TempDir
        Path tempDir;

        /** Loading a flat cache populates effective values without base values. */
        @Test
        void loadEffectiveCache_populatesWithoutBaseValues() throws IOException {
            Path cacheFile = tempDir.resolve("cache.json");
            Files.writeString(cacheFile,
                    "{\"minecraft:iron_ingot\": {\"metal\": 10}}", StandardCharsets.UTF_8);
            registry.setEffectiveCachePath(cacheFile);

            registry.loadEffectiveCache();

            GooValue val = registry.lookup(id("minecraft:iron_ingot"));
            assertNotNull(val);
            assertEquals(10, val.get(GooType.METAL));
            assertEquals(0, registry.baseSize());
        }

        /** Missing cache file leaves effective values empty. */
        @Test
        void loadEffectiveCache_missingFileIsEmpty() {
            registry.setEffectiveCachePath(tempDir.resolve("nonexistent.json"));

            registry.loadEffectiveCache();

            assertEquals(0, registry.size());
        }

        /** Round-trip: save effective values, clear, reload, and verify match. */
        @Test
        void saveAndLoadRoundTrip() {
            Path cacheFile = tempDir.resolve("roundtrip.json");
            registry.setEffectiveCachePath(cacheFile);

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
            registry.deriveFromRecipeInputs(recipes, false);

            Map<Identifier, GooValue> before = Map.copyOf(registry.getEffectiveValues());
            registry.saveEffectiveValues();

            // Clear all state and reload from cache
            registry.clearAll();
            assertEquals(0, registry.size());

            registry.setEffectiveCachePath(cacheFile);
            registry.loadEffectiveCache();

            assertEquals(before.size(), registry.size());
            for (Map.Entry<Identifier, GooValue> entry : before.entrySet()) {
                GooValue reloaded = registry.lookup(entry.getKey());
                assertNotNull(reloaded, "Missing after reload: " + entry.getKey());
                assertEquals(entry.getValue().totalBlobs(), reloaded.totalBlobs(),
                        "Mismatch for " + entry.getKey());
            }
        }
    }

    // ── Expression validation ────────────────────────────────────────────

    @Nested
    class ExpressionValidation {

        /** Forgotten $ prefix on a known constant is flagged. */
        @Test
        void forgottenDollarPrefix() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "_constants": { "iron": 100 },
                    "minecraft:foo": { "metal": "iron * 3" }
                }
                """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("iron") && w.contains("$")),
                    "Should warn about missing $ prefix: " + warnings);
        }

        /** Unknown constant reference is flagged. */
        @Test
        void unknownConstant() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "minecraft:foo": { "metal": "$nonexistent" }
                }
                """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("nonexistent")),
                    "Should warn about unknown constant: " + warnings);
        }

        /** Out-of-order item reference is flagged. */
        @Test
        void outOfOrderReference() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "minecraft:thing": "minecraft:iron_ingot * 2",
                    "minecraft:iron_ingot": { "metal": 10 }
                }
                """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("iron_ingot") && w.contains("not defined above")),
                    "Should warn about out-of-order reference: " + warnings);
        }

        /** Valid expressions produce no warnings. */
        @Test
        void validExpressionsNoWarnings() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "_constants": { "iron": 100 },
                    "minecraft:iron_ingot": { "metal": "$iron" },
                    "minecraft:iron_block": "minecraft:iron_ingot * 9"
                }
                """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /** Tree constant ref in expression doesn't produce a warning. */
        @Test
        void treeConstantRefNoWarning() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "_constants": { "stripped": { "nether": 50 } },
                    "minecraft:dark_oak_log": { "leaf": 384 },
                    "minecraft:stripped_dark_oak_log": "minecraft:dark_oak_log + $stripped"
                }
                """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /** Bare word item ref in expression doesn't produce a warning. */
        @Test
        void bareWordItemRefNoWarning() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:iron_block": "iron_ingot * 9"
                }
                """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /** Dot notation referencing an item above is valid. */
        @Test
        void validDotNotation() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "minecraft:coal": { "rock": 48, "blaze": 336 },
                    "minecraft:thing": { "blaze": "minecraft:coal.blaze * 2" }
                }
                """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /** Dot notation referencing an item below is flagged. */
        @Test
        void outOfOrderDotNotation() {
            List<String> warnings = registry.validateJsonString("""
                {
                    "minecraft:thing": { "blaze": "minecraft:coal.blaze * 2" },
                    "minecraft:coal": { "rock": 48, "blaze": 336 }
                }
                """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("coal") && w.contains("not defined above")),
                    "Should warn about out-of-order dot-notation reference: " + warnings);
        }
    }

    // ── Datapack Merging ─────────────────────────────────────────────────

    @Nested
    class DatapackMerging {

        /** Parses a JSON string into a JsonObject for layer construction. */
        private JsonObject json(String raw) {
            return JsonParser.parseString(raw).getAsJsonObject();
        }

        /** A later pack's item definition overwrites an earlier one. */
        @Test
        void laterPackOverridesItem() {
            JsonObject base = json("""
                { "minecraft:stick": { "vital": 5 } }
                """);
            JsonObject overlay = json("""
                { "minecraft:stick": { "vital": 10 } }
                """);
            JsonObject merged = GooValueRegistry.mergeBaseValueJsonLayers(List.of(base, overlay));

            assertEquals(10, merged.getAsJsonObject("minecraft:stick").get("vital").getAsInt());
        }

        /** A later pack can add items not present in the earlier pack. */
        @Test
        void laterPackAddsNewItem() {
            JsonObject base = json("""
                { "minecraft:stick": { "vital": 5 } }
                """);
            JsonObject overlay = json("""
                { "minecraft:coal": { "blaze": 20 } }
                """);
            JsonObject merged = GooValueRegistry.mergeBaseValueJsonLayers(List.of(base, overlay));

            assertTrue(merged.has("minecraft:stick"), "Base item should survive");
            assertTrue(merged.has("minecraft:coal"), "Overlay item should appear");
        }

        /** Constants merge at inner key level: new keys add, existing keys overwrite. */
        @Test
        void constantsMergeAtKeyLevel() {
            JsonObject base = json("""
                { "_constants": { "base": 1000, "stone": 240 } }
                """);
            JsonObject overlay = json("""
                { "_constants": { "base": 2000 } }
                """);
            JsonObject merged = GooValueRegistry.mergeBaseValueJsonLayers(List.of(base, overlay));

            JsonObject constants = merged.getAsJsonObject("_constants");
            assertEquals(2000, constants.get("base").getAsInt(), "Overridden constant");
            assertEquals(240, constants.get("stone").getAsInt(), "Preserved constant");
        }

        /** Groups merge at inner key level: new groups add, existing groups overwrite. */
        @Test
        void groupsMergeAtKeyLevel() {
            JsonObject base = json("""
                {
                    "_groups": {
                        "logs": { "value": { "vital": 10 }, "items": ["minecraft:oak_log"] },
                        "ores": { "value": "denied", "items": ["minecraft:iron_ore"] }
                    }
                }
                """);
            JsonObject overlay = json("""
                {
                    "_groups": {
                        "logs": { "value": { "vital": 20 }, "items": ["minecraft:birch_log"] }
                    }
                }
                """);
            JsonObject merged = GooValueRegistry.mergeBaseValueJsonLayers(List.of(base, overlay));

            JsonObject groups = merged.getAsJsonObject("_groups");
            assertTrue(groups.has("ores"), "Preserved group from base");
            assertEquals(20, groups.getAsJsonObject("logs")
                    .getAsJsonObject("value").get("vital").getAsInt(), "Overridden group");
        }

        /** A single layer passes through unchanged. */
        @Test
        void singleLayerIdenticalToInput() {
            JsonObject layer = json("""
                {
                    "_constants": { "base": 1000 },
                    "minecraft:stick": { "vital": 5 }
                }
                """);
            JsonObject merged = GooValueRegistry.mergeBaseValueJsonLayers(List.of(layer));

            assertEquals(1000, merged.getAsJsonObject("_constants").get("base").getAsInt());
            assertEquals(5, merged.getAsJsonObject("minecraft:stick").get("vital").getAsInt());
        }

        /** An empty layer list produces an empty JsonObject. */
        @Test
        void emptyLayerListProducesEmptyObject() {
            JsonObject merged = GooValueRegistry.mergeBaseValueJsonLayers(List.of());

            assertEquals(0, merged.size());
        }

        /** A later pack can deny a previously valued item. */
        @Test
        void laterPackCanDenyPreviouslyValuedItem() {
            JsonObject base = json("""
                { "minecraft:iron_ore": { "metal": 100 } }
                """);
            JsonObject overlay = json("""
                { "minecraft:iron_ore": "denied" }
                """);
            JsonObject merged = GooValueRegistry.mergeBaseValueJsonLayers(List.of(base, overlay));

            assertEquals("denied", merged.get("minecraft:iron_ore").getAsString());
        }
    }

    // ── Tag Expansion ────────────────────────────────────────────────────

    @Nested
    class TagExpansion {

        /** Parses a JSON string into a JsonObject. */
        private JsonObject json(String raw) {
            return JsonParser.parseString(raw).getAsJsonObject();
        }

        /** Builds a tag resolver backed by a map of tag ID to member set. */
        private Function<Identifier, Set<Identifier>> resolver(Map<Identifier, Set<Identifier>> tags) {
            return tagId -> tags.getOrDefault(tagId, Collections.emptySet());
        }

        /** A tag key expands to all members with the tag's value. */
        @Test
        void tagExpandsToAllMembers() {
            JsonObject input = json("""
                { "#test:planks": { "leaf": 100 } }
                """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueRegistry.expandTagEntries(input, resolver(tags));

            assertTrue(result.has("minecraft:oak_planks"));
            assertTrue(result.has("minecraft:birch_planks"));
            assertFalse(result.has("#test:planks"));
            assertEquals(100, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(100, result.getAsJsonObject("minecraft:birch_planks").get("leaf").getAsInt());
        }

        /** An explicit entry after a tag overwrites that member (last-in-wins). */
        @Test
        void explicitEntryAfterTagOverrides() {
            JsonObject input = json("""
                { "#test:planks": { "leaf": 100 }, "minecraft:oak_planks": { "leaf": 200 } }
                """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueRegistry.expandTagEntries(input, resolver(tags));

            assertEquals(200, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(100, result.getAsJsonObject("minecraft:birch_planks").get("leaf").getAsInt());
        }

        /** A tag after an explicit entry overwrites it (last-in-wins). */
        @Test
        void explicitEntryBeforeTagIsOverridden() {
            JsonObject input = json("""
                { "minecraft:oak_planks": { "leaf": 200 }, "#test:planks": { "leaf": 100 } }
                """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueRegistry.expandTagEntries(input, resolver(tags));

            assertEquals(100, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(100, result.getAsJsonObject("minecraft:birch_planks").get("leaf").getAsInt());
        }

        /** Multiple tags expand independently. */
        @Test
        void multipleTagsExpanded() {
            JsonObject input = json("""
                { "#test:planks": { "leaf": 100 }, "#test:ores": { "rock": 50 } }
                """);
            Map<Identifier, Set<Identifier>> tags = new HashMap<>();
            tags.put(id("test:planks"), Set.of(id("minecraft:oak_planks")));
            tags.put(id("test:ores"), Set.of(id("minecraft:iron_ore")));

            JsonObject result = GooValueRegistry.expandTagEntries(input, resolver(tags));

            assertEquals(100, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(50, result.getAsJsonObject("minecraft:iron_ore").get("rock").getAsInt());
        }

        /** An unknown tag (empty resolver result) adds no entries and does not crash. */
        @Test
        void unknownTagSkipped() {
            JsonObject input = json("""
                { "#test:nonexistent": { "leaf": 100 } }
                """);

            JsonObject result = GooValueRegistry.expandTagEntries(input, resolver(Map.of()));

            assertEquals(0, result.size());
        }

        /** A tag value can be a string expression; expansion preserves it as-is for later evaluation. */
        @Test
        void tagValueCanBeExpression() {
            JsonObject input = json("""
                { "#test:planks": "stick * 2" }
                """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueRegistry.expandTagEntries(input, resolver(tags));

            assertEquals("stick * 2", result.get("minecraft:oak_planks").getAsString());
            assertEquals("stick * 2", result.get("minecraft:birch_planks").getAsString());
        }

        /** _constants and _groups pass through unchanged; only # keys are expanded. */
        @Test
        void constantsAndGroupsUntouchedByTagExpansion() {
            JsonObject input = json("""
                {
                    "_constants": { "iron": 10 },
                    "_groups": { "ores": { "value": { "rock": 5 }, "items": ["minecraft:gold_ore"] } },
                    "#test:planks": { "leaf": 100 }
                }
                """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                id("test:planks"), Set.of(id("minecraft:oak_planks"))
            );

            JsonObject result = GooValueRegistry.expandTagEntries(input, resolver(tags));

            assertTrue(result.has("_constants"));
            assertTrue(result.has("_groups"));
            assertEquals(10, result.getAsJsonObject("_constants").get("iron").getAsInt());
            assertTrue(result.has("minecraft:oak_planks"));
            assertFalse(result.has("#test:planks"));
        }
    }
}
