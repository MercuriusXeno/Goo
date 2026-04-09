package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses base_values.json into registry state: constants, groups (pseudo-tags),
 * item values, conversions, and restrictions. Handles classpath and datapack loading,
 * pseudo-tag expansion, and parallel copy.
 * Pure-static utility with no instance state.
 */
final class GooValueLoader {

    private static final String BASE_VALUES_PATH = "/data/goo/goo_values/base_values.json";
    private static final String PREFIX_INTERNAL = "_";
    static final String PREFIX_TAG = "#";
    private static final String VALUE_DENIED = "denied";
    private static final String MOD_NAMESPACE = "goo";
    private static final String BASE_VALUES_RESOURCE = "goo_values/base_values.json";

    // --- Log messages ---

    private static final String LOG_NO_BASE_FILE = "Could not find base_values.json";
    private static final String LOG_LOAD_FAIL = "Failed to load base goo values";
    private static final String LOG_LOADED_BASE = "Loaded {} base goo values";
    private static final String LOG_LOADED_PACKS = "Loaded {} base goo values from {} pack(s)";
    private static final String LOG_READ_PACK_FAIL = "Failed to read base_values.json from pack {}: {}";
    private static final String LOG_NEGATIVE_BASE = "Negative goo in base value for {}: {} -- skipped";

    private GooValueLoader() {}

    /**
     * Mutable state bucket passed through parsing methods so the loader remains static.
     * Created by the registry before a load, populated during parsing, then read back.
     */
    static final class ParseState {
        final Map<Identifier, GooValue> baseValues;
        final Map<Identifier, GooValue> effectiveValues;
        final Set<Identifier> deniedItems;
        final Set<Identifier> restrictedItems;
        final Map<String, Integer> constants;
        final Map<String, GooValue> treeConstants;
        final Map<String, Set<Identifier>> pseudoTags;
        GooConversion.ParsedConversions preConversions;
        GooConversion.ParsedConversions postConversions;
        JsonObject lastMergedBaseValues;

        ParseState(Map<Identifier, GooValue> baseValues,
                   Map<Identifier, GooValue> effectiveValues,
                   Set<Identifier> deniedItems,
                   Set<Identifier> restrictedItems,
                   Map<String, Integer> constants,
                   Map<String, GooValue> treeConstants,
                   Map<String, Set<Identifier>> pseudoTags) {
            this.baseValues = baseValues;
            this.effectiveValues = effectiveValues;
            this.deniedItems = deniedItems;
            this.restrictedItems = restrictedItems;
            this.constants = constants;
            this.treeConstants = treeConstants;
            this.pseudoTags = pseudoTags;
        }
    }

    // ── Classpath loading ──────────────────────────────────────────────

    /**
     * Reads and parses the embedded base_values.json classpath resource.
     *
     * @param state mutable parsing state to populate
     */
    static void loadBaseValuesFromClasspath(ParseState state) {
        try (InputStream is = GooValueRegistry.class.getResourceAsStream(BASE_VALUES_PATH)) {
            if (is == null) {
                Goo.LOGGER.error(LOG_NO_BASE_FILE);
                return;
            }
            parseBaseValuesFromStream(is, state);
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_LOAD_FAIL, e);
        }
        if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_LOADED_BASE, state.baseValues.size()); }
    }

    /**
     * Parses constants, groups, item entries, and conversions from an input stream.
     *
     * @param is the input stream containing base_values.json data
     * @param state mutable parsing state to populate
     * @throws IOException if reading the stream fails
     */
    static void parseBaseValuesFromStream(InputStream is, ParseState state) throws IOException {
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            GooConstantParser.parseConstants(json, state);
            parseItemValues(json, state);
            GooConversionLoader.parseConversions(json, state);
            GooConversionLoader.applyConversions(state.preConversions, state.baseValues, state.pseudoTags);
        }
    }

    // ── Datapack loading ───────────────────────────────────────────────

    /**
     * Parses each resource in the stack into a JsonObject, skipping failures.
     *
     * @param stack ordered resource list from the pack stack
     * @return parsed JSON objects (failures skipped)
     */
    static List<JsonObject> parseResourceLayers(List<Resource> stack) {
        List<JsonObject> layers = new ArrayList<>();
        for (Resource resource : stack) {
            try (BufferedReader reader = resource.openAsReader()) {
                layers.add(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException e) {
                if (Goo.LOGGER.isWarnEnabled()) {
                    Goo.LOGGER.warn(LOG_READ_PACK_FAIL,
                            resource.sourcePackId(), e.getMessage());
                }
            }
        }
        return layers;
    }

    /**
     * Merges, expands, and applies parsed JSON layers to parsing state.
     *
     * @param layers parsed JSON objects in pack order
     * @param state mutable parsing state to populate
     */
    static void applyMergedLayers(List<JsonObject> layers, ParseState state) {
        JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(layers);
        merged = GooValueMerger.expandTagEntries(merged, GooValueMerger::resolveItemTag);
        state.lastMergedBaseValues = merged;
        GooConstantParser.parseConstants(merged, state);
        parseItemValues(merged, state);
        GooConversionLoader.parseConversions(merged, state);
        GooConversionLoader.applyConversions(state.preConversions, state.baseValues, state.pseudoTags);
        state.effectiveValues.putAll(state.baseValues);
        if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_LOADED_PACKS, state.baseValues.size(), layers.size()); }
    }

    // ── Item values / groups / pseudo-tags ──────────────────────────────

    /**
     * Parses _groups first (pseudo-tags), then item entries. Resolves #name against pseudo-tags.
     *
     * @param json the root JSON object to extract item values from
     * @param state mutable parsing state
     */
    private static void parseItemValues(JsonObject json, ParseState state) {
        GooGroupParser.parseGroups(json, state);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PREFIX_INTERNAL)) { continue; }
            if (key.startsWith(PREFIX_TAG)) {
                GooParallelCopy.expandPseudoTag(key.substring(1), entry.getValue(), state);
                continue;
            }
            assignItemValue(Identifier.parse(key), entry.getValue(), state);
        }
        GooGroupParser.parseRestricted(json, state);
    }

    // ── Item assignment ────────────────────────────────────────────────

    /**
     * Assigns a value or denial to a single item.
     *
     * @param itemId the item to assign a value to
     * @param value the JSON value (object for explicit, string for expression, "denied")
     * @param state mutable parsing state
     */
    static void assignItemValue(Identifier itemId, JsonElement value, ParseState state) {
        if (isDeniedEntry(value)) {
            state.deniedItems.add(itemId);
        } else {
            GooValue resolved = resolveItemEntry(value, state);
            if (resolved.hasNegative()) {
                Goo.LOGGER.error(LOG_NEGATIVE_BASE, itemId, resolved);
            } else {
                state.baseValues.put(itemId, resolved);
            }
        }
    }

    /**
     * Resolves a non-denied item entry: JSON object -> explicit GooValue,
     * string -> item reference expression.
     *
     * @param value the JSON element to resolve
     * @param state parsing state containing constants and base values
     * @return the parsed GooValue
     */
    private static GooValue resolveItemEntry(JsonElement value, ParseState state) {
        if (value.isJsonObject()) {
            return GooValueJsonFormat.parseGooValue(value.getAsJsonObject(),
                    state.constants, state.baseValues, state.treeConstants);
        }
        return GooValueExpression.evaluate(value.getAsString().trim(),
                state.baseValues, state.constants, state.treeConstants);
    }

    /**
     * Returns true if the JSON value is the string "denied".
     *
     * @param element the JSON element to check
     * @return true if the element is the string "denied"
     */
    private static boolean isDeniedEntry(JsonElement element) {
        return element.isJsonPrimitive() && VALUE_DENIED.equals(element.getAsString());
    }


    // ── Registry state management ──────────────────────────────────────

    /**
     * Clears all mutable registry state before a fresh load.
     *
     * @param state the parse state to clear
     */
    static void clearRegistryState(ParseState state) {
        state.baseValues.clear();
        state.deniedItems.clear();
        state.restrictedItems.clear();
        state.effectiveValues.clear();
        state.constants.clear();
        state.treeConstants.clear();
        state.pseudoTags.clear();
        state.preConversions = null;
        state.postConversions = null;
        state.lastMergedBaseValues = null;
    }

    /**
     * Returns the mod namespace string.
     *
     * @return "goo"
     */
    static String modNamespace() {
        return MOD_NAMESPACE;
    }

    /**
     * Returns the base values resource path within the datapack.
     *
     * @return "goo_values/base_values.json"
     */
    static String baseValuesResource() {
        return BASE_VALUES_RESOURCE;
    }
}
