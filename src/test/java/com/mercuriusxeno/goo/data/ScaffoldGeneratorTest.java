package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.mercuriusxeno.goo.data.TestRecipeBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScaffoldGenerator root-finding and scaffold output.
 */
class ScaffoldGeneratorTest {

    // ── Root finding ─────────────────────────────────────────────────────

    @Nested
    class RootFinding {

        /** An unvalued item with no recipe that feeds into recipes is a root. */
        @Test
        void unvaluedInputWithNoRecipeIsRoot() {
            // raw_copper has no recipe, copper_ingot is smelted from it
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper"))
            );
            Map<Identifier, GooValue> baseValues = Map.of();

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, baseValues, Set.of());

            assertTrue(roots.stream().anyMatch(r ->
                    r.itemId().equals(id("minecraft:raw_copper"))),
                    "raw_copper should be identified as a root: " + roots);
        }

        /** An already-valued item is not a root. */
        @Test
        void valuedItemIsNotRoot() {
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper"))
            );
            Map<Identifier, GooValue> baseValues = Map.of(
                id("minecraft:raw_copper"), goo(GooType.METAL, 100)
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, baseValues, Set.of());

            assertTrue(roots.stream().noneMatch(r ->
                    r.itemId().equals(id("minecraft:raw_copper"))),
                    "raw_copper should NOT be a root when it has a value");
        }

        /** Roots are sorted by downstream impact (most unblocked items first). */
        @Test
        void rootsSortedByDownstreamImpact() {
            // raw_iron -> iron_ingot -> iron_block -> heavy_weighted_pressure_plate (3 downstream)
            // raw_gold -> gold_ingot (1 downstream)
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:iron_ingot", 1, slot("minecraft:raw_iron")),
                recipe("minecraft:iron_block", 1, slot("minecraft:iron_ingot")),
                recipe("minecraft:heavy_weighted_pressure_plate", 1, slot("minecraft:iron_block")),
                recipe("minecraft:gold_ingot", 1, slot("minecraft:raw_gold"))
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());

            // raw_iron should come first (more downstream)
            assertFalse(roots.isEmpty());
            assertEquals(id("minecraft:raw_iron"), roots.get(0).itemId());
        }

        /** A chain where the intermediate is valued doesn't need the leaf root. */
        @Test
        void chainWithValuedIntermediateDoesNotNeedLeafRoot() {
            // raw_copper -> copper_ingot -> copper_block
            // If copper_ingot is already valued, raw_copper isn't needed as a root
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper")),
                recipe("minecraft:copper_block", 1, slot("minecraft:copper_ingot"))
            );
            Map<Identifier, GooValue> baseValues = Map.of(
                id("minecraft:copper_ingot"), goo(GooType.METAL, 100)
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, baseValues, Set.of());

            // raw_copper has no downstream that isn't already derivable
            assertTrue(roots.stream().noneMatch(r ->
                    r.itemId().equals(id("minecraft:raw_copper"))),
                    "raw_copper should not be a root when copper_ingot is valued");
        }

        /** With zero base values, one root per cluster, preferring most-multiplied hub. */
        @Test
        void emptyBaseValuesFindsOneRootPerCluster() {
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper")),
                recipe("minecraft:copper_block", 9, slot("minecraft:copper_ingot")),
                recipe("minecraft:iron_ingot", 1, slot("minecraft:raw_iron")),
                recipe("minecraft:iron_block", 9, slot("minecraft:iron_ingot")),
                recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                recipe("minecraft:stick", 4, slot("minecraft:oak_planks")),
                recipe("minecraft:ladder", 3, slot("minecraft:stick")),
                recipe("minecraft:oak_fence", 1, slot("minecraft:stick"), slot("minecraft:oak_planks"))
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());

            // 3 clusters: {raw_copper, copper_ingot, copper_block},
            //             {raw_iron, iron_ingot, iron_block},
            //             {oak_log, oak_planks, stick, ladder}
            // stick is 16x from log, most multiplied with fan-out to ladder/fence
            assertEquals(3, roots.size(), "One root per cluster: " + roots);
            assertTrue(roots.stream().anyMatch(r -> r.itemId().equals(id("minecraft:stick"))),
                    "stick should be the wood cluster root: " + roots);
        }

        /** Homogenous chain picks the widest hub, not the deepest recipeless item. */
        @Test
        void homogenousChainPicksWidestHub() {
            // log -> 4 planks -> 4 sticks (all homogenous single-input recipes)
            // stick -> fence, stick -> ladder, stick -> sign (stick has most fan-out)
            // Valuing stick reverse-derives planks and log, so stick is the best anchor.
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                recipe("minecraft:stick", 4, slot("minecraft:oak_planks")),
                recipe("minecraft:oak_fence", 1, slot("minecraft:stick"), slot("minecraft:oak_planks")),
                recipe("minecraft:ladder", 3, slot("minecraft:stick")),
                recipe("minecraft:oak_sign", 1, slot("minecraft:stick"), slot("minecraft:oak_planks"))
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());

            // Should suggest ONE root for the whole wood cluster, and it should be stick
            ScaffoldGenerator.Root woodRoot = roots.stream()
                    .filter(r -> r.itemId().equals(id("minecraft:stick"))
                            || r.itemId().equals(id("minecraft:oak_log"))
                            || r.itemId().equals(id("minecraft:oak_planks")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected a wood root in: " + roots.stream().map(r -> r.itemId() + "(" + r.reason() + ")").toList()));
            assertEquals(id("minecraft:stick"), woodRoot.itemId(),
                    "Should pick stick (widest fan-out) as the cluster entry, not log: " + roots);
        }

        /** Multi-variant tag recipes don't merge clusters (can't reverse-derive). */
        @Test
        void multiVariantRecipeDoesNotMergeClusters() {
            // stick recipe accepts any planks -- but you can't reverse-derive
            // which specific plank type from the stick value, so each wood type
            // stays its own cluster.
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                recipe("minecraft:birch_planks", 4, slot("minecraft:birch_log")),
                recipe("minecraft:stick", 4,
                    slot("minecraft:oak_planks", "minecraft:birch_planks"),
                    slot("minecraft:oak_planks", "minecraft:birch_planks")),
                recipe("minecraft:ladder", 3, slot("minecraft:stick"))
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());

            // Each wood type is a separate cluster; stick is a separate cluster
            assertTrue(roots.size() >= 2,
                    "Oak and birch should be separate clusters: " + roots);
        }

        /** Homogenous-input recipes propagate value in reverse. */
        @Test
        void homogenousRecipeReversePropagates() {
            // 1 log -> 4 planks (homogenous: single input type)
            // 2 planks -> 4 sticks (homogenous: single input type)
            // Valuing stick -> derives planks (reverse) -> derives log (reverse)
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                recipe("minecraft:stick", 4, slot("minecraft:oak_planks"))
            );
            Map<Identifier, GooValue> baseValues = Map.of(
                id("minecraft:stick"), goo(GooType.VITAL, 10)
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, baseValues, Set.of());

            assertTrue(roots.isEmpty(),
                    "No roots expected -- log and planks derivable via reverse propagation: " + roots);
        }

        /** Mixed-input recipes do NOT reverse-propagate from the output. */
        @Test
        void mixedInputRecipeDoesNotReversePropagateFromOutput() {
            // stick + coal -> torch (mixed), stick -> ladder (homogenous)
            // Valuing torch alone can't reverse-derive stick or coal.
            // So ladder remains underivable and stick should be a root.
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:torch", 4, slot("minecraft:stick"), slot("minecraft:coal")),
                recipe("minecraft:ladder", 3, slot("minecraft:stick"))
            );
            Map<Identifier, GooValue> baseValues = Map.of(
                id("minecraft:torch"), goo(GooType.VITAL, 10)
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, baseValues, Set.of());

            // stick should still be a root -- torch's mixed recipe can't reverse-derive it
            assertTrue(roots.stream().anyMatch(r -> r.itemId().equals(id("minecraft:stick"))),
                    "stick should be a root (mixed recipe not reversible): " + roots);
        }

        /** Mixed-input recipes still derive forward when all inputs are valued. */
        @Test
        void mixedInputRecipeStillDerivesForward() {
            // 1 stick + 1 coal -> 4 torches
            // Both inputs valued -> torch is derivable (normal forward propagation)
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:torch", 4, slot("minecraft:stick"), slot("minecraft:coal"))
            );
            Map<Identifier, GooValue> baseValues = Map.of(
                id("minecraft:stick"), goo(GooType.VITAL, 10),
                id("minecraft:coal"), goo(GooType.ROCK, 20)
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, baseValues, Set.of());

            assertTrue(roots.isEmpty(),
                    "No roots expected -- torch derivable forward from valued inputs: " + roots);
        }

        /** Denied items are not picked as the cluster entry point. */
        @Test
        void deniedItemNotPickedAsEntry() {
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper")),
                recipe("minecraft:copper_block", 9, slot("minecraft:copper_ingot"))
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of(id("minecraft:raw_copper")));

            // raw_copper is denied, so copper_block (most multiplied with fan-out)
            // or copper_ingot should be picked instead
            assertTrue(roots.stream().noneMatch(r ->
                    r.itemId().equals(id("minecraft:raw_copper"))),
                    "Denied items should not be the cluster entry: " + roots);
        }
    }

    // ── Scaffold output ──────────────────────────────────────────────────

    @Nested
    class ScaffoldOutput {

        /** Empty roots produce a "no roots" message. */
        @Test
        void emptyRootsMessage() {
            ScaffoldGenerator.ScaffoldResult result = ScaffoldGenerator.generateScaffold(List.of());
            assertEquals(0, result.rootCount());
            assertTrue(result.lines().stream().anyMatch(l -> l.contains("No unvalued roots")));
        }

        /** Scaffold entries are in JSON-pasteable format. */
        @Test
        void entriesArePasteable() {
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper"))
            );
            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());
            ScaffoldGenerator.ScaffoldResult result = ScaffoldGenerator.generateScaffold(roots);

            assertEquals(1, result.rootCount());
            assertTrue(result.lines().stream().anyMatch(l ->
                    l.contains("\"raw_copper\"") && l.contains("{ }")),
                    "Should contain pasteable JSON entry: " + result.lines());
        }
    }

    // ── Tag grouping ────────────────────────────────────────────────────

    @Nested
    class TagGrouping {

        /** Roots sharing a tag are grouped into a single "#tag" scaffold entry. */
        @Test
        void rootsSharingTagGroupedInScaffold() {
            // Three plank types are roots (no recipe produces them here).
            // A stick recipe has a tag-backed slot containing all three planks.
            RecipeInput stickRecipe = tagRecipe(
                    "minecraft:stick", 4,
                    Arrays.asList("minecraft:planks", "minecraft:planks"),
                    slot("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:spruce_planks"),
                    slot("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:spruce_planks"));

            // ladder uses sticks (gives sticks downstream)
            RecipeInput ladderRecipe = recipe("minecraft:ladder", 3, slot("minecraft:stick"));

            List<RecipeInput> recipes = List.of(stickRecipe, ladderRecipe);
            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());

            ScaffoldGenerator.ScaffoldResult result = ScaffoldGenerator.generateScaffold(roots, recipes);

            String joined = String.join("\n", result.lines());
            assertTrue(joined.contains("\"#planks\""),
                    "Should contain grouped tag entry #planks: " + joined);
            assertFalse(joined.contains("\"oak_planks\""),
                    "Individual plank entries should be replaced by tag group: " + joined);
            assertFalse(joined.contains("\"birch_planks\""),
                    "Individual plank entries should be replaced by tag group: " + joined);
            assertFalse(joined.contains("\"spruce_planks\""),
                    "Individual plank entries should be replaced by tag group: " + joined);
        }

        /** Scaffold entries are sorted by unblock count, not insertion order. */
        @Test
        void scaffoldEntriesSortedByUnblockCount() {
            // Small root: glass_pane -> red_stained_glass_pane (1 downstream)
            RecipeInput redPane = recipe("minecraft:red_stained_glass_pane", 1,
                    slot("minecraft:glass_pane"), slot("minecraft:red_dye"));

            // Large tag group: 3 log types each feeding into many recipes
            // We need the tag group's union downstream to exceed glass_pane's
            RecipeInput oakPlanks = tagRecipe("minecraft:oak_planks", 4,
                    Arrays.asList("minecraft:logs"),
                    slot("minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log"));
            RecipeInput stick = recipe("minecraft:stick", 4, slot("minecraft:oak_planks"));
            RecipeInput ladder = recipe("minecraft:ladder", 3, slot("minecraft:stick"));
            RecipeInput fence = recipe("minecraft:oak_fence", 1,
                    slot("minecraft:stick"), slot("minecraft:oak_planks"));
            RecipeInput sign = recipe("minecraft:oak_sign", 1,
                    slot("minecraft:stick"), slot("minecraft:oak_planks"));

            List<RecipeInput> recipes = List.of(redPane, oakPlanks, stick, ladder, fence, sign);
            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());
            ScaffoldGenerator.ScaffoldResult result = ScaffoldGenerator.generateScaffold(roots, recipes);

            String joined = String.join("\n", result.lines());
            // The tag group (#logs) unblocks more items than glass_pane,
            // so it must appear first in the scaffold output.
            int logsPos = joined.indexOf("#logs");
            int panePos = joined.indexOf("glass_pane");
            assertTrue(logsPos >= 0, "Expected #logs entry: " + joined);
            assertTrue(panePos >= 0, "Expected glass_pane entry: " + joined);
            assertTrue(logsPos < panePos,
                    "Higher-impact #logs should appear before glass_pane: " + joined);
        }

        /** Roots without any tag association stay as individual entries. */
        @Test
        void rootsWithoutTagStayIndividual() {
            List<RecipeInput> recipes = List.of(
                recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper")),
                recipe("minecraft:iron_ingot", 1, slot("minecraft:raw_iron"))
            );

            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());
            ScaffoldGenerator.ScaffoldResult result = ScaffoldGenerator.generateScaffold(roots, recipes);

            String joined = String.join("\n", result.lines());
            assertTrue(joined.contains("\"raw_copper\""),
                    "raw_copper should be an individual entry: " + joined);
            assertTrue(joined.contains("\"raw_iron\""),
                    "raw_iron should be an individual entry: " + joined);
            assertFalse(joined.contains("\"#"),
                    "No tag groups expected: " + joined);
        }

        /** Mixed tag and non-tag roots: tag roots grouped, non-tag roots individual. */
        @Test
        void mixedTagAndNonTagRoots() {
            // Planks share a tag via the stick recipe
            RecipeInput stickRecipe = tagRecipe(
                    "minecraft:stick", 4,
                    Arrays.asList("minecraft:planks", "minecraft:planks"),
                    slot("minecraft:oak_planks", "minecraft:birch_planks"),
                    slot("minecraft:oak_planks", "minecraft:birch_planks"));
            RecipeInput ladderRecipe = recipe("minecraft:ladder", 3, slot("minecraft:stick"));

            // raw_copper is an unrelated root with no tag
            RecipeInput ingotRecipe = recipe("minecraft:copper_ingot", 1, slot("minecraft:raw_copper"));

            List<RecipeInput> recipes = List.of(stickRecipe, ladderRecipe, ingotRecipe);
            List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                    recipes, Map.of(), Set.of());
            ScaffoldGenerator.ScaffoldResult result = ScaffoldGenerator.generateScaffold(roots, recipes);

            String joined = String.join("\n", result.lines());
            assertTrue(joined.contains("\"#planks\""),
                    "Planks should be grouped by tag: " + joined);
            assertTrue(joined.contains("\"raw_copper\""),
                    "raw_copper should stay individual: " + joined);
        }
    }

    // ── RecipeInput tag fields ──────────────────────────────────────────

    @Nested
    class RecipeInputTags {

        /** RecipeInput preserves per-slot tag IDs through the canonical constructor. */
        @Test
        void recipeInputPreservesTagIds() {
            RecipeInput input = tagRecipe(
                    "minecraft:stick", 4,
                    Arrays.asList("minecraft:planks", null),
                    slot("minecraft:oak_planks", "minecraft:birch_planks"),
                    slot("minecraft:oak_planks"));

            assertEquals(2, input.slotTagIds().size());
            assertEquals(Optional.of(id("minecraft:planks")), input.slotTagIds().get(0));
            assertEquals(Optional.empty(), input.slotTagIds().get(1));
        }

        /** The 3-arg constructor defaults slotTagIds to empty optionals. */
        @Test
        void threeArgConstructorDefaultsTagIds() {
            RecipeInput input = recipe("minecraft:stick", 4,
                    slot("minecraft:oak_planks"), slot("minecraft:birch_planks"));

            assertEquals(2, input.slotTagIds().size());
            assertTrue(input.slotTagIds().stream().allMatch(Optional::isEmpty));
        }

        /** The 4-arg constructor defaults slotTagIds to empty optionals. */
        @Test
        void fourArgConstructorDefaultsTagIds() {
            RecipeInput input = new RecipeInput(
                    id("minecraft:stick"), 4,
                    List.of(Set.of(id("minecraft:oak_planks"))),
                    Map.of());

            assertEquals(1, input.slotTagIds().size());
            assertTrue(input.slotTagIds().get(0).isEmpty());
        }
    }
}
