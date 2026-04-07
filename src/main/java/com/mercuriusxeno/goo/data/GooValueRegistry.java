package com.mercuriusxeno.goo.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import org.jspecify.annotations.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Central registry for all item goo values. Loads base values from JSON,
 * derives values from all recipe types using LCD rule, and persists derived values.
 *
 * <p>The derivation engine is split into two layers: a thin Minecraft adapter
 * (recipe/ingredient resolution) and a pure logic core that operates on
 * {@link RecipeInput} records, enabling unit testing without a running server.</p>
 */
public class GooValueRegistry implements IGooValueLookup {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASE_VALUES_PATH = "/data/goo/goo_values/base_values.json";
    private static final String CONSTANTS_SUFFIX = "_constants";
    private static final String GROUPS_SUFFIX = "_groups";
    static final int MAX_DERIVATION_PASSES = 20;

    // --- Resource identifiers ---

    /** Mod namespace for resource locations. */
    private static final String MOD_NAMESPACE = "goo";
    /** Resource path for base values JSON. */
    private static final String BASE_VALUES_RESOURCE = "goo_values/base_values.json";

    // --- JSON keys and prefixes ---

    /** Prefix for internal JSON keys (constants, groups, conversions). */
    private static final String PREFIX_INTERNAL = "_";
    /** Prefix for tag/pseudo-tag references. */
    private static final String PREFIX_TAG = "#";
    /** Denied value string in base_values.json. */
    private static final String VALUE_DENIED = "denied";
    /** JSON block key for pre-derivation conversions. */
    private static final String KEY_CONVERSIONS = "_conversions";
    /** JSON block key for post-derivation conversions. */
    private static final String KEY_POST_CONVERSIONS = "_post_conversions";
    /** Dot separator for validation context paths. */
    private static final String DOT = ".";
    /** Constants key prefix for validation context. */
    private static final String CTX_CONSTANTS = "_constants.";
    /** Groups key prefix for validation context. */
    private static final String CTX_GROUPS = "_groups.";
    /** Formula arrow operator in conversion values. */
    private static final String FORMULA_ARROW = "->";
    /** Additive prefix in conversion values. */
    private static final String ADDITIVE_PREFIX = "+";
    /** At-sign prefix for conversion references. */
    private static final String AT_PREFIX = "@";
    /** Dollar-sign prefix for constant references. */
    private static final String DOLLAR_PREFIX = "$";
    /** Colon character used in namespaced identifiers. */
    private static final String COLON = ":";
    /** Default namespace for unqualified item names. */
    private static final String DEFAULT_NS_PREFIX = "minecraft:";
    /** Operator characters skipped during expression validation. */
    private static final String OPERATOR_CHARS = "+-*/()";
    /** Whitespace regex for splitting conversion reference strings. */
    private static final String WHITESPACE_REGEX = "\\s+";
    /** Regex for stripping leading digits from ref names. */
    private static final String LEADING_DIGITS_REGEX = "^\\d+\\s*";

    // --- Log messages ---

    /** Log: base values file not found. */
    private static final String LOG_NO_BASE_FILE = "Could not find base_values.json";
    /** Log: failed to load base values. */
    private static final String LOG_LOAD_FAIL = "Failed to load base goo values";
    /** Log: loaded base values count. */
    private static final String LOG_LOADED_BASE = "Loaded {} base goo values";
    /** Log: no datapack provides base values. */
    private static final String LOG_NO_DATAPACK = "No datapack provides goo_values/base_values.json";
    /** Log: loaded base values from packs. */
    private static final String LOG_LOADED_PACKS = "Loaded {} base goo values from {} pack(s)";
    /** Log: failed to read base values from pack. */
    private static final String LOG_READ_PACK_FAIL = "Failed to read base_values.json from pack {}: {}";
    /** Log: loaded constants count. */
    private static final String LOG_LOADED_CONSTANTS = "Loaded {} constants ({} scalar, {} tree)";
    /** Log: pseudo-tag resolved to no members. */
    private static final String LOG_PSEUDO_TAG_EMPTY = "Pseudo-tag #{} resolved to no members, skipping";
    /** Log: parallel copy source empty. */
    private static final String LOG_PARALLEL_SRC_EMPTY = "Parallel copy source #{} resolved to no members";
    /** Log: parallel copy size mismatch. */
    private static final String LOG_PARALLEL_MISMATCH = "Parallel copy size mismatch: #{} has {} items, #{} has {} items";
    /** Log: parallel copy source has no value. */
    private static final String LOG_PARALLEL_NO_VALUE = "Parallel copy: source {} has no value, skipping target {}";
    /** Log: parallel copy produced negative. */
    private static final String LOG_PARALLEL_NEGATIVE = "Parallel copy produced negative for {}: {}";
    /** Log: parallel copy scale failed. */
    private static final String LOG_PARALLEL_SCALE_FAIL = "Parallel copy scale failed for {} (from {}): {}";
    /** Log: negative goo in base value. */
    private static final String LOG_NEGATIVE_BASE = "Negative goo in base value for {}: {} -- skipped";
    /** Log: loaded effective values from cache. */
    private static final String LOG_LOADED_CACHE = "Loaded {} effective goo values from cache";
    /** Log: failed to load cache. */
    private static final String LOG_CACHE_LOAD_FAIL = "Failed to load effective goo value cache";
    /** Log: saved effective values to cache. */
    private static final String LOG_SAVED_CACHE = "Saved {} effective goo values to cache";
    /** Log: failed to save cache. */
    private static final String LOG_CACHE_SAVE_FAIL = "Failed to save effective goo value cache";
    /** Log: derived values from recipes. */
    private static final String LOG_DERIVED = "Derived {} goo values from recipes";
    /** Log: unsupported recipe type. */
    private static final String LOG_UNSUPPORTED_RECIPE = "Unsupported recipe type: {}";
    /** Log: failed to get recipe result. */
    private static final String LOG_RECIPE_RESULT_FAIL = "Failed to get result from {}: {}";
    /** Log: client received values. */
    private static final String LOG_CLIENT_RECEIVED = "Client received {} effective goo values";
    /** Log: conversion target resolved to no items. */
    private static final String LOG_CONV_TARGET_EMPTY = "Conversion target {} resolved to no items";
    /** Log: parallel source resolved to no items. */
    private static final String LOG_CONV_SOURCE_EMPTY = "Parallel source {} resolved to no items";

    // --- Validation warning messages ---

    /** Warning: no base values loaded yet. */
    private static final String WARN_NO_BASE_VALUES = "No base values loaded. Run /goo regen first.";
    /** Warning suffix: expected a string value. */
    private static final String WARN_EXPECTED_STRING = ": expected a string value";
    /** Warning suffix: expected an array of item IDs. */
    private static final String WARN_EXPECTED_ARRAY = ": expected an array of item IDs";
    /** Warning suffix: invalid formula prefix. */
    private static final String WARN_INVALID_FORMULA = ": invalid formula: ";
    /** Warning suffix: unknown constant prefix. */
    private static final String WARN_UNKNOWN_CONST = ": unknown constant ";
    /** Warning suffix: unknown conversion reference prefix. */
    private static final String WARN_UNKNOWN_CONV_REF = ": unknown conversion reference @";
    /** Warning prefix for unknown constant in expression. */
    private static final String WARN_UNKNOWN_CONST_DOLLAR = ": unknown constant $";
    /** Warning infix: references item not defined above. */
    private static final String WARN_REFERENCES = ": references ";
    /** Warning suffix: not defined above it. */
    private static final String WARN_NOT_DEFINED = " which is not defined above it";
    /** Warning: missing $ prefix pattern start. */
    private static final String WARN_MISSING_DOLLAR_PREFIX = ": '";
    /** Warning: missing $ prefix pattern middle. */
    private static final String WARN_MISSING_DOLLAR_MID = "' looks like a constant missing its $ prefix (should be $";
    /** Warning: missing $ prefix pattern end. */
    private static final String WARN_MISSING_DOLLAR_SUFFIX = ")";
    /** Warning: unrecognized token prefix. */
    private static final String WARN_UNRECOGNIZED_PREFIX = ": unrecognized token '";
    /** Warning: unrecognized token suffix. */
    private static final String WARN_UNRECOGNIZED_SUFFIX = "'";

    // --- Regex group indices for PARALLEL_COPY_PATTERN ---

    /** Regex group index for the multiplier in parallel copy pattern. */
    private static final int PARALLEL_COPY_MULTIPLIER_GROUP = 2;
    /** Regex group index for the divisor in parallel copy pattern. */
    private static final int PARALLEL_COPY_DIVISOR_GROUP = 3;
    /** Suppress-warnings key for unchecked casts. */
    private static final String SUPPRESS_UNCHECKED = "unchecked";
    /** Empty string replacement for regex stripping. */
    private static final String EMPTY = "";

    private final Map<Identifier, GooValue> baseValues = new HashMap<>();
    /** Effective values after LCD comparison between base and derived. */
    private final Map<Identifier, GooValue> effectiveValues = new HashMap<>();
    /** Items explicitly denied a value (e.g. ore blocks - fortune makes them unvaluable). */
    private final Set<Identifier> deniedItems = new HashSet<>();
    /** Named constants from _constants block, resolved during value parsing. */
    private final Map<String, Integer> constants = new HashMap<>();
    /** Tree constants from _constants block: GooValue objects keyed by name. */
    private final Map<String, GooValue> treeConstants = new HashMap<>();
    /** Pseudo-tags from _groups block: group name to item set. */
    private final Map<String, Set<Identifier>> pseudoTags = new HashMap<>();
    /** Pre-derivation conversions from _conversions block. Applied to base values before recipe derivation. */
    private GooConversion.ParsedConversions preConversions;
    /** Post-derivation conversions from _post_conversions block. Applied after all values finalize. */
    private GooConversion.ParsedConversions postConversions;

    /** Result of the last derivation or cache load. Null before first derivation. */
    @Nullable
    private DerivationResult lastDerivation;

    /** Cached recipe inputs from the last derivation, for scaffold generation. */
    private List<RecipeInput> lastRecipes = List.of();

    /** Last merged base_values JSON from regen, retained for validation. */
    @Nullable
    private JsonObject lastMergedBaseValues;

    private Path effectiveCachePath;

    /** Pattern for parallel copy with optional scale: "#source * N / M" or just "#source". */
    private static final java.util.regex.Pattern PARALLEL_COPY_PATTERN = java.util.regex.Pattern.compile(
            "#(\\w+)(?:\\s*\\*\\s*(\\d+)\\s*/\\s*(\\d+))?\\s*");

    /**
     * A strongly connected component in the recipe dependency graph.
     *
     * @param items     the items forming the cycle
     * @param hasAnchor whether the cycle contains a hand-keyed anchor value
     * @param anchor    the anchor item, or null if no anchor
     */
    public record RecipeCycle(List<Identifier> items, boolean hasAnchor, @Nullable Identifier anchor) {}

    /**
     * A disagreement between a hand-keyed base value and a recipe-derived value.
     *
     * @param item        the conflicting item
     * @param baseValue   the hand-keyed base value
     * @param recipeValue the recipe-derived value
     */
    public record ValueConflict(Identifier item, GooValue baseValue, GooValue recipeValue) {
            /**
         * Returns true if the recipe path produces fewer total blobs than the base value.
         *
         * @return true if derived is cheaper than hand-keyed
         */
        public boolean isRecipeCheaper() { return recipeValue.totalBlobs() < baseValue.totalBlobs(); }
    }

    /**
     * A recipe where integer division causes value loss in the output.
     *
     * @param output       the output item
     * @param outputCount  the recipe output count
     * @param inputTotal   the total input value in blobs
     * @param perItemValue the per-item value after division
     * @param lostBlobs    the blobs lost to integer truncation
     * @param recipe       the source recipe input
     */
    public record DivisibilityLoss(Identifier output, int outputCount, int inputTotal,
            int perItemValue, int lostBlobs, RecipeInput recipe) {}

    /**
     * Sets the path for the effective value cache file.
     *
     * @param path the filesystem path for caching effective values
     */
    public void setEffectiveCachePath(Path path) {
        this.effectiveCachePath = path;
    }

    // ── JSON loading / caching ──────────────────────────────────────────

    /**
     * Loads base values from the embedded JSON resource.
     */
    public void loadBaseValues() {
        baseValues.clear();
        deniedItems.clear();
        effectiveValues.clear();
        treeConstants.clear();
        try (InputStream is = GooValueRegistry.class.getResourceAsStream(BASE_VALUES_PATH)) {
            if (is == null) {
                Goo.LOGGER.error(LOG_NO_BASE_FILE);
                return;
            }
            parseBaseValuesFromStream(is);
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_LOAD_FAIL, e);
        }
        effectiveValues.putAll(baseValues);
        if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_LOADED_BASE, baseValues.size()); }
    }

    /**
     * Loads base values by merging all datapack layers via the server's ResourceManager.
     * Each pack's base_values.json is parsed independently, then merged with last-in-wins
     * semantics before applying constants and item values.
     *
     * @param server the running server whose resource manager provides the pack stack
     */
    public void loadBaseValuesFromPacks(MinecraftServer server) {
        clearRegistryState();
        ResourceManager resourceManager = server.getResourceManager();
        Identifier location = Identifier.fromNamespaceAndPath(MOD_NAMESPACE, BASE_VALUES_RESOURCE);
        List<Resource> stack = resourceManager.getResourceStack(location);

        if (stack.isEmpty()) {
            Goo.LOGGER.error(LOG_NO_DATAPACK);
            return;
        }

        List<JsonObject> layers = parseResourceLayers(stack);
        JsonObject merged = mergeBaseValueJsonLayers(layers);
        merged = expandTagEntries(merged, GooValueRegistry::resolveItemTag);
        lastMergedBaseValues = merged;
        parseConstants(merged);
        parseItemValues(merged);
        parseConversions(merged);
        applyConversions(preConversions, baseValues);
        effectiveValues.putAll(baseValues);
        if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_LOADED_PACKS, baseValues.size(), layers.size()); }
    }

    /**
     * Parses each resource in the stack into a JsonObject, skipping failures.
     *
     * @param stack ordered resource list from the pack stack
     * @return parsed JSON objects (failures skipped)
     */
    private List<JsonObject> parseResourceLayers(List<Resource> stack) {
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
     * @param layer the incoming layer to merge
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
     * @param merged the accumulator JSON object
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
     * @param tagKey the #tag key from the JSON
     * @param value the value to assign to each tag member
     * @param tagResolver resolves a tag ID to its member item IDs
     * @param result the accumulator JSON object
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
    private static Set<Identifier> resolveItemTag(Identifier tagId) {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        Set<Identifier> members = new HashSet<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            members.add(BuiltInRegistries.ITEM.getKey(holder.value()));
        }
        return members;
    }

    /** Clears all mutable registry state before a fresh load. */
    private void clearRegistryState() {
        baseValues.clear();
        deniedItems.clear();
        effectiveValues.clear();
        constants.clear();
        treeConstants.clear();
        pseudoTags.clear();
        preConversions = null;
        postConversions = null;
        lastMergedBaseValues = null;
    }

    /**
     * Parses constants, groups, item entries, and conversions from an input stream.
     *
     * @param is the input stream containing base_values.json data
     * @throws IOException if reading the stream fails
     */
    void parseBaseValuesFromStream(InputStream is) throws IOException {
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            parseConstants(json);
            parseItemValues(json);
            parseConversions(json);
            applyConversions(preConversions, baseValues);
        }
    }

    /**
     * Parses the _constants object from the JSON root, if present.
     * Constants can reference earlier constants via $name expressions,
     * so parse order matters (JSON object iteration order).
     *
     * @param json the root JSON object containing the _constants key
     */
    private void parseConstants(JsonObject json) {
        constants.clear();
        treeConstants.clear();
        if (!json.has(CONSTANTS_SUFFIX)) { return; }
        JsonObject obj = json.getAsJsonObject(CONSTANTS_SUFFIX);
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                treeConstants.put(entry.getKey(),
                        GooValueJsonFormat.parseGooValue(entry.getValue().getAsJsonObject(), constants, null, treeConstants));
            } else {
                // Try tree evaluation first (handles "9 $metal_nugget" where $metal_nugget is a tree).
                // Fall back to scalar if the result is empty or the expression has no tree refs.
                String expr = entry.getValue().getAsString().trim();
                if (referencesTreeConstant(expr)) {
                    GooValue tree = GooValueExpression.evaluate(expr, Map.of(), constants, treeConstants);
                    if (!tree.isEmpty()) {
                        treeConstants.put(entry.getKey(), tree);
                        continue;
                    }
                }
                constants.put(entry.getKey(),
                        GooValueJsonFormat.resolveConstantValue(entry.getValue(), constants));
            }
        }
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED_CONSTANTS,
                    constants.size() + treeConstants.size(), constants.size(), treeConstants.size());
        }
    }

    /**
     * True if the expression string contains a $ref that's a known tree constant.
     *
     * @param expr the expression to scan for tree constant references
     * @return true if any $ref resolves to a tree constant
     */
    private boolean referencesTreeConstant(String expr) {
        int i = expr.indexOf('$');
        while (i >= 0 && i < expr.length() - 1) {
            int start = i + 1;
            int end = start;
            while (end < expr.length() && (Character.isLetterOrDigit(expr.charAt(end)) || expr.charAt(end) == '_')) {
                end++;
            }
            if (end > start && treeConstants.containsKey(expr.substring(start, end))) {
                return true;
            }
            i = expr.indexOf('$', end);
        }
        return false;
    }

    /**
     * Parses _groups first (pseudo-tags), then item entries. Resolves #name against pseudo-tags.
     *
     * @param json the root JSON object to extract item values from
     */
    private void parseItemValues(JsonObject json) {
        parseGroups(json);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PREFIX_INTERNAL)) { continue; }
            if (key.startsWith(PREFIX_TAG)) {
                expandPseudoTag(key.substring(1), entry.getValue());
                continue;
            }
            assignItemValue(Identifier.parse(key), entry.getValue());
        }
    }

    /**
     * Expands a #name key against pseudo-tags, assigning the value to each member.
     *
     * @param name the pseudo-tag name (without # prefix)
     * @param value the JSON value to assign to each member
     */
    private void expandPseudoTag(String name, JsonElement value) {
        Set<Identifier> targetMembers = resolvePseudoTag(name);
        if (targetMembers == null || targetMembers.isEmpty()) {
            Goo.LOGGER.warn(LOG_PSEUDO_TAG_EMPTY, name);
            return;
        }

        // Check for parallel copy: "#source * N / M"
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            java.util.regex.Matcher m = PARALLEL_COPY_PATTERN.matcher(value.getAsString().trim());
            if (m.matches()) {
                String sourceName = m.group(1);
                int multiplier = m.group(PARALLEL_COPY_MULTIPLIER_GROUP) != null ? Integer.parseInt(m.group(PARALLEL_COPY_MULTIPLIER_GROUP)) : 1;
                int divisor = m.group(PARALLEL_COPY_DIVISOR_GROUP) != null ? Integer.parseInt(m.group(PARALLEL_COPY_DIVISOR_GROUP)) : 1;
                parallelCopyBaseValues(name, sourceName, targetMembers, multiplier, divisor);
                return;
            }
        }

        for (Identifier member : targetMembers) {
            assignItemValue(member, value);
        }
    }

    /**
     * Resolves a pseudo-tag name to its members, trying exact then parsed path.
     *
     * @param name the pseudo-tag name to resolve
     * @return the set of member item IDs, or null if not found
     */
    private Set<Identifier> resolvePseudoTag(String name) {
        Set<Identifier> members = pseudoTags.get(name);
        if (members == null) {
            members = pseudoTags.get(Identifier.parse(name).getPath());
        }
        return members;
    }

    /**
     * Parallel copy from source tag to target tag, with optional scale, during base value parsing.
     *
     * @param targetName the target pseudo-tag name
     * @param sourceName the source pseudo-tag name to copy from
     * @param targetMembers resolved target item IDs
     * @param multiplier numerator for post-copy scaling
     * @param divisor denominator for post-copy scaling
     */
    private void parallelCopyBaseValues(String targetName, String sourceName,
                                         Set<Identifier> targetMembers,
                                         int multiplier, int divisor) {
        Set<Identifier> sourceMembers = resolvePseudoTag(sourceName);
        if (sourceMembers == null || sourceMembers.isEmpty()) {
            Goo.LOGGER.error(LOG_PARALLEL_SRC_EMPTY, sourceName);
            return;
        }
        List<Identifier> targets = new ArrayList<>(targetMembers);
        List<Identifier> sources = new ArrayList<>(sourceMembers);
        if (targets.size() != sources.size()) {
            if (Goo.LOGGER.isErrorEnabled()) {
                Goo.LOGGER.error(LOG_PARALLEL_MISMATCH,
                        targetName, targets.size(), sourceName, sources.size());
            }
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            copyScaledValue(sources.get(i), targets.get(i), multiplier, divisor);
        }
    }

    /**
     * Copies a single source item's value to a target, applying multiplier/divisor scaling.
     * Logs and skips if source has no value, result is negative, or arithmetic fails.
     *
     * @param source the source item ID
     * @param target the target item ID
     * @param multiplier numerator for scaling
     * @param divisor denominator for scaling
     */
    private void copyScaledValue(Identifier source, Identifier target,
                                  int multiplier, int divisor) {
        GooValue sourceVal = baseValues.get(source);
        if (sourceVal == null || sourceVal.isEmpty()) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_PARALLEL_NO_VALUE, source, target);
            }
            return;
        }
        try {
            GooValue scaled = sourceVal.multiply(multiplier).divideExact(divisor);
            if (scaled.hasNegative()) {
                if (Goo.LOGGER.isErrorEnabled()) { Goo.LOGGER.error(LOG_PARALLEL_NEGATIVE, target, scaled); }
                return;
            }
            baseValues.put(target, scaled);
        } catch (ArithmeticException e) {
            if (Goo.LOGGER.isErrorEnabled()) {
                Goo.LOGGER.error(LOG_PARALLEL_SCALE_FAIL, target, source, e.getMessage());
            }
        }
    }

    /**
     * Assigns a value or denial to a single item.
     *
     * @param itemId the item to assign a value to
     * @param value the JSON value (object for explicit, string for expression, "denied")
     */
    private void assignItemValue(Identifier itemId, JsonElement value) {
        if (isDeniedEntry(value)) {
            deniedItems.add(itemId);
        } else {
            GooValue resolved = resolveItemEntry(value);
            if (resolved.hasNegative()) {
                Goo.LOGGER.error(LOG_NEGATIVE_BASE, itemId, resolved);
            } else {
                baseValues.put(itemId, resolved);
            }
        }
    }

    /**
     * Resolves a non-denied item entry: JSON object -> explicit GooValue,
     * string -> item reference expression.
     *
     * @param value the JSON element to resolve
     * @return the parsed GooValue
     */
    private GooValue resolveItemEntry(JsonElement value) {
        if (value.isJsonObject()) {
            return GooValueJsonFormat.parseGooValue(value.getAsJsonObject(), constants, baseValues, treeConstants);
        }
        // String expression referencing other items
        return GooValueExpression.evaluate(value.getAsString().trim(), baseValues, constants, treeConstants);
    }

    /**
     * Parses _groups into pseudo-tags: each key maps to an array of item IDs.
     *
     * @param json the root JSON object containing the _groups key
     */
    private void parseGroups(JsonObject json) {
        if (!json.has(GROUPS_SUFFIX)) { return; }
        JsonObject groups = json.getAsJsonObject(GROUPS_SUFFIX);
        for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
            String name = group.getKey();
            com.google.gson.JsonArray items = group.getValue().getAsJsonArray();
            Set<Identifier> members = new LinkedHashSet<>();
            for (JsonElement item : items) {
                members.add(Identifier.parse(item.getAsString()));
            }
            pseudoTags.put(name, members);
        }
    }

    /**
     * Parses both _conversions and _post_conversions blocks.
     *
     * @param json the root JSON object containing conversion blocks
     */
    private void parseConversions(JsonObject json) {
        preConversions = parseConversionBlock(json, KEY_CONVERSIONS);
        postConversions = parseConversionBlock(json, KEY_POST_CONVERSIONS);
    }

    /**
     * Parses a single conversion block by key name, with constant resolution.
     *
     * @param json the root JSON object
     * @param key the block key (e.g. _conversions)
     * @return parsed conversions, or null if the block is absent
     */
    private GooConversion.ParsedConversions parseConversionBlock(JsonObject json, String key) {
        if (!json.has(key)) { return null; }
        JsonObject block = json.getAsJsonObject(key);
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
            entries.put(entry.getKey(), entry.getValue().getAsString());
        }
        return GooConversion.parseBlock(entries, constants, treeConstants);
    }

    /**
     * Applies a parsed conversion set to a values map.
     *
     * @param parsed the parsed conversions to apply (may be null)
     * @param values the mutable values map to modify
     */
    private void applyConversions(GooConversion.ParsedConversions parsed,
                                   Map<Identifier, GooValue> values) {
        if (parsed == null) { return; }
        for (GooConversion.Assignment assignment : parsed.assignments()) {
            applyOneConversion(assignment, parsed, values);
        }
    }

    /**
     * Resolves targets/sources for a single conversion assignment and applies it.
     * Logs warnings when targets or parallel sources resolve to no items.
     *
     * @param assignment the conversion assignment to apply
     * @param parsed the full parsed conversion set (for formulas/additives)
     * @param values the mutable values map to modify
     */
    private void applyOneConversion(GooConversion.Assignment assignment,
                                     GooConversion.ParsedConversions parsed,
                                     Map<Identifier, GooValue> values) {
        List<Identifier> targetItems = resolveConversionTarget(assignment.target());
        if (targetItems.isEmpty()) {
            if (Goo.LOGGER.isWarnEnabled()) { Goo.LOGGER.warn(LOG_CONV_TARGET_EMPTY, assignment.target()); }
            return;
        }
        List<Identifier> sourceItems = resolveParallelSource(assignment);
        if (assignment.parallelSource() != null && sourceItems == null) { return; }
        GooConversion.applyAssignment(values, targetItems, sourceItems,
                assignment, parsed.formulas(), parsed.additives());
    }

    /**
     * Resolves the parallel source for a conversion assignment. Returns null when
     * no parallel source is configured (normal non-parallel case) or when the
     * source cannot be resolved (logged as warning -- caller should bail).
     *
     * @param assignment the conversion assignment
     * @return resolved source items, or null if no source or resolution failed
     */
    private List<Identifier> resolveParallelSource(GooConversion.Assignment assignment) {
        if (assignment.parallelSource() == null) { return null; }
        List<Identifier> sourceItems = resolveConversionTarget(assignment.parallelSource());
        if (sourceItems.isEmpty()) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_CONV_SOURCE_EMPTY, assignment.parallelSource());
            }
            return null;
        }
        return sourceItems;
    }

    /**
     * Resolves a conversion target (item ID or #tag) to an ordered list of item IDs.
     *
     * @param target the target string (item ID or #tag reference)
     * @return ordered list of resolved item IDs
     */
    private List<Identifier> resolveConversionTarget(String target) {
        if (target.startsWith(PREFIX_TAG)) {
            String name = target.substring(1);
            Set<Identifier> members = pseudoTags.get(name);
            if (members == null) {
                members = pseudoTags.get(Identifier.parse(name).getPath());
            }
            return members != null ? new ArrayList<>(members) : List.of();
        }
        return List.of(Identifier.parse(target));
    }

    /**
     * Returns true if the JSON value is the string "denied".
     *
     * @param element the JSON element to check
     * @return true if the element is the string "denied"
     */
    private boolean isDeniedEntry(JsonElement element) {
        return element.isJsonPrimitive() && VALUE_DENIED.equals(element.getAsString());
    }

    /**
     * Loads effective values from the flat cache file. The cache contains
     * pre-resolved integer values per goo type, so no expression evaluation
     * or base value merging is needed.
     */
    public void loadEffectiveCache() {
        if (effectiveCachePath == null || !Files.exists(effectiveCachePath)) { return; }

        try (Reader reader = Files.newBufferedReader(effectiveCachePath, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            effectiveValues.clear();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                Identifier itemId = Identifier.parse(entry.getKey());
                effectiveValues.put(itemId, GooValueJsonFormat.parseGooValue(entry.getValue().getAsJsonObject()));
            }
            if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_LOADED_CACHE, effectiveValues.size()); }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Goo.LOGGER.warn(LOG_CACHE_LOAD_FAIL, e);
        }
    }

    /**
     * Saves the complete effective value map to the cache file.
     * Written by /goo regen so startup can load a flat, pre-resolved file.
     */
    public void saveEffectiveValues() {
        if (effectiveCachePath == null) { return; }

        try {
            Files.createDirectories(effectiveCachePath.getParent());
            JsonObject json = serializeValues(effectiveValues);
            Files.writeString(effectiveCachePath, GSON.toJson(json), StandardCharsets.UTF_8);
            if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_SAVED_CACHE, effectiveValues.size()); }
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_CACHE_SAVE_FAIL, e);
        }
    }

    /**
     * Serializes a value map to a sorted JSON object.
     *
     * @param derivedValues the values to serialize
     * @return sorted JSON object with item IDs as keys
     */
    private JsonObject serializeValues(Map<Identifier, GooValue> derivedValues) {
        JsonObject json = new JsonObject();
        derivedValues.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> json.add(e.getKey().toString(), GooValueJsonFormat.toJson(e.getValue())));
        return json;
    }

    // ── Minecraft adapter layer ─────────────────────────────────────────

    /**
     * Derives goo values from all server recipes using the LCD rule.
     * Thin adapter: converts MC recipes to {@link RecipeInput} then delegates to pure logic.
     *
     * @param server the running server providing recipes
     * @return the number of items that received derived values
     */
    public int deriveFromRecipes(MinecraftServer server) {
        HolderLookup.Provider registries = server.registryAccess();
        lastRecipes = adaptRecipes(server.getRecipeManager().getRecipes(), registries);
        boolean baseOverride = GooConfig.BASE_VALUES_OVERRIDE_RECIPES.get();
        int derived = deriveFromRecipeInputs(lastRecipes, baseOverride);
        Goo.LOGGER.info(LOG_DERIVED, derived);
        return derived;
    }

    /**
     * Converts MC RecipeHolders into MC-free RecipeInputs.
     *
     * @param holders the MC recipe holders to adapt
     * @param registries the registry access for result assembly
     * @return list of adapted RecipeInput records
     */
    private List<RecipeInput> adaptRecipes(Collection<RecipeHolder<?>> holders,
            HolderLookup.Provider registries) {
        List<RecipeInput> result = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            RecipeInput adapted = adaptOneRecipe(holder.value(), registries);
            if (adapted != null) {
                result.add(adapted);
            }
        }
        return result;
    }

    /**
     * Converts a single MC recipe to a RecipeInput, or null if unsupported.
     *
     * @param recipe the MC recipe to adapt
     * @param registries the registry access for result assembly
     * @return the adapted RecipeInput, or null if unsupported
     */
    @Nullable
    private RecipeInput adaptOneRecipe(Recipe<?> recipe, HolderLookup.Provider registries) {
        ItemStack resultStack = getRecipeResult(recipe, registries);
        if (resultStack == null || resultStack.isEmpty()) { return null; }

        Identifier outputId = BuiltInRegistries.ITEM.getKey(resultStack.getItem());
        AdaptedIngredients adapted = adaptIngredients(recipe);
        Map<Identifier, Identifier> containers = adaptContainerItems(recipe);
        return new RecipeInput(outputId, resultStack.getCount(),
                adapted.slots(), containers, adapted.tagIds());
    }

    /**
     * Builds a map of ingredient item ID to its crafting remainder item ID.
     *
     * @param recipe the MC recipe to inspect
     * @return map of ingredient to its crafting remainder
     */
    private Map<Identifier, Identifier> adaptContainerItems(Recipe<?> recipe) {
        Map<Identifier, Identifier> containers = new HashMap<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) { continue; }
            collectContainersFromIngredient(ingredient, containers);
        }
        return containers;
    }

    /**
     * Inspects each alternative in an ingredient for a crafting remainder.
     *
     * @param ingredient the ingredient to inspect
     * @param containers accumulator for ingredient-to-remainder mappings
     */
    private void collectContainersFromIngredient(Ingredient ingredient,
            Map<Identifier, Identifier> containers) {
        for (Holder<Item> holder : resolveIngredientItems(ingredient)) {
            ItemStackTemplate remainder = holder.value().getCraftingRemainder();
            if (remainder != null) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(holder.value());
                Identifier containerId = BuiltInRegistries.ITEM.getKey(remainder.item().value());
                containers.put(itemId, containerId);
            }
        }
    }

    /** Ingredient slots paired with per-slot tag identity from the recipe's HolderSet. */
    private record AdaptedIngredients(
            List<Set<Identifier>> slots,
            List<Optional<Identifier>> tagIds
    ) {}

    /**
     * Converts MC Ingredients to sets of item Identifiers, capturing tag identity per slot.
     *
     * @param recipe the MC recipe to extract ingredients from
     * @return adapted ingredient slots with tag identity metadata
     */
    private AdaptedIngredients adaptIngredients(Recipe<?> recipe) {
        List<Set<Identifier>> slots = new ArrayList<>();
        List<Optional<Identifier>> tagIds = new ArrayList<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) { continue; }
            Set<Identifier> alternatives = adaptOneIngredient(ingredient);
            if (!alternatives.isEmpty()) {
                slots.add(alternatives);
                tagIds.add(extractTagId(ingredient));
            }
        }
        return new AdaptedIngredients(slots, tagIds);
    }

    /**
     * Extracts the tag identity from a standard ingredient's HolderSet, if present.
     *
     * @param ingredient the ingredient to extract a tag from
     * @return the tag ID if present, otherwise empty
     */
    private Optional<Identifier> extractTagId(Ingredient ingredient) {
        if (ingredient.isCustom()) { return Optional.empty(); }
        HolderSet<Item> holderSet = ingredient.getValues();
        return holderSet.unwrapKey()
                .map(TagKey::location);
    }

    /**
     * Resolves a single MC Ingredient to its set of item IDs.
     *
     * @param ingredient the ingredient to resolve
     * @return set of alternative item IDs
     */
    private Set<Identifier> adaptOneIngredient(Ingredient ingredient) {
        Set<Identifier> alternatives = new HashSet<>();
        for (Holder<Item> holder : resolveIngredientItems(ingredient)) {
            alternatives.add(BuiltInRegistries.ITEM.getKey(holder.value()));
        }
        return alternatives;
    }

    /**
     * Resolves an ingredient's item holders, using getValues() for standard ingredients.
     *
     * @param ingredient the ingredient to resolve
     * @return list of item holders for the ingredient
     */
    private List<Holder<Item>> resolveIngredientItems(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return ingredient.getCustomIngredient().items().toList();
        }
        return ingredient.getValues().stream().toList();
    }

    /**
     * Gets the result ItemStack from a recipe using the public assemble() API.
     * Vanilla implementations ignore the input and return a copy of the stored result.
     *
     * @param recipe the MC recipe to get the result from
     * @param registries the registry access for assembly context
     * @return the result ItemStack, or null if unsupported
     */
    @SuppressWarnings(SUPPRESS_UNCHECKED)
    static ItemStack getRecipeResult(Recipe<?> recipe, HolderLookup.Provider registries) {
        try {
            // TransmuteRecipe.assemble needs real input items; read the result field directly.
            if (recipe instanceof TransmuteRecipe transmute) {
                return transmute.result.apply(DataComponentPatch.EMPTY);
            } else if (recipe instanceof CraftingRecipe crafting) {
                return crafting.assemble(CraftingInput.EMPTY);
            } else if (recipe instanceof SingleItemRecipe single) {
                return single.assemble(new SingleRecipeInput(ItemStack.EMPTY));
            }
            if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_UNSUPPORTED_RECIPE, recipe.getClass().getName()); }
        } catch (RuntimeException e) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_RECIPE_RESULT_FAIL,
                    recipe.getClass().getSimpleName(), e.getMessage());
            }
        }
        return null;
    }

    // ── Pure derivation logic (no MC dependency) ────────────────────────

    /**
     * Derives goo values from MC-free recipe inputs using the LCD rule.
     * Delegates to {@link GooValueDerivation} and applies the result to registry state.
     *
     * @param recipes      all recipes to consider
     * @param baseOverride when true, base values always win over derived values
     * @return the number of items that received derived values
     */
    int deriveFromRecipeInputs(List<RecipeInput> recipes, boolean baseOverride) {
        lastDerivation = GooValueDerivation.derive(recipes, baseValues, deniedItems, baseOverride);
        effectiveValues.clear();
        effectiveValues.putAll(lastDerivation.effectiveValues());
        applyConversions(postConversions, effectiveValues);
        return lastDerivation.derivedValues().size();
    }

    /**
     * Returns the item ID with the lowest {@link GooValue#totalBlobs()} among {@code candidates},
     * using {@code lookup} to resolve each ID. Null and empty values are skipped.
     *
     * @param candidates set of item IDs to compare
     * @param lookup     function from item ID to GooValue (may return null)
     * @return cheapest item ID, or null if no candidate has a non-empty value
     */
    public static @Nullable Identifier findCheapestAmong(
            Set<Identifier> candidates, Function<Identifier, GooValue> lookup) {
        Identifier cheapestId = null;
        int cheapestTotal = Integer.MAX_VALUE;
        for (Identifier itemId : candidates) {
            GooValue val = lookup.apply(itemId);
            if (val != null && !val.isEmpty() && val.totalBlobs() < cheapestTotal) {
                cheapestTotal = val.totalBlobs();
                cheapestId = itemId;
            }
        }
        return cheapestId;
    }

    // ── Public accessors ────────────────────────────────────────────────

    /**
     * Looks up the effective goo value for an item (LCD-resolved).
     *
     * @param itemId the item's registry ID
     * @return effective GooValue, or null
     */
    @Override
    public GooValue lookup(Identifier itemId) {
        return effectiveValues.get(itemId);
    }

    /**
     * Looks up the goo value for an item stack. Falls back to component-based
     * value computation if the item implements {@link IComponentValueProvider}.
     *
     * @param stack the item stack to look up
     * @return effective GooValue, or null if none
     */
    public GooValue lookup(ItemStack stack) {
        if (stack.isEmpty()) { return null; }
        GooValue base = lookup(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (base != null) { return base; }
        if (stack.getItem() instanceof IComponentValueProvider provider) {
            return provider.computeComponentValue(stack, this);
        }
        return null;
    }

    /**
     * Returns the total number of items with effective goo values.
     *
     * @return effective value count
     */
    @Override
    public int size() {
        return effectiveValues.size();
    }

    /**
     * Returns the number of hand-keyed base values.
     *
     * @return base value count
     */
    public int baseSize() {
        return baseValues.size();
    }

    /**
     * Returns the number of recipe-derived values.
     *
     * @return derived value count
     */
    public int derivedSize() {
        return lastDerivation != null ? lastDerivation.derivedValues().size() : 0;
    }

    /**
     * Returns the last detected recipe dependency cycles.
     *
     * @return list of recipe cycles, empty if no derivation run yet
     */
    public List<RecipeCycle> getLastCycles() {
        return lastDerivation != null ? lastDerivation.cycles() : List.of();
    }

    /**
     * Returns the last detected base/derived value conflicts.
     *
     * @return list of value conflicts, empty if no derivation run yet
     */
    public List<ValueConflict> getLastConflicts() {
        return lastDerivation != null ? lastDerivation.conflicts() : List.of();
    }

    /**
     * Returns the last detected divisibility losses.
     *
     * @return list of divisibility losses, empty if no derivation run yet
     */
    public List<DivisibilityLoss> getLastDivisibilityLosses() {
        return lastDerivation != null ? lastDerivation.divisibilityLosses() : List.of();
    }

    /**
     * Returns true if the item has a hand-keyed base value.
     *
     * @param itemId the item's registry ID
     * @return true if the item has a hand-keyed base value
     */
    @Override
    public boolean hasBaseValue(Identifier itemId) {
        return baseValues.containsKey(itemId);
    }

    /**
     * Returns true if the item is on the deny list.
     *
     * @param itemId the item's registry ID
     * @return true if the item is denied
     */
    @Override
    public boolean isDenied(Identifier itemId) {
        return deniedItems.contains(itemId);
    }

    /**
     * Returns all identifiers referenced in base_values.json (valued + denied).
     *
     * @return unmodifiable set of all referenced item IDs
     */
    public Set<Identifier> getAllReferencedIds() {
        Set<Identifier> all = new HashSet<>(baseValues.keySet());
        all.addAll(deniedItems);
        return Collections.unmodifiableSet(all);
    }

    /**
     * Returns the recipe that produced the derived value for an item, or null.
     *
     * @param itemId the item to look up
     * @return the source recipe, or null if not derived
     */
    @Nullable
    public RecipeInput getDerivationSource(Identifier itemId) {
        return lastDerivation != null ? lastDerivation.derivationSources().get(itemId) : null;
    }

    /**
     * Reloads effective values from the cache file.
     */
    public void reload() {
        loadEffectiveCache();
    }

    // ── Test support (package-private) ──────────────────────────────────

    /**
     * Sets denied items directly, bypassing JSON loading. For test use only.
     *
     * @param items the denied item IDs to set
     */
    void setDeniedItems(Set<Identifier> items) {
        deniedItems.clear();
        deniedItems.addAll(items);
    }

    /**
     * Sets base values directly, bypassing JSON loading. For test use only.
     *
     * @param values the base values to set
     */
    void setBaseValues(Map<Identifier, GooValue> values) {
        baseValues.clear();
        baseValues.putAll(values);
        effectiveValues.clear();
        effectiveValues.putAll(values);
    }

    /**
     * Returns a snapshot of current derived values. For test assertions.
     *
     * @return immutable map of derived values
     */
    Map<Identifier, GooValue> getDerivedValues() {
        return lastDerivation != null ? lastDerivation.derivedValues() : Map.of();
    }

    /**
     * Returns a snapshot of current effective values.
     *
     * @return unmodifiable map of effective values
     */
    @Override
    public Map<Identifier, GooValue> getEffectiveValues() {
        return Collections.unmodifiableMap(effectiveValues);
    }

    /**
     * Replaces effective values wholesale with server-provided data.
     * Used on the client side to receive synced values over the network.
     *
     * @param values the server-synced effective values
     */
    public void receiveClientValues(Map<Identifier, GooValue> values) {
        effectiveValues.clear();
        effectiveValues.putAll(values);
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_CLIENT_RECEIVED, values.size()); }
    }

    /**
     * Generates scaffold by collecting recipes fresh from the server.
     * Does not require a prior regen.
     *
     * @param server the running server providing recipes
     * @return scaffold result with lines and root count
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldFresh(MinecraftServer server) {
        return generateScaffoldFresh(server, false);
    }

    /**
     * Generates scaffold by collecting recipes fresh from the server.
     *
     * @param server the running server providing recipes
     * @param bare if true, emit only root keys with empty values
     * @return scaffold result with lines and root count
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldFresh(MinecraftServer server, boolean bare) {
        HolderLookup.Provider registries = server.registryAccess();
        List<RecipeInput> recipes = adaptRecipes(server.getRecipeManager().getRecipes(), registries);
        // Use only base values -- conversion targets are not pre-resolved, so the
        // scaffold's boolean propagation catches items whose chains actually break.
        Set<Identifier> allItems = BuiltInRegistries.ITEM.keySet();
        List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                recipes, baseValues, deniedItems, allItems);
        return ScaffoldGenerator.generateScaffold(roots, recipes, bare);
    }

    /**
     * Generates scaffold from cached recipes with default (verbose) output.
     *
     * @return scaffold result with lines and root count
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldMissing() {
        return generateScaffoldMissing(false);
    }

    /**
     * Generates scaffold from cached recipes (requires a prior regen or load).
     * Shows only roots that are still missing after the last derivation pass.
     * Uses effective values (post-derivation, post-conversion) for completeness.
     *
     * @param bare if true, emit only root keys with empty values
     * @return scaffold result with lines and root count
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldMissing(boolean bare) {
        Set<Identifier> allItems = BuiltInRegistries.ITEM.keySet();
        List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                lastRecipes, effectiveValues, deniedItems, allItems);
        return ScaffoldGenerator.generateScaffold(roots, lastRecipes, bare);
    }

    /**
     * Copies base values into effective values. For test use after parseBaseValuesFromStream.
     */
    void copyBaseToEffective() {
        effectiveValues.putAll(baseValues);
        applyConversions(postConversions, effectiveValues);
    }

    /**
     * Validates the last-loaded base_values.json for authoring mistakes.
     * Uses the merged JSON from the most recent regen. Returns a warning
     * if no regen has been run yet.
     *
     * @return list of validation warnings
     */
    public List<String> validateBaseValues() {
        List<String> warnings = new ArrayList<>();
        if (lastMergedBaseValues == null) {
            warnings.add(WARN_NO_BASE_VALUES);
            return warnings;
        }
        validateJson(lastMergedBaseValues, warnings);
        return warnings;
    }

    /**
     * Validates a JSON string for expression mistakes. For testing.
     *
     * @param jsonString the JSON string to validate
     * @return list of validation warnings
     */
    List<String> validateJsonString(String jsonString) {
        List<String> warnings = new ArrayList<>();
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        validateJson(json, warnings);
        return warnings;
    }

    /**
     * Walks the JSON checking expression tokens for mistakes.
     *
     * @param json the root JSON object to validate
     * @param warnings accumulator for validation warnings
     */
    private void validateJson(JsonObject json, List<String> warnings) {
        Set<String> knownConstants = validateConstantsSection(json, warnings);
        Set<String> knownItems = validateBaseSection(json, knownConstants, warnings);
        validateGroupsSection(json, knownItems, warnings);
        validateConversionsSection(json, knownConstants, warnings);
    }

    /**
     * Validates the _constants block: checks expression tokens within each constant definition.
     *
     * @param json the root JSON object
     * @param warnings accumulator for validation warnings
     * @return the set of known constant names, in definition order
     */
    private Set<String> validateConstantsSection(JsonObject json, List<String> warnings) {
        Set<String> knownConstants = new LinkedHashSet<>();
        if (!json.has(CONSTANTS_SUFFIX)) { return knownConstants; }
        JsonObject obj = json.getAsJsonObject(CONSTANTS_SUFFIX);
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                validateExprTokens(entry.getValue().getAsString(), knownConstants,
                        new LinkedHashSet<>(), CTX_CONSTANTS + entry.getKey(), warnings);
            }
            knownConstants.add(entry.getKey());
        }
        return knownConstants;
    }

    /**
     * Validates individual item entries (non-internal, non-tag keys) for expression correctness.
     *
     * @param json the root JSON object
     * @param knownConstants known constant names from the constants section
     * @param warnings accumulator for validation warnings
     * @return the set of known item keys, in definition order
     */
    private Set<String> validateBaseSection(JsonObject json, Set<String> knownConstants,
                                             List<String> warnings) {
        Set<String> knownItems = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getKey().startsWith(PREFIX_INTERNAL) || entry.getKey().startsWith(PREFIX_TAG)) { continue; }
            String itemKey = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                validateValueObject(value.getAsJsonObject(), knownConstants, knownItems, itemKey, warnings);
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                       && !VALUE_DENIED.equals(value.getAsString())) {
                validateExprTokens(value.getAsString(), knownConstants, knownItems, itemKey, warnings);
            }
            knownItems.add(itemKey);
        }
        return knownItems;
    }

    /**
     * Validates the _groups block: checks that each group is an array, and registers members as known items.
     *
     * @param json the root JSON object
     * @param knownItems mutable set of known item keys (group members are added)
     * @param warnings accumulator for validation warnings
     */
    private void validateGroupsSection(JsonObject json, Set<String> knownItems, List<String> warnings) {
        if (!json.has(GROUPS_SUFFIX)) { return; }
        JsonObject groups = json.getAsJsonObject(GROUPS_SUFFIX);
        for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
            if (!group.getValue().isJsonArray()) {
                warnings.add(CTX_GROUPS + group.getKey() + WARN_EXPECTED_ARRAY);
                continue;
            }
            com.google.gson.JsonArray items = group.getValue().getAsJsonArray();
            for (JsonElement item : items) {
                knownItems.add(item.getAsString());
            }
        }
    }

    /**
     * Validates both _conversions and _post_conversions blocks.
     *
     * @param json the root JSON object
     * @param knownConstants known constant names for reference validation
     * @param warnings accumulator for validation warnings
     */
    private void validateConversionsSection(JsonObject json, Set<String> knownConstants,
                                             List<String> warnings) {
        validateConversionBlock(json, KEY_CONVERSIONS, knownConstants, warnings);
        validateConversionBlock(json, KEY_POST_CONVERSIONS, knownConstants, warnings);
    }

    /**
     * Validates a conversion block for unknown @refs and malformed formulas.
     *
     * @param json the root JSON object containing the block
     * @param blockKey the key of the conversion block
     * @param knownConstants known constant names for reference validation
     * @param warnings accumulator for validation warnings
     */
    private void validateConversionBlock(JsonObject json, String blockKey,
                                          Set<String> knownConstants, List<String> warnings) {
        if (!json.has(blockKey)) { return; }
        JsonObject block = json.getAsJsonObject(blockKey);
        Set<String> knownRefs = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().isJsonPrimitive()) {
                warnings.add(blockKey + DOT + key + WARN_EXPECTED_STRING);
                continue;
            }
            String value = entry.getValue().getAsString();
            if (VALUE_DENIED.equals(value)) { continue; }
            String ctx = blockKey + DOT + key;
            if (value.contains(FORMULA_ARROW)) {
                validateFormulaEntry(ctx, value, warnings);
                knownRefs.add(key);
            } else if (value.startsWith(ADDITIVE_PREFIX)) {
                validateAdditiveEntry(ctx, value, knownConstants, warnings);
                knownRefs.add(key);
            } else if (value.contains(AT_PREFIX)) {
                validateRefEntry(ctx, key, value, knownRefs, warnings);
            }
        }
    }

    /**
     * Validates a formula conversion entry (contains "->") by attempting to parse it.
     *
     * @param ctx the context path for error messages
     * @param value the formula string
     * @param warnings accumulator for validation warnings
     */
    private void validateFormulaEntry(String ctx, String value, List<String> warnings) {
        try {
            GooConversion.parseFormula(value);
        } catch (IllegalArgumentException e) {
            warnings.add(ctx + WARN_INVALID_FORMULA + e.getMessage());
        }
    }

    /**
     * Validates an additive conversion entry (starts with "+") for constant reference correctness.
     *
     * @param ctx the context path for error messages
     * @param value the additive string (e.g. "+$iron")
     * @param knownConstants known constant names
     * @param warnings accumulator for validation warnings
     */
    private void validateAdditiveEntry(String ctx, String value, Set<String> knownConstants,
                                        List<String> warnings) {
        String ref = value.substring(1).trim();
        if (ref.startsWith(DOLLAR_PREFIX) && !knownConstants.contains(ref.substring(1))) {
            warnings.add(ctx + WARN_UNKNOWN_CONST + ref);
        }
    }

    /**
     * Validates a ref-based conversion entry (contains "@") for unknown @references.
     * Registers the key as a known ref if it is not a tag or namespaced identifier.
     *
     * @param ctx the context path for error messages
     * @param key the conversion entry key
     * @param value the conversion value string
     * @param knownRefs mutable set of known conversion reference names
     * @param warnings accumulator for validation warnings
     */
    private void validateRefEntry(String ctx, String key, String value,
                                   Set<String> knownRefs, List<String> warnings) {
        for (String part : value.trim().split(WHITESPACE_REGEX)) {
            if (part.startsWith(AT_PREFIX)) {
                String refName = part.substring(1).replaceAll(LEADING_DIGITS_REGEX, EMPTY);
                if (!knownRefs.contains(refName)) {
                    warnings.add(ctx + WARN_UNKNOWN_CONV_REF + refName);
                }
            }
        }
        if (!key.startsWith(PREFIX_TAG) && !key.contains(COLON)) {
            knownRefs.add(key);
        }
    }

    /**
     * Validates per-type expressions in a value object like { "metal": "$iron * 3" }.
     *
     * @param obj the value JSON object to validate
     * @param knownConstants known constant names
     * @param knownItems known item IDs (defined before this entry)
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    private void validateValueObject(JsonObject obj, Set<String> knownConstants,
                                     Set<String> knownItems, String context,
                                     List<String> warnings) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                validateExprTokens(entry.getValue().getAsString(), knownConstants, knownItems,
                        context + DOT + entry.getKey(), warnings);
            }
        }
    }

    /**
     * Checks expression tokens for forgotten $ prefixes, unknown constants, and out-of-order items.
     *
     * @param expr the expression string to validate
     * @param knownConstants known constant names
     * @param knownItems known item IDs (defined before this entry)
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    private void validateExprTokens(String expr, Set<String> knownConstants,
                                    Set<String> knownItems, String context,
                                    List<String> warnings) {
        List<String> tokens = GooValueExpression.tokenize(expr);
        for (String token : tokens) {
            if (isOperatorToken(token)) { continue; }

            if (token.startsWith(DOLLAR_PREFIX)) {
                validateConstantToken(token, knownConstants, context, warnings);
            } else if (token.contains(COLON)) {
                validateNamespacedToken(token, knownItems, context, warnings);
            } else if (!Character.isDigit(token.charAt(0))) {
                validateBareWordToken(token, knownConstants, knownItems, context, warnings);
            }
        }
    }

    /**
     * Returns true if the token is a single-character operator or parenthesis.
     *
     * @param token the token to check
     * @return true if operator/paren
     */
    private boolean isOperatorToken(String token) {
        return token.length() == 1 && OPERATOR_CHARS.contains(token);
    }

    /**
     * Validates a $-prefixed constant token, stripping dot-notation suffixes for lookup.
     *
     * @param token the constant token (e.g. "$food.vital")
     * @param knownConstants known constant names
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    private void validateConstantToken(String token, Set<String> knownConstants,
                                        String context, List<String> warnings) {
        String name = token.substring(1);
        int dot = name.indexOf('.');
        String baseName = dot >= 0 ? name.substring(0, dot) : name;
        if (!knownConstants.contains(baseName)) {
            warnings.add(context + WARN_UNKNOWN_CONST_DOLLAR + name);
        }
    }

    /**
     * Validates a namespaced item reference token (contains ":"), stripping .type suffixes for lookup.
     *
     * @param token the namespaced token (e.g. "minecraft:iron_ingot.metal")
     * @param knownItems known item IDs defined before this entry
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    private void validateNamespacedToken(String token, Set<String> knownItems,
                                          String context, List<String> warnings) {
        String itemId = token;
        int colonIdx = token.indexOf(':');
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx > colonIdx && dotIdx < token.length() - 1) {
            itemId = token.substring(0, dotIdx);
        }
        if (!knownItems.contains(itemId)) {
            warnings.add(context + WARN_REFERENCES + itemId + WARN_NOT_DEFINED);
        }
    }

    /**
     * Validates a bare word token: checks if it is a known item (with minecraft: prefix),
     * a forgotten $-prefixed constant, or an unrecognized token.
     *
     * @param token the bare word token
     * @param knownConstants known constant names
     * @param knownItems known item IDs defined before this entry
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    private void validateBareWordToken(String token, Set<String> knownConstants,
                                        Set<String> knownItems, String context,
                                        List<String> warnings) {
        String bareItem = token;
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < token.length() - 1) {
            bareItem = token.substring(0, dotIdx);
        }
        String qualifiedItem = bareItem.contains(COLON) ? bareItem : DEFAULT_NS_PREFIX + bareItem;
        if (!knownItems.contains(qualifiedItem) && !knownItems.contains(bareItem)) {
            if (knownConstants.contains(token)) {
                warnings.add(context + WARN_MISSING_DOLLAR_PREFIX + token + WARN_MISSING_DOLLAR_MID + token + WARN_MISSING_DOLLAR_SUFFIX);
            } else {
                warnings.add(context + WARN_UNRECOGNIZED_PREFIX + token + WARN_UNRECOGNIZED_SUFFIX);
            }
        }
    }

    /**
     * Clears all internal state. Used on client disconnect to prevent stale data.
     */
    public void clearAll() {
        baseValues.clear();
        effectiveValues.clear();
        deniedItems.clear();
        constants.clear();
        treeConstants.clear();
        lastDerivation = null;
        lastMergedBaseValues = null;
    }

}
