package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for GooValueJsonFormat: $constant expression resolution and GooValue serialization.
 * No Minecraft server required.
 */
class GooValueJsonFormatTest {

    // ── parseGooValue ────────────────────────────────────────────────────

    @Nested
    class ParseGooValue {

        /**
         * Plain integer value is parsed directly.
         */
        @Test
        void plainIntegerParsed() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", 42);

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of());
            assertEquals(42, result.get(GooType.METAL));
        }

        /**
         * $constant reference resolves via symbol table.
         */
        @Test
        void constantReferenceParsed() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "$base");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 10));
            assertEquals(10, result.get(GooType.ROCK));
        }

        /**
         * $constant * N expression resolves to product.
         */
        @Test
        void constantMultiply() {
            JsonObject json = new JsonObject();
            json.addProperty("leaf", "$base * 3");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 5));
            assertEquals(15, result.get(GooType.LEAF));
        }

        /**
         * $constant + N expression resolves to sum.
         */
        @Test
        void constantAdd() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$base + 7");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 3));
            assertEquals(10, result.get(GooType.METAL));
        }

        /**
         * $constant - N expression resolves to difference.
         */
        @Test
        void constantSubtract() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "$base - 2");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 10));
            assertEquals(8, result.get(GooType.ROCK));
        }

        /**
         * $constant / N expression resolves to quotient.
         */
        @Test
        void constantDivide() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$base / 4");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 20));
            assertEquals(5, result.get(GooType.METAL));
        }

        /**
         * Multiply before add: $base * 3 + 2 = (base*3)+2.
         */
        @Test
        void multiplyBeforeAdd() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "$base * 3 + 2");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 10));
            assertEquals(32, result.get(GooType.ROCK));
        }

        /**
         * Precedence: $a + 1 * 2 - 3 = a+(1*2)-3, not ((a+1)*2)-3.
         */
        @Test
        void precedenceMixedOps() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$x + 1 * 2 - 3");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("x", 5));
            assertEquals(4, result.get(GooType.METAL));
        }

        /**
         * Precedence: $a + $b * $c = a+(b*c).
         */
        @Test
        void precedenceAddThenMultiply() {
            JsonObject json = new JsonObject();
            json.addProperty("leaf", "$a + $b * $c");

            GooValue result = GooValueJsonFormat.parseGooValue(json,
                    Map.of("a", 10, "b", 5, "c", 3));
            assertEquals(25, result.get(GooType.LEAF));
        }

        /**
         * Division before subtraction: 100 - 20 / 4 = 100-5 = 95.
         */
        @Test
        void divisionBeforeSubtraction() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "100 - 20 / 4");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of());
            assertEquals(95, result.get(GooType.ROCK));
        }

        /**
         * Bare integer string without $ still parses.
         */
        @Test
        void bareIntegerString() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "240");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of());
            assertEquals(240, result.get(GooType.ROCK));
        }

        /**
         * Parentheses override left-to-right: $a + ( 1 * 2 ) = a+2, not (a+1)*2.
         */
        @Test
        void parenthesesOverrideLeftToRight() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "$a + ( 1 * 2 )");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("a", 5));
            assertEquals(7, result.get(GooType.ROCK));
        }

        /**
         * Nested parentheses: ( ( $a + 1 ) * 2 ).
         */
        @Test
        void nestedParentheses() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "( ( $a + 1 ) * 2 )");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("a", 3));
            assertEquals(8, result.get(GooType.METAL));
        }

        /**
         * Parenthesized sub-expression as first operand: ( $a + $b ) * 3.
         */
        @Test
        void parenthesizedFirstOperand() {
            JsonObject json = new JsonObject();
            json.addProperty("leaf", "( $a + $b ) * 3");

            GooValue result = GooValueJsonFormat.parseGooValue(json,
                    Map.of("a", 2, "b", 4));
            assertEquals(18, result.get(GooType.LEAF));
        }

        /**
         * Multiple parenthesized groups: ( $a + 1 ) * ( $b - 1 ).
         */
        @Test
        void multipleParenthesizedGroups() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "( $a + 1 ) * ( $b - 1 )");

            GooValue result = GooValueJsonFormat.parseGooValue(json,
                    Map.of("a", 3, "b", 5));
            assertEquals(16, result.get(GooType.ROCK));
        }

        /**
         * No spaces around parens: ($a+1)*2.
         */
        @Test
        void noSpacesAroundParens() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "($a+1)*2");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("a", 5));
            assertEquals(12, result.get(GooType.ROCK));
        }

        /**
         * No spaces anywhere: $a*3+2.
         */
        @Test
        void noSpacesAnywhere() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$a*3+2");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("a", 10));
            assertEquals(32, result.get(GooType.METAL));
        }

        /**
         * Mixed spacing: ($a +$b)*$c.
         */
        @Test
        void mixedSpacing() {
            JsonObject json = new JsonObject();
            json.addProperty("leaf", "($a +$b)*$c");

            GooValue result = GooValueJsonFormat.parseGooValue(json,
                    Map.of("a", 2, "b", 4, "c", 3));
            assertEquals(18, result.get(GooType.LEAF));
        }

        /**
         * Tight nested parens: (($a+1)*2).
         */
        @Test
        void tightNestedParens() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "(($a+1)*2)");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("a", 3));
            assertEquals(8, result.get(GooType.ROCK));
        }

        /**
         * Bare word dot notation in per-type expression extracts the type.
         */
        @Test
        void bareWordDotNotation() {
            Map<Identifier, GooValue> baseValues = new LinkedHashMap<>();
            baseValues.put(Identifier.parse("minecraft:coal"),
                    new GooValue(Map.of(GooType.ROCK, 48, GooType.BLAZE, 336)));
            JsonObject json = new JsonObject();
            json.addProperty("metal", "coal.blaze * 2");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of(), baseValues);
            assertEquals(672, result.get(GooType.METAL));
        }

        /**
         * Implicit multiplication: "3 $base" == "$base * 3".
         */
        @Test
        void implicitMultiplication() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "3 $base");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 10));
            assertEquals(30, result.get(GooType.METAL));
        }

        /**
         * Implicit multiplication with addition: "$a + 2 $b".
         */
        @Test
        void implicitMultiplicationWithAddition() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "$a + 2 $b");

            GooValue result = GooValueJsonFormat.parseGooValue(json,
                    Map.of("a", 5, "b", 10));
            assertEquals(25, result.get(GooType.ROCK));
        }

        /**
         * Unary minus with dot notation: "-cut_copper.metal / 4".
         */
        @Test
        void unaryMinusDotNotation() {
            Map<Identifier, GooValue> baseValues = new LinkedHashMap<>();
            baseValues.put(Identifier.parse("minecraft:cut_copper"),
                    new GooValue(Map.of(GooType.METAL, 200)));
            JsonObject json = new JsonObject();
            json.addProperty("metal", "-cut_copper.metal / 4");
            json.addProperty("aeon", "cut_copper.metal / 8");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of(), baseValues);
            assertEquals(-50, result.get(GooType.METAL)); // -(200) / 4
            assertEquals(25, result.get(GooType.AEON));    // 200 / 8
        }

        /**
         * Unary minus on scalar constant: "-$base".
         */
        @Test
        void unaryMinusOnConstant() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "-$base");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 64));
            assertEquals(-64, result.get(GooType.METAL));
        }

        /**
         * Unknown constant resolves to zero (no exception).
         */
        @Test
        void unknownConstantReturnsZero() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$missing");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of());
            assertEquals(0, result.get(GooType.METAL));
        }

        /**
         * Multiple goo types in one object all parsed.
         */
        @Test
        void multipleTypesAllParsed() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", 5);
            json.addProperty("rock", 3);

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of());
            assertEquals(5, result.get(GooType.METAL));
            assertEquals(3, result.get(GooType.ROCK));
        }
    }

    // ── resolveConstantValue (used by _constants parsing) ─────────────────

    @Nested
    class ResolveConstantValue {

        /**
         * Constants can reference earlier constants.
         */
        @Test
        void constantReferencesEarlierConstant() {
            JsonObject json = new JsonObject();
            json.addProperty("val", "$stone / 4");

            var constants = new java.util.LinkedHashMap<String, Integer>();
            constants.put("stone", 240);
            int result = GooValueJsonFormat.resolveConstantValue(
                    json.get("val"), constants);
            assertEquals(60, result);
        }

        /**
         * Plain integer constants still work.
         */
        @Test
        void plainIntConstant() {
            JsonObject json = new JsonObject();
            json.addProperty("val", 100);

            int result = GooValueJsonFormat.resolveConstantValue(
                    json.get("val"), Map.of());
            assertEquals(100, result);
        }
    }

    // ── toJson ───────────────────────────────────────────────────────────

    @Nested
    class ToJson {

        /**
         * Round-trip: parseGooValue -> toJson preserves values.
         */
        @Test
        void roundTrip() {
            JsonObject original = new JsonObject();
            original.addProperty("metal", 10);
            original.addProperty("rock", 5);

            GooValue value = GooValueJsonFormat.parseGooValue(original, Map.of());
            JsonObject serialized = GooValueJsonFormat.toJson(value);

            assertEquals(10, serialized.get("metal").getAsInt());
            assertEquals(5, serialized.get("rock").getAsInt());
        }
    }
}
