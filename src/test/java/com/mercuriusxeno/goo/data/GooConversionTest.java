package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.id;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GooConversion system: formula parsing, stacking, and application.
 */
class GooConversionTest {

    // ── Formula parsing ──────────────────────────────────────────────────

    @Nested
    class FormulaParsing {

        /** Basic formula: "metal / 4 -> aeon / 2". */
        @Test
        void parseBasicFormula() {
            GooConversion.Formula f = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            assertEquals(GooType.METAL, f.sourceType());
            assertEquals(4, f.sourceDivisor());
            assertEquals(GooType.AEON, f.targetType());
            assertEquals(2, f.targetDivisor());
        }

        /** Formula with divisor of 1: "vital / 1 -> nether / 2". */
        @Test
        void parseDivisorOfOne() {
            GooConversion.Formula f = GooConversion.parseFormula("vital / 1 -> nether / 2");
            assertEquals(GooType.VITAL, f.sourceType());
            assertEquals(1, f.sourceDivisor());
            assertEquals(GooType.NETHER, f.targetType());
            assertEquals(2, f.targetDivisor());
        }

        /** Invalid formula throws. */
        @Test
        void invalidFormulaThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> GooConversion.parseFormula("not a formula"));
        }
    }

    // ── Application math ─────────────────────────────────────────────────

    @Nested
    class ApplicationMath {

        /** 1x oxidation on metal=160: removes 40, adds 20 aeon. */
        @Test
        void singleApplication() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooType.METAL, 160);

            GooValue result = GooConversion.apply(copper, oxidation, 1);
            assertEquals(120, result.get(GooType.METAL)); // 160 - 40
            assertEquals(20, result.get(GooType.AEON));    // 40 / 2
        }

        /** 2x oxidation on metal=160: removes 80, adds 40 aeon (simultaneous). */
        @Test
        void doubleApplicationSimultaneous() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooType.METAL, 160);

            GooValue result = GooConversion.apply(copper, oxidation, 2);
            assertEquals(80, result.get(GooType.METAL));  // 160 - 2*40
            assertEquals(40, result.get(GooType.AEON));    // 2 * (40/2)
        }

        /** 3x oxidation on metal=160: removes 120, adds 60 aeon. */
        @Test
        void tripleApplicationSimultaneous() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooType.METAL, 160);

            GooValue result = GooConversion.apply(copper, oxidation, 3);
            assertEquals(40, result.get(GooType.METAL));  // 160 - 3*40
            assertEquals(60, result.get(GooType.AEON));    // 3 * (40/2)
        }

        /** Conversion preserves other goo types untouched. */
        @Test
        void otherTypesPreserved() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooType.METAL, 160, GooType.ROCK, 50);

            GooValue result = GooConversion.apply(copper, oxidation, 1);
            assertEquals(120, result.get(GooType.METAL));
            assertEquals(20, result.get(GooType.AEON));
            assertEquals(50, result.get(GooType.ROCK)); // untouched
        }

        /** Conversion on an item missing the source type is a no-op. */
        @Test
        void missingSourceTypeNoOp() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue noMetal = goo(GooType.ROCK, 100);

            GooValue result = GooConversion.apply(noMetal, oxidation, 1);
            assertEquals(100, result.get(GooType.ROCK));
            assertEquals(0, result.get(GooType.METAL));
            assertEquals(0, result.get(GooType.AEON));
        }

        /** Lossy source division throws. */
        @Test
        void lossySourceDivisionThrows() {
            GooConversion.Formula f = GooConversion.parseFormula("metal / 3 -> aeon / 1");
            GooValue copper = goo(GooType.METAL, 100); // 100/3 = 33.3

            assertThrows(ArithmeticException.class,
                    () -> GooConversion.apply(copper, f, 1));
        }

        /** Lossy target division throws. */
        @Test
        void lossyTargetDivisionThrows() {
            GooConversion.Formula f = GooConversion.parseFormula("metal / 4 -> aeon / 3");
            GooValue copper = goo(GooType.METAL, 160); // 40/3 = 13.3

            assertThrows(ArithmeticException.class,
                    () -> GooConversion.apply(copper, f, 1));
        }
    }

    // ── Block parsing ────────────────────────────────────────────────────

    @Nested
    class BlockParsing {

        /** Full _conversions block: formulas, stacks, and assignments. */
        @Test
        void parseFullBlock() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("oxidation", "metal / 4 -> aeon / 2");
            entries.put("exposed", "@oxidation");
            entries.put("weathered", "2 @oxidation");
            entries.put("oxidized", "3 @oxidation");

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            assertEquals(1, parsed.formulas().size());
            assertEquals(3, parsed.stacks().size());
            assertEquals(2, parsed.stacks().get("weathered").multiplier());
            assertEquals("oxidation", parsed.stacks().get("weathered").formulaName());
        }

        /** "denied" on an assignment skips it. */
        @Test
        void deniedAssignmentSkipped() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("oxidation", "metal / 4 -> aeon / 2");
            entries.put("exposed", "@oxidation");
            entries.put("#copper_stuff", "@exposed");
            entries.put("#iron_stuff", "denied");

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            assertEquals(1, parsed.assignments().size());
            assertEquals("#copper_stuff", parsed.assignments().get(0).target());
        }

        /** "denied" on a formula removes it. */
        @Test
        void deniedFormulaRemoved() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("oxidation", "metal / 4 -> aeon / 2");
            entries.put("oxidation", "denied"); // override removes it

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            assertTrue(parsed.formulas().isEmpty());
        }
    }

    // ── End-to-end with effective values ──────────────────────────────────

    @Nested
    class EndToEnd {

        /** Apply conversion chain to effective values. */
        @Test
        void chainApplies() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("minecraft:weathered_copper"), goo(GooType.METAL, 160, GooType.ROCK, 50));
            effective.put(id("minecraft:weathered_cut_copper"), goo(GooType.METAL, 160));

            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooConversion.Stack weatheredStack = new GooConversion.Stack("oxidation", 2);
            Map<String, GooConversion.Formula> formulas = Map.of("oxidation", oxidation);

            java.util.List<Identifier> targets = java.util.List.of(
                    id("minecraft:weathered_copper"),
                    id("minecraft:weathered_cut_copper"));

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "#weathered", null, java.util.List.of(weatheredStack));
            GooConversion.applyAssignment(effective, targets, null, assignment, formulas);

            GooValue copper = effective.get(id("minecraft:weathered_copper"));
            assertEquals(80, copper.get(GooType.METAL));
            assertEquals(40, copper.get(GooType.AEON));
            assertEquals(50, copper.get(GooType.ROCK));
        }

        /** Parallel copy + conversion chain. */
        @Test
        void parallelCopyThenConvert() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("minecraft:copper_block"), goo(GooType.METAL, 160));
            effective.put(id("minecraft:cut_copper"), goo(GooType.METAL, 80));
            // Targets start empty
            effective.put(id("minecraft:exposed_copper"), GooValue.EMPTY);
            effective.put(id("minecraft:exposed_cut_copper"), GooValue.EMPTY);

            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            Map<String, GooConversion.Formula> formulas = Map.of("oxidation", oxidation);

            java.util.List<Identifier> sources = java.util.List.of(
                    id("minecraft:copper_block"), id("minecraft:cut_copper"));
            java.util.List<Identifier> targets = java.util.List.of(
                    id("minecraft:exposed_copper"), id("minecraft:exposed_cut_copper"));

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "#exposed", "#originals",
                    java.util.List.of(new GooConversion.Stack("oxidation", 1)));
            GooConversion.applyAssignment(effective, targets, sources, assignment, formulas);

            // copper_block (160 metal) copied to exposed_copper, then 1x oxidation
            GooValue exposed = effective.get(id("minecraft:exposed_copper"));
            assertEquals(120, exposed.get(GooType.METAL)); // 160 - 40
            assertEquals(20, exposed.get(GooType.AEON));    // 40 / 2

            // cut_copper (80 metal) copied to exposed_cut_copper, then 1x oxidation
            GooValue exposedCut = effective.get(id("minecraft:exposed_cut_copper"));
            assertEquals(60, exposedCut.get(GooType.METAL)); // 80 - 20
            assertEquals(10, exposedCut.get(GooType.AEON));   // 20 / 2
        }

        /** Size mismatch between source and target logs error, skips assignment. */
        @Test
        void parallelSizeMismatchSkips() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("a"), goo(GooType.METAL, 100));
            effective.put(id("b"), GooValue.EMPTY);
            effective.put(id("c"), GooValue.EMPTY);

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "#target", "#source", java.util.List.of());
            GooConversion.applyAssignment(effective,
                    java.util.List.of(id("b"), id("c")),
                    java.util.List.of(id("a")),
                    assignment, Map.of());

            // Mismatch: 1 source, 2 targets. No copy happens.
            assertTrue(effective.get(id("b")).isEmpty());
            assertTrue(effective.get(id("c")).isEmpty());
        }
    }
}
