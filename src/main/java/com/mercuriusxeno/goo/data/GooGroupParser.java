package com.mercuriusxeno.goo.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parses group definitions (_groups) and restriction lists (_restricted) from
 * base_values.json. Groups act as pseudo-tags: named sets of item IDs that can
 * be referenced by #name in values and restrictions.
 */
final class GooGroupParser {

    private static final String GROUPS_SUFFIX = "_groups";
    private static final String KEY_RESTRICTED = "_restricted";

    private GooGroupParser() {
    }

    /**
     * Parses _groups into pseudo-tags: each key maps to an array of item IDs.
     *
     * @param json  the root JSON object containing the _groups key
     * @param state mutable parsing state
     */
    static void parseGroups(JsonObject json, GooValueLoader.ParseState state) {
        if (!json.has(GROUPS_SUFFIX)) {
            return;
        }
        JsonObject groups = json.getAsJsonObject(GROUPS_SUFFIX);
        for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
            state.pseudoTags.put(group.getKey(), parseGroupMembers(group.getValue().getAsJsonArray()));
        }
    }

    /**
     * Parses a JSON array of item ID strings into a linked set of Identifiers.
     *
     * @param items the JSON array of item ID strings
     * @return ordered set of parsed item IDs
     */
    static Set<Identifier> parseGroupMembers(JsonArray items) {
        Set<Identifier> members = new LinkedHashSet<>();
        for (JsonElement item : items) {
            members.add(Identifier.parse(item.getAsString()));
        }
        return members;
    }

    /**
     * Parses the _restricted array, resolving #group references against pseudo-tags.
     *
     * @param json  the root JSON object containing the optional _restricted key
     * @param state mutable parsing state
     */
    static void parseRestricted(JsonObject json, GooValueLoader.ParseState state) {
        if (!json.has(KEY_RESTRICTED)) {
            return;
        }
        for (JsonElement elem : json.getAsJsonArray(KEY_RESTRICTED)) {
            String entry = elem.getAsString();
            if (entry.startsWith(GooValueLoader.PREFIX_TAG)) {
                Set<Identifier> members = resolvePseudoTag(entry.substring(1), state.pseudoTags);
                if (members != null) {
                    state.restrictedItems.addAll(members);
                }
            } else {
                state.restrictedItems.add(Identifier.parse(entry));
            }
        }
    }

    /**
     * Resolves a pseudo-tag name to its members, trying exact then parsed path.
     *
     * @param name       the pseudo-tag name to resolve
     * @param pseudoTags the pseudo-tag map
     * @return the set of member item IDs, or null if not found
     */
    static Set<Identifier> resolvePseudoTag(String name, Map<String, Set<Identifier>> pseudoTags) {
        Set<Identifier> members = pseudoTags.get(name);
        if (members == null) {
            members = pseudoTags.get(Identifier.parse(name).getPath());
        }
        return members;
    }
}
