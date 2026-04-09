package com.mercuriusxeno.goo.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads and saves the effective goo value cache to disk.
 * The cache contains pre-resolved integer values per goo type,
 * so no expression evaluation or base value merging is needed at startup.
 * Pure-static utility with no instance state.
 */
final class GooValueCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Log: loaded effective values from cache. */
    private static final String LOG_LOADED_CACHE = "Loaded {} effective goo values from cache";
    /** Log: failed to load cache. */
    private static final String LOG_CACHE_LOAD_FAIL = "Failed to load effective goo value cache";
    /** Log: saved effective values to cache. */
    private static final String LOG_SAVED_CACHE = "Saved {} effective goo values to cache";
    /** Log: failed to save cache. */
    private static final String LOG_CACHE_SAVE_FAIL = "Failed to save effective goo value cache";

    private GooValueCache() {}

    /**
     * Loads effective values from the flat cache file into the provided map.
     * The cache contains pre-resolved integer values per goo type, so no expression
     * evaluation or base value merging is needed.
     *
     * @param cachePath the path to the cache file (may be null or non-existent)
     * @param effectiveValues the mutable map to populate with loaded values
     */
    static void loadEffectiveCache(Path cachePath, Map<Identifier, GooValue> effectiveValues) {
        if (cachePath == null || !Files.exists(cachePath)) { return; }

        try (Reader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            deserializeEffectiveValues(json, effectiveValues);
            if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_LOADED_CACHE, effectiveValues.size()); }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Goo.LOGGER.warn(LOG_CACHE_LOAD_FAIL, e);
        }
    }

    /**
     * Replaces effective values with entries parsed from a flat cache JSON object.
     *
     * @param json the cache JSON with item ID keys and GooValue objects
     * @param effectiveValues the mutable map to populate
     */
    private static void deserializeEffectiveValues(JsonObject json, Map<Identifier, GooValue> effectiveValues) {
        effectiveValues.clear();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            Identifier itemId = Identifier.parse(entry.getKey());
            effectiveValues.put(itemId, GooValueJsonFormat.parseGooValue(entry.getValue().getAsJsonObject()));
        }
    }

    /**
     * Saves the complete effective value map to the cache file.
     * Written by /goo regen so startup can load a flat, pre-resolved file.
     *
     * @param cachePath the path to the cache file (may be null)
     * @param effectiveValues the values to save
     */
    static void saveEffectiveValues(Path cachePath, Map<Identifier, GooValue> effectiveValues) {
        if (cachePath == null) { return; }

        try {
            Files.createDirectories(cachePath.getParent());
            JsonObject json = serializeValues(effectiveValues);
            Files.writeString(cachePath, GSON.toJson(json), StandardCharsets.UTF_8);
            if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_SAVED_CACHE, effectiveValues.size()); }
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_CACHE_SAVE_FAIL, e);
        }
    }

    /**
     * Serializes a value map to a sorted JSON object.
     *
     * @param values the values to serialize
     * @return sorted JSON object with item IDs as keys
     */
    private static JsonObject serializeValues(Map<Identifier, GooValue> values) {
        JsonObject json = new JsonObject();
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> json.add(e.getKey().toString(), GooValueJsonFormat.toJson(e.getValue())));
        return json;
    }
}
