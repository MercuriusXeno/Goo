package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GooValueJsonFormat: $constant expression resolution and GooValue serialization.
 * No Minecraft server required.
 */
class GooValueJsonFormatTest {

    // ── parseGooValue ────────────────────────────────────────────────────

    @Nested
    class ParseGooValue {

        /** Plain integer value is parsed directly. */
        @Test
        void plainIntegerParsed() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", 42);

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of());
            assertEquals(42, result.get(GooType.METAL));
        }

        /** $constant reference resolves via symbol table. */
        @Test
        void constantReferenceParsed() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "$base");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 10));
            assertEquals(10, result.get(GooType.ROCK));
        }

        /** $constant * N expression resolves to product. */
        @Test
        void constantMultiply() {
            JsonObject json = new JsonObject();
            json.addProperty("leaf", "$base * 3");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 5));
            assertEquals(15, result.get(GooType.LEAF));
        }

        /** $constant + N expression resolves to sum. */
        @Test
        void constantAdd() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$base + 7");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 3));
            assertEquals(10, result.get(GooType.METAL));
        }

        /** $constant - N expression resolves to difference. */
        @Test
        void constantSubtract() {
            JsonObject json = new JsonObject();
            json.addProperty("rock", "$base - 2");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 10));
            assertEquals(8, result.get(GooType.ROCK));
        }

        /** $constant / N expression resolves to quotient. */
        @Test
        void constantDivide() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$base / 4");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of("base", 20));
            assertEquals(5, result.get(GooType.METAL));
        }

        /** Unknown constant resolves to zero (no exception). */
        @Test
        void unknownConstantReturnsZero() {
            JsonObject json = new JsonObject();
            json.addProperty("metal", "$missing");

            GooValue result = GooValueJsonFormat.parseGooValue(json, Map.of());
            assertEquals(0, result.get(GooType.METAL));
        }

        /** Multiple goo types in one object all parsed. */
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

    // ── toJson ───────────────────────────────────────────────────────────

    @Nested
    class ToJson {

        /** Round-trip: parseGooValue -> toJson preserves values. */
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
