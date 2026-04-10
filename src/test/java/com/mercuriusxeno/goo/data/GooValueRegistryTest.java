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
import java.util.EnumMap;
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

    // ── Test helpers (replace removed registry methods) ────────────────

    /** Sets base values on the registry, copying to effective. */
    private void setBaseValues(Map<Identifier, GooValue> values) {
        registry.baseValues.clear();
        registry.baseValues.putAll(values);
        registry.effectiveValues.clear();
        registry.effectiveValues.putAll(values);
    }

    /** Sets denied items on the registry. */
    private void setDeniedItems(Set<Identifier> items) {
        registry.deniedItems.clear();
        registry.deniedItems.addAll(items);
    }

    /** Returns derived values snapshot. */
    private Map<Identifier, GooValue> getDerivedValues() {
        return registry.lastDerivation != null
                ? registry.lastDerivation.derivedValues() : Map.of();
    }

    /** Parses base values from a JSON stream, copying conversions back to registry. */
    private void parseBaseValuesFromStream(java.io.InputStream is) throws IOException {
        var state = new GooValueLoader.ParseState(
                registry.baseValues, registry.effectiveValues,
                registry.deniedItems, registry.restrictedItems,
                registry.constants, registry.treeConstants,
                registry.pseudoTags);
        GooValueLoader.parseBaseValuesFromStream(is, state);
        registry.preConversions = state.preConversions;
        registry.postConversions = state.postConversions;
    }

    /** Copies base values to effective, applying post-conversions. */
    private void copyBaseToEffective() {
        registry.effectiveValues.putAll(registry.baseValues);
        GooConversionLoader.applyConversions(registry.postConversions,
                registry.effectiveValues, registry.pseudoTags);
    }

    /** Validates a JSON string for expression mistakes. */
    private List<String> validateJsonString(String jsonString) {
        List<String> warnings = new java.util.ArrayList<>();
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        GooValueValidator.validateJson(json, warnings);
        return warnings;
    }

    // ── LCD Derivation ──────────────────────────────────────────────────

    @Nested
    class LcdDerivation {

        /** A single recipe with all ingredients valued produces a derived value. */
        @Test
        void singleRecipeDerivesValue() {
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            assertEquals(0, registry.diagnostics().derivedSize());
        }

        /** Same total blobs, fewer goo types wins. */
        @Test
        void fewerGooTypesWinsTiebreak() {
            setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 16, GooType.ROCK, 8, GooType.CRYSTAL, 8),
                id("b"), goo(GooType.METAL, 16, GooType.ROCK, 16)
            ));
            // Both recipes cost 32 total blobs, but b has 2 types vs a's 3
            List<RecipeInput> recipes = List.of(
                recipe("x", 1, slot("a")),
                recipe("x", 1, slot("b"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("x"));
            assertNotNull(val);
            assertEquals(2, val.getAll().size(), "Should pick the 2-type recipe: " + val);
            assertEquals(16, val.get(GooType.METAL));
            assertEquals(16, val.get(GooType.ROCK));
        }

        /** When total blobs differ, cheaper still wins regardless of type count. */
        @Test
        void cheaperStillWinsOverFewerTypes() {
            setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 10, GooType.ROCK, 10, GooType.CRYSTAL, 10),
                id("b"), goo(GooType.METAL, 20, GooType.ROCK, 20)
            ));
            // a costs 30 (3 types), b costs 40 (2 types) -- a wins on total
            List<RecipeInput> recipes = List.of(
                recipe("x", 1, slot("a")),
                recipe("x", 1, slot("b"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.lookup(id("x"));
            assertNotNull(val);
            assertEquals(30, val.totalBlobs(), "Cheaper recipe wins even with more types");
            assertEquals(3, val.getAll().size());
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
            setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 10),
                id("b"), goo(GooType.METAL, 5)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b"), slot("b")) // cost 10 == base 10
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.diagnostics().conflicts().isEmpty());
        }

        /** Recipe cheaper than base is flagged as conflict. */
        @Test
        void recipeCheaperThanBaseIsConflict() {
            setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 20),
                id("b"), goo(GooType.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")) // cost 3 < base 20
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.diagnostics().conflicts().size());
            assertTrue(registry.diagnostics().conflicts().get(0).isRecipeCheaper());
        }

        /** Base cheaper than recipe is also flagged as conflict (value mismatch). */
        @Test
        void baseCheaperThanRecipeIsConflict() {
            setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 5),
                id("b"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")) // cost 10 > base 5
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.diagnostics().conflicts().size());
            assertFalse(registry.diagnostics().conflicts().get(0).isRecipeCheaper());
        }
    }

    // ── Effective Values ────────────────────────────────────────────────

    @Nested
    class EffectiveValues {

        /** Derived-only item: effective equals derived. */
        @Test
        void derivedOnlyItemUsesDerivation() {
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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

    @Nested
    class NegativeValueRejection {

        /** A derived value with negatives is excluded from effective values. */
        @Test
        void negativeDerivationExcludedFromEffective() {
            Map<GooType, Integer> negMap = new EnumMap<>(GooType.class);
            negMap.put(GooType.METAL, -10);
            negMap.put(GooType.AEON, 5);
            GooValue negValue = new GooValue(negMap);

            Map<Identifier, GooValue> base = Map.of();
            Map<Identifier, GooValue> derived = Map.of(id("bad_item"), negValue);

            Map<Identifier, GooValue> effective = GooValueDerivation.buildEffectiveValues(
                    base, derived, false);
            assertNull(effective.get(id("bad_item")),
                    "Items with negative goo types must not appear in effective values");
        }

        /** A base value with negatives is excluded from effective values. */
        @Test
        void negativeBaseExcludedFromEffective() {
            Map<GooType, Integer> negMap = new EnumMap<>(GooType.class);
            negMap.put(GooType.METAL, -10);
            GooValue negValue = new GooValue(negMap);

            Map<Identifier, GooValue> base = Map.of(id("bad_base"), negValue);
            Map<Identifier, GooValue> derived = Map.of();

            Map<Identifier, GooValue> effective = GooValueDerivation.buildEffectiveValues(
                    base, derived, false);
            assertNull(effective.get(id("bad_base")),
                    "Base items with negative goo types must not appear in effective values");
        }

        /** A valid derived value next to a rejected one still appears. */
        @Test
        void validItemSurvivesAlongsideRejected() {
            Map<GooType, Integer> negMap = new EnumMap<>(GooType.class);
            negMap.put(GooType.METAL, -10);
            GooValue negValue = new GooValue(negMap);
            GooValue goodValue = goo(GooType.ROCK, 50);

            Map<Identifier, GooValue> base = Map.of();
            Map<Identifier, GooValue> derived = Map.of(
                    id("bad_item"), negValue,
                    id("good_item"), goodValue);

            Map<Identifier, GooValue> effective = GooValueDerivation.buildEffectiveValues(
                    base, derived, false);
            assertNull(effective.get(id("bad_item")));
            assertEquals(50, effective.get(id("good_item")).get(GooType.ROCK));
        }
    }

    // ── Divisibility Loss ───────────────────────────────────────────────

    @Nested
    class DivisibilityLossDetection {

        /** Evenly divisible recipe produces no loss. */
        @Test
        void evenlyDivisibleNoLoss() {
            setBaseValues(Map.of(
                id("input"), goo(GooType.ROCK, 12)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 4, slot("input")) // 12 / 4 = 3, no remainder
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.diagnostics().divisibilityLosses().isEmpty());
        }

        /** Recipe with remainder detects loss with correct amounts. */
        @Test
        void remainderDetectsLoss() {
            setBaseValues(Map.of(
                id("input"), goo(GooType.ROCK, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 3, slot("input")) // 10 / 3 = 3 remainder 1
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.diagnostics().divisibilityLosses().size());
            var loss = registry.diagnostics().divisibilityLosses().get(0);
            assertEquals(10, loss.inputTotal());
            assertEquals(3, loss.outputCount());
            assertEquals(3, loss.perItemValue());
            assertEquals(1, loss.lostBlobs());
        }

        /** Result count 1 is never checked for divisibility loss. */
        @Test
        void resultCountOneSkipped() {
            setBaseValues(Map.of(
                id("input"), goo(GooType.ROCK, 7)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("output", 1, slot("input"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.diagnostics().divisibilityLosses().isEmpty());
        }
    }

    // ── Deny List ────────────────────────────────────────────────────────

    @Nested
    class DenyList {

        /** Denied items never receive a derived value, even with valid recipes. */
        @Test
        void deniedItemNeverDerives() {
            setBaseValues(Map.of(
                id("raw"), goo(GooType.METAL, 10)
            ));
            setDeniedItems(Set.of(id("ore_block")));
            List<RecipeInput> recipes = List.of(
                recipe("ore_block", 1, slot("raw"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertNull(registry.lookup(id("ore_block")));
        }

        /** Denied items can still be used as recipe inputs for other items. */
        @Test
        void deniedItemWorksAsInput() {
            setBaseValues(Map.of(
                id("ore_block"), goo(GooType.METAL, 20)
            ));
            setDeniedItems(Set.of(id("ore_block")));
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
            setBaseValues(Map.of(
                id("ore"), goo(GooType.METAL, 10)
            ));
            setDeniedItems(Set.of(id("ore")));
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of());
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")),
                recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.diagnostics().cycles();
            assertEquals(1, cycles.size());
            assertTrue(cycles.get(0).items().containsAll(List.of(id("a"), id("b"))));
        }

        /** Anchored cycle: one member has a base value. */
        @Test
        void anchoredCycleDetected() {
            setBaseValues(Map.of(
                id("a"), goo(GooType.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("a", 1, slot("b")),
                recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.diagnostics().cycles();
            assertEquals(1, cycles.size());
            assertTrue(cycles.get(0).hasAnchor());
            assertEquals(id("a"), cycles.get(0).anchor());
        }

        /** Non-cyclic chain produces no cycles. */
        @Test
        void noCyclesInLinearChain() {
            setBaseValues(Map.of(
                id("raw"), goo(GooType.ROCK, 5)
            ));
            List<RecipeInput> recipes = List.of(
                recipe("mid", 1, slot("raw")),
                recipe("final", 1, slot("mid"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.diagnostics().cycles().isEmpty());
        }

        /** Unanchored cycle: no member has a base value or is reachable from one. */
        @Test
        void unanchoredCycleDetected() {
            setBaseValues(Map.of());
            List<RecipeInput> recipes = List.of(
                recipe("x", 1, slot("y")),
                recipe("y", 1, slot("x"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.diagnostics().cycles();
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
            setBaseValues(Map.of(
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
            setBaseValues(Map.of(
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
            Identifier result = IGooValueLookup.findCheapestAmong(
                Set.of(id("a"), id("b"), id("c")), values::get);
            assertEquals(id("b"), result);
        }

        /** Null lookup results are skipped. */
        @Test
        void nullLookupSkipped() {
            Map<Identifier, GooValue> values = Map.of(
                id("known"), goo(GooType.METAL, 5)
            );
            Identifier result = IGooValueLookup.findCheapestAmong(
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
            Identifier result = IGooValueLookup.findCheapestAmong(
                Set.of(id("empty"), id("real")), values::get);
            assertEquals(id("real"), result);
        }

        /** Returns null if no candidate has a non-empty value. */
        @Test
        void allNullReturnsNull() {
            Identifier result = IGooValueLookup.findCheapestAmong(
                Set.of(id("a"), id("b")), id -> null);
            assertNull(result);
        }
    }

    // ── Group parsing ────────────────────────────────────────────────────

    @Nested
    class GroupParsing {

        /** Helper: parse a JSON string through the registry's base value loader. */
        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /** A _groups entry creates a pseudo-tag, #name assigns values. */
        @Test
        void groupCreatesPseudoTagForAssignment() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "logs": ["minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log"]
                    },
                    "#logs": { "leaf": 384 }
                }
                """);
            assertEquals(384, registry.lookup(id("minecraft:oak_log")).get(GooType.LEAF));
            assertEquals(384, registry.lookup(id("minecraft:spruce_log")).get(GooType.LEAF));
            assertEquals(384, registry.lookup(id("minecraft:birch_log")).get(GooType.LEAF));
        }

        /** A denied pseudo-tag marks all items as denied. */
        @Test
        void pseudoTagDeniedMarksAllItems() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "ores": ["minecraft:coal_ore", "minecraft:iron_ore"]
                    },
                    "#ores": "denied"
                }
                """);
            assertTrue(registry.isDenied(id("minecraft:coal_ore")));
            assertTrue(registry.isDenied(id("minecraft:iron_ore")));
        }

        /** Pseudo-tag values support $constant expressions. */
        @Test
        void pseudoTagValuesResolveConstants() throws IOException {
            loadJson("""
                {
                    "_constants": { "log": 384 },
                    "_groups": {
                        "logs": ["minecraft:oak_log", "minecraft:spruce_log"]
                    },
                    "#logs": { "leaf": "$log" }
                }
                """);
            assertEquals(384, registry.lookup(id("minecraft:oak_log")).get(GooType.LEAF));
            assertEquals(384, registry.lookup(id("minecraft:spruce_log")).get(GooType.LEAF));
        }

        /** Multiple groups all create pseudo-tags. */
        @Test
        void multipleGroupsAllParse() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "logs": ["minecraft:oak_log"],
                        "stones": ["minecraft:stone", "minecraft:cobblestone"]
                    },
                    "#logs": { "leaf": 384 },
                    "#stones": { "rock": 240 }
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
                        "logs": ["minecraft:oak_log"]
                    },
                    "#logs": { "leaf": 384 }
                }
                """);
            assertEquals(12000, registry.lookup(id("minecraft:diamond")).get(GooType.CRYSTAL));
            assertEquals(384, registry.lookup(id("minecraft:oak_log")).get(GooType.LEAF));
        }

        /** Parallel copy with scale: #wood = #logs * 3 / 4. */
        @Test
        void parallelCopyWithScaleInBaseValues() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "logs": ["minecraft:oak_log", "minecraft:birch_log"],
                        "wood": ["minecraft:oak_wood", "minecraft:birch_wood"]
                    },
                    "#logs": { "leaf": 480 },
                    "#wood": "#logs * 3 / 4"
                }
                """);
            assertEquals(360, registry.lookup(id("minecraft:oak_wood")).get(GooType.LEAF));
            assertEquals(360, registry.lookup(id("minecraft:birch_wood")).get(GooType.LEAF));
        }

        /** Parallel copy without scale: #stripped_logs = #logs (identity copy). */
        @Test
        void parallelCopyWithoutScale() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "logs": ["minecraft:oak_log", "minecraft:birch_log"],
                        "stripped": ["minecraft:stripped_oak_log", "minecraft:stripped_birch_log"]
                    },
                    "#logs": { "leaf": 480 },
                    "#stripped": "#logs"
                }
                """);
            assertEquals(480, registry.lookup(id("minecraft:stripped_oak_log")).get(GooType.LEAF));
            assertEquals(480, registry.lookup(id("minecraft:stripped_birch_log")).get(GooType.LEAF));
        }

        /** Parallel copy preserves multi-type values with scale. */
        @Test
        void parallelCopyMultiTypeWithScale() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "ingots": ["minecraft:iron_ingot"],
                        "nuggets": ["minecraft:iron_nugget"]
                    },
                    "#ingots": { "metal": 1728, "rock": 432 },
                    "#nuggets": "#ingots * 1 / 9"
                }
                """);
            GooValue nugget = registry.lookup(id("minecraft:iron_nugget"));
            assertEquals(192, nugget.get(GooType.METAL));  // 1728 / 9
            assertEquals(48, nugget.get(GooType.ROCK));     // 432 / 9
        }

        /** Unresolved pseudo-tag (no _groups entry, no MC tag) logs warning. */
        @Test
        void unresolvedPseudoTagSkipped() throws IOException {
            loadJson("""
                {
                    "#nonexistent": { "rock": 100 }
                }
                """);
            // No items assigned, no crash
            assertNull(registry.lookup(id("minecraft:nonexistent")));
        }
    }

    // ── Conversions ────────────────────────────────────────────────────

    @Nested
    class Conversions {

        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /** Single conversion applied to an individual item. */
        @Test
        void singleConversionOnItem() throws IOException {
            loadJson("""
                {
                    "minecraft:copper_ingot": { "metal": 160, "rock": 40 },
                    "_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "exposed": "@oxidation",
                        "minecraft:copper_ingot": "@exposed"
                    }
                }
                """);
            GooValue val = registry.lookup(id("minecraft:copper_ingot"));
            assertNotNull(val);
            assertEquals(120, val.get(GooType.METAL)); // 160 - 40
            assertEquals(20, val.get(GooType.AEON));    // 40 / 2
            assertEquals(40, val.get(GooType.ROCK));    // untouched
        }

        /** Stacked conversion: 2x oxidation. */
        @Test
        void stackedConversionOnItem() throws IOException {
            loadJson("""
                {
                    "minecraft:copper_ingot": { "metal": 160 },
                    "_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "weathered": "2 @oxidation",
                        "minecraft:copper_ingot": "@weathered"
                    }
                }
                """);
            GooValue val = registry.lookup(id("minecraft:copper_ingot"));
            assertNotNull(val);
            assertEquals(80, val.get(GooType.METAL));  // 160 - 2*40
            assertEquals(40, val.get(GooType.AEON));    // 2 * 20
        }

        /** Conversion applied to pseudo-tag group. */
        @Test
        void conversionOnPseudoTag() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "weathered_copper": [
                            "minecraft:weathered_copper",
                            "minecraft:weathered_cut_copper"
                        ]
                    },
                    "minecraft:weathered_copper": { "metal": 160 },
                    "minecraft:weathered_cut_copper": { "metal": 160 },
                    "_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "weathered": "2 @oxidation",
                        "#weathered_copper": "@weathered"
                    }
                }
                """);
            GooValue copper = registry.lookup(id("minecraft:weathered_copper"));
            GooValue cut = registry.lookup(id("minecraft:weathered_cut_copper"));
            assertNotNull(copper);
            assertNotNull(cut);
            assertEquals(80, copper.get(GooType.METAL));
            assertEquals(40, copper.get(GooType.AEON));
            assertEquals(80, cut.get(GooType.METAL));
            assertEquals(40, cut.get(GooType.AEON));
        }

        /** 3x oxidation (oxidized). */
        @Test
        void tripleStackConversion() throws IOException {
            loadJson("""
                {
                    "minecraft:copper_ingot": { "metal": 160 },
                    "_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "oxidized": "3 @oxidation",
                        "minecraft:copper_ingot": "@oxidized"
                    }
                }
                """);
            GooValue val = registry.lookup(id("minecraft:copper_ingot"));
            assertNotNull(val);
            assertEquals(40, val.get(GooType.METAL));   // 160 - 3*40
            assertEquals(60, val.get(GooType.AEON));     // 3 * 20
        }

        /** Pre-derivation conversions propagate through recipes. */
        @Test
        void preConversionPropagatesThroughRecipes() throws IOException {
            parseBaseValuesFromStream(
                new ByteArrayInputStream("""
                {
                    "minecraft:exposed_copper": { "metal": 160 },
                    "_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "minecraft:exposed_copper": "@oxidation"
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)));
            // Pre-conversion modifies base: exposed_copper = {metal: 120, aeon: 20}
            // Recipe derives a slab from it
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:exposed_copper_slab", 2, slot("minecraft:exposed_copper"))
            );
            registry.deriveFromRecipeInputs(recipes, false);
            GooValue slab = registry.lookup(id("minecraft:exposed_copper_slab"));
            assertNotNull(slab);
            assertEquals(60, slab.get(GooType.METAL));  // 120 / 2
            assertEquals(10, slab.get(GooType.AEON));    // 20 / 2
        }

        /** Post-derivation conversions modify effective values after recipes. */
        @Test
        void postConversionAppliesAfterDerivation() throws IOException {
            parseBaseValuesFromStream(
                new ByteArrayInputStream("""
                {
                    "minecraft:copper_ingot": { "metal": 160 },
                    "_post_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "minecraft:copper_ingot": "@oxidation"
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)));
            // Post-conversion: base value is unchanged (metal: 160)
            // Recipes derive from unmodified base
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_block", 1,
                    slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                    slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                    slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                    slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                    slot("minecraft:copper_ingot"))
            );
            registry.deriveFromRecipeInputs(recipes, false);
            // copper_block derived from unmodified ingot: 9 * 160 = 1440 metal
            GooValue block = registry.lookup(id("minecraft:copper_block"));
            assertNotNull(block);
            assertEquals(1440, block.get(GooType.METAL));
            // But copper_ingot itself got post-converted
            GooValue ingot = registry.lookup(id("minecraft:copper_ingot"));
            assertNotNull(ingot);
            assertEquals(120, ingot.get(GooType.METAL)); // 160 - 40
            assertEquals(20, ingot.get(GooType.AEON));    // 40 / 2
        }

        /** Parallel copy from source tag + conversion chain in post_conversions. */
        @Test
        void parallelCopyWithConversionChain() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "originals": ["minecraft:copper_block", "minecraft:cut_copper"],
                        "exposed": ["minecraft:exposed_copper", "minecraft:exposed_cut_copper"]
                    },
                    "minecraft:copper_block": { "metal": 160 },
                    "minecraft:cut_copper": { "metal": 80 },
                    "_post_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "exposed": "@oxidation",
                        "#exposed": "#originals @exposed"
                    }
                }
                """);
            // exposed_copper gets copper_block's value (160 metal), then 1x oxidation
            GooValue exposed = registry.lookup(id("minecraft:exposed_copper"));
            assertNotNull(exposed);
            assertEquals(120, exposed.get(GooType.METAL)); // 160 - 40
            assertEquals(20, exposed.get(GooType.AEON));    // 40 / 2

            // exposed_cut_copper gets cut_copper's value (80 metal), then 1x oxidation
            GooValue exposedCut = registry.lookup(id("minecraft:exposed_cut_copper"));
            assertNotNull(exposedCut);
            assertEquals(60, exposedCut.get(GooType.METAL)); // 80 - 20
            assertEquals(10, exposedCut.get(GooType.AEON));   // 20 / 2
        }

        /** Chained parallel copy: weathered copies from exposed (already converted). */
        @Test
        void chainedParallelCopy() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "originals": ["minecraft:copper_block"],
                        "exposed": ["minecraft:exposed_copper"],
                        "weathered": ["minecraft:weathered_copper"]
                    },
                    "minecraft:copper_block": { "metal": 160 },
                    "_post_conversions": {
                        "oxidation": "metal / 4 -> aeon / 2",
                        "exposed_conv": "@oxidation",
                        "weathered_conv": "2 @oxidation",
                        "#exposed": "#originals @exposed_conv",
                        "#weathered": "#exposed @weathered_conv"
                    }
                }
                """);
            // exposed_copper: copy 160 metal, 1x oxidation -> 120 metal, 20 aeon
            GooValue exposed = registry.lookup(id("minecraft:exposed_copper"));
            assertNotNull(exposed);
            assertEquals(120, exposed.get(GooType.METAL));
            assertEquals(20, exposed.get(GooType.AEON));

            // weathered_copper: copy from exposed (120 metal, 20 aeon), 2x oxidation
            // 2 * (120/4) = 60 removed, 2 * (120/4/2) = 30 added
            GooValue weathered = registry.lookup(id("minecraft:weathered_copper"));
            assertNotNull(weathered);
            assertEquals(60, weathered.get(GooType.METAL));  // 120 - 60
            assertEquals(50, weathered.get(GooType.AEON));    // 20 + 30
        }

        /** Additive conversion: +$waxed adds flat vital to copied copper items. */
        @Test
        void additiveConversionAddsFlatValue() throws IOException {
            loadJson("""
                {
                    "_constants": { "waxed": { "vital": 48 } },
                    "_groups": {
                        "copper": ["minecraft:copper_block", "minecraft:cut_copper"],
                        "waxed": ["minecraft:waxed_copper_block", "minecraft:waxed_cut_copper"]
                    },
                    "minecraft:copper_block": { "metal": 160 },
                    "minecraft:cut_copper": { "metal": 80 },
                    "_post_conversions": {
                        "waxed": "+$waxed",
                        "#waxed": "#copper @waxed"
                    }
                }
                """);
            GooValue waxedBlock = registry.lookup(id("minecraft:waxed_copper_block"));
            assertNotNull(waxedBlock);
            assertEquals(160, waxedBlock.get(GooType.METAL)); // copied from copper_block
            assertEquals(48, waxedBlock.get(GooType.VITAL));   // added by +$waxed

            GooValue waxedCut = registry.lookup(id("minecraft:waxed_cut_copper"));
            assertNotNull(waxedCut);
            assertEquals(80, waxedCut.get(GooType.METAL));
            assertEquals(48, waxedCut.get(GooType.VITAL));
        }
    }

    // ── Item reference expressions ───────────────────────────────────────

    @Nested
    class ItemReferenceExpressions {

        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /** String constant referencing a tree constant promotes to tree. */
        @Test
        void stringConstantReferencingTreePromotes() throws IOException {
            loadJson("""
                {
                    "_constants": {
                        "base": 48,
                        "nugget": { "metal": "$base" },
                        "ingot": "9 $nugget",
                        "block": "9 $ingot"
                    },
                    "minecraft:iron_block": "$block"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:iron_block"));
            assertNotNull(val, "iron_block should have a value");
            // nugget = {metal: 48}, ingot = 9 * {metal: 48} = {metal: 432}
            // block = 9 * {metal: 432} = {metal: 3888}
            assertEquals(3888, val.get(GooType.METAL));
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

        /** Unary minus on scalar: "-2 $base" negates the multiplier. */
        @Test
        void unaryMinusScalarTimesTreeConstant() throws IOException {
            loadJson("""
                {
                    "_constants": { "base": { "metal": 100 } },
                    "minecraft:copper_ingot": { "metal": 160 },
                    "minecraft:exposed_copper": "copper_ingot + -2 $base"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:exposed_copper"));
            // 160 + (-2 * 100) = -40 metal; negative -> excluded by effective value guard
            // But the interstitial expression itself should produce -40
            // Since effective values reject negatives, exposed_copper won't appear
            assertNull(registry.lookup(id("minecraft:exposed_copper")),
                    "Negative final value should be rejected from effective values");
        }

        /** Unary minus on a tree constant: "-$bonus" negates all types. */
        @Test
        void unaryMinusOnTreeConstant() throws IOException {
            loadJson("""
                {
                    "_constants": { "drain": { "metal": 64 } },
                    "minecraft:copper_ingot": { "metal": 200, "aeon": 30 },
                    "minecraft:exposed_copper": "copper_ingot + -$drain"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:exposed_copper"));
            assertNotNull(val);
            assertEquals(136, val.get(GooType.METAL)); // 200 - 64
            assertEquals(30, val.get(GooType.AEON));   // unchanged
        }

        /** Unary minus combined with addition: tree constant with mixed signs. */
        @Test
        void treeConstantWithNegativeType() throws IOException {
            loadJson("""
                {
                    "_constants": { "exposed": { "aeon": 32, "metal": -64 } },
                    "minecraft:copper_ingot": { "metal": 200, "rock": 50 },
                    "minecraft:exposed_copper": "copper_ingot + $exposed"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:exposed_copper"));
            assertNotNull(val);
            assertEquals(136, val.get(GooType.METAL)); // 200 - 64
            assertEquals(32, val.get(GooType.AEON));   // 0 + 32
            assertEquals(50, val.get(GooType.ROCK));   // unchanged
        }

        /** Tree constant dot notation in item-level expression: produces single-type GooValue. */
        @Test
        void treeConstantDotInItemExpression() throws IOException {
            loadJson("""
                {
                    "_constants": { "log": { "leaf": 480 } },
                    "minecraft:oak_wood": "$log.leaf * 3 / 4"
                }
                """);
            GooValue val = registry.lookup(id("minecraft:oak_wood"));
            assertNotNull(val);
            assertEquals(360, val.get(GooType.LEAF)); // 480 * 3 / 4
            assertEquals(1, val.typeCount()); // only leaf, not the full tree
        }

        /** Tree constant dot notation in per-type expression: resolves to scalar int. */
        @Test
        void treeConstantDotInPerTypeExpression() throws IOException {
            loadJson("""
                {
                    "_constants": { "log": { "leaf": 480 } },
                    "minecraft:stem": { "leaf": "$log.leaf" }
                }
                """);
            GooValue val = registry.lookup(id("minecraft:stem"));
            assertNotNull(val);
            assertEquals(480, val.get(GooType.LEAF));
        }

        /** Tree constant dot notation with arithmetic in per-type context. */
        @Test
        void treeConstantDotWithArithmeticPerType() throws IOException {
            loadJson("""
                {
                    "_constants": { "log": { "leaf": 480 } },
                    "minecraft:oak_wood": { "leaf": "$log.leaf * 3 / 4" }
                }
                """);
            GooValue val = registry.lookup(id("minecraft:oak_wood"));
            assertNotNull(val);
            assertEquals(360, val.get(GooType.LEAF));
        }

        /** Binary subtraction still works after unary minus support. */
        @Test
        void binarySubtractionStillWorks() throws IOException {
            loadJson("""
                {
                    "minecraft:iron_block": { "metal": 90 },
                    "minecraft:iron_ingot": { "metal": 10 },
                    "minecraft:scrap": "iron_block - 2 iron_ingot"
                }
                """);
            assertEquals(70, registry.lookup(id("minecraft:scrap")).get(GooType.METAL));
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
            assertEquals(0, registry.diagnostics().baseSize());
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

            setBaseValues(Map.of(
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
            List<String> warnings = validateJsonString("""
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
            List<String> warnings = validateJsonString("""
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
            List<String> warnings = validateJsonString("""
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
            List<String> warnings = validateJsonString("""
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
            List<String> warnings = validateJsonString("""
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
            List<String> warnings = validateJsonString("""
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
            List<String> warnings = validateJsonString("""
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
            List<String> warnings = validateJsonString("""
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
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

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
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

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
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

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
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

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
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(layer));

            assertEquals(1000, merged.getAsJsonObject("_constants").get("base").getAsInt());
            assertEquals(5, merged.getAsJsonObject("minecraft:stick").get("vital").getAsInt());
        }

        /** An empty layer list produces an empty JsonObject. */
        @Test
        void emptyLayerListProducesEmptyObject() {
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of());

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
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

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

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

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

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

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

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

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

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertEquals(100, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(50, result.getAsJsonObject("minecraft:iron_ore").get("rock").getAsInt());
        }

        /** An unknown tag (empty resolver result) adds no entries and does not crash. */
        @Test
        void unknownTagPreservedForPseudoTagResolution() {
            JsonObject input = json("""
                { "#test:nonexistent": { "leaf": 100 } }
                """);

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(Map.of()));

            // Unresolved MC tags are preserved so pseudo-tag resolution can try them
            assertEquals(1, result.size());
            assertTrue(result.has("#test:nonexistent"));
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

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

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

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertTrue(result.has("_constants"));
            assertTrue(result.has("_groups"));
            assertEquals(10, result.getAsJsonObject("_constants").get("iron").getAsInt());
            assertTrue(result.has("minecraft:oak_planks"));
            assertFalse(result.has("#test:planks"));
        }
    }

    // ── Restricted items ───────────────────────────────────────────────

    @Nested
    class RestrictedItems {

        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /** Items listed in _restricted are flagged but still have values. */
        @Test
        void restrictedItemKeepsValueButIsFlagged() throws IOException {
            loadJson("""
                {
                    "minecraft:coal_ore": { "rock": 100 },
                    "_restricted": ["minecraft:coal_ore"]
                }
                """);
            assertTrue(registry.isRestricted(id("minecraft:coal_ore")));
            assertNotNull(registry.lookup(id("minecraft:coal_ore")));
            assertEquals(100, registry.lookup(id("minecraft:coal_ore")).get(GooType.ROCK));
        }

        /** Group references in _restricted expand to all members. */
        @Test
        void restrictedGroupExpandsToMembers() throws IOException {
            loadJson("""
                {
                    "_groups": {
                        "ores": ["minecraft:coal_ore", "minecraft:iron_ore"]
                    },
                    "#ores": { "rock": 100 },
                    "_restricted": ["#ores"]
                }
                """);
            assertTrue(registry.isRestricted(id("minecraft:coal_ore")));
            assertTrue(registry.isRestricted(id("minecraft:iron_ore")));
            assertNotNull(registry.lookup(id("minecraft:coal_ore")));
        }

        /** Items not in _restricted are not flagged. */
        @Test
        void nonRestrictedItemIsNotFlagged() throws IOException {
            loadJson("""
                {
                    "minecraft:coal_ore": { "rock": 100 },
                    "minecraft:stick": { "leaf": 50 },
                    "_restricted": ["minecraft:coal_ore"]
                }
                """);
            assertFalse(registry.isRestricted(id("minecraft:stick")));
        }

        /** Absent _restricted section means nothing is restricted. */
        @Test
        void noRestrictedSectionMeansNothingRestricted() throws IOException {
            loadJson("""
                {
                    "minecraft:coal_ore": { "rock": 100 }
                }
                """);
            assertFalse(registry.isRestricted(id("minecraft:coal_ore")));
        }
    }
}
