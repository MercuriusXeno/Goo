package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Merges datapack JSON layers and expands tag keys into per-item entries.
 * Pure-static utility with no instance state.
 */
final class GooValueMerger {

    /**
     * Prefix for tag/pseudo-tag references.
     */
    private static final String PREFIX_TAG = "#";
    /**
     * Suffix key for the constants block.
     */
    private static final String CONSTANTS_SUFFIX = "_constants";
    /**
     * Suffix key for the groups block.
     */
    private static final String GROUPS_SUFFIX = "_groups";

    private GooValueMerger() {
    }

    /**
     * Merges multiple JSON layers (one per datapack, in bottom-to-top order) into a single
     * JsonObject using last-in-wins semantics. {@code _constants} and {@code _groups} merge
     * at inner key level; all other keys overwrite entirely.
     *
     * @param layers parsed JSON objects in pack order (base first, overlays later)
     * @return a single merged JsonObject ready for parseConstants + parseItemValues
     */
    static JsonObject mergeBaseValueJsonLayers(List<JsonObject> layers) {
        JsonObject merged = new JsonObject();
        for (JsonObject layer : layers) {
            mergeOneLayer(merged, layer);
        }
        return merged;
    }

    /**
     * Applies one layer's entries onto the merged result.
     *
     * @param merged the accumulator JSON object
     * @param layer  the incoming layer to merge
     */
    private static void mergeOneLayer(JsonObject merged, JsonObject layer) {
        for (Map.Entry<String, JsonElement> entry : layer.entrySet()) {
            String key = entry.getKey();
            if (CONSTANTS_SUFFIX.equals(key) || GROUPS_SUFFIX.equals(key)) {
                mergeNestedObject(merged, key, entry.getValue().getAsJsonObject());
            } else {
                merged.add(key, entry.getValue());
            }
        }
    }

    /**
     * Merges inner keys of a nested object (constants or groups) at key level.
     *
     * @param merged   the accumulator JSON object
     * @param outerKey the top-level key (e.g. _constants)
     * @param incoming the inner object to merge
     */
    private static void mergeNestedObject(JsonObject merged, String outerKey, JsonObject incoming) {
        JsonObject existing = merged.has(outerKey)
                ? merged.getAsJsonObject(outerKey)
                : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : incoming.entrySet()) {
            existing.add(entry.getKey(), entry.getValue());
        }
        merged.add(outerKey, existing);
    }

    /**
     * Expands tag keys (prefixed with {@code #}) in the merged JSON into individual item entries.
     * Iterates entries top-to-bottom so last-in-wins ordering is preserved: a {@code #tag} paints
     * all its members, and a later explicit entry overwrites a specific member (or vice versa).
     *
     * @param merged      the merged JSON from all datapack layers
     * @param tagResolver resolves a tag identifier to the set of item identifiers it contains
     * @return a new JsonObject with tag keys expanded and removed
     */
    static JsonObject expandTagEntries(JsonObject merged, Function<Identifier, Set<Identifier>> tagResolver) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : merged.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PREFIX_TAG)) {
                expandOneTag(key, entry.getValue(), tagResolver, result);
            } else {
                result.add(key, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Expands a MC tag key into per-member entries. Preserves unresolved keys for pseudo-tag handling.
     *
     * @param tagKey      the #tag key from the JSON
     * @param value       the value to assign to each tag member
     * @param tagResolver resolves a tag ID to its member item IDs
     * @param result      the accumulator JSON object
     */
    private static void expandOneTag(String tagKey, JsonElement value,
                                     Function<Identifier, Set<Identifier>> tagResolver,
                                     JsonObject result) {
        Identifier tagId = Identifier.parse(tagKey.substring(1));
        Set<Identifier> members = tagResolver.apply(tagId);
        if (members.isEmpty()) {
            // Keep the #key for pseudo-tag resolution in parseItemValues
            result.add(tagKey, value);
            return;
        }
        for (Identifier member : members) {
            result.add(member.toString(), value);
        }
    }

    /**
     * Resolves an item tag to the set of item identifiers it contains.
     *
     * @param tagId the tag identifier to resolve
     * @return set of item IDs in the tag (empty if tag not found)
     */
    static Set<Identifier> resolveItemTag(Identifier tagId) {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        Set<Identifier> members = new HashSet<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            members.add(BuiltInRegistries.ITEM.getKey(holder.value()));
        }
        return members;
    }
}
