package com.mercuriusxeno.goo.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
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
import org.jspecify.annotations.Nullable;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
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
    static final int MAX_DERIVATION_PASSES = 20;

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

    /** A strongly connected component in the recipe dependency graph. */
    public record RecipeCycle(List<Identifier> items, boolean hasAnchor, @Nullable Identifier anchor) {}

    /** A disagreement between a hand-keyed base value and a recipe-derived value. */
    public record ValueConflict(Identifier item, GooValue baseValue, GooValue recipeValue) {
        /** Returns true if the recipe path produces fewer total blobs than the base value. */
        public boolean isRecipeCheaper() { return recipeValue.totalBlobs() < baseValue.totalBlobs(); }
    }

    /** A recipe where integer division causes value loss in the output. */
    public record DivisibilityLoss(Identifier output, int outputCount, int inputTotal,
            int perItemValue, int lostBlobs, RecipeInput recipe) {}

    /** Sets the path for the effective value cache file. */
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
                Goo.LOGGER.error("Could not find base_values.json");
                return;
            }
            parseBaseValuesFromStream(is);
        } catch (IOException e) {
            Goo.LOGGER.error("Failed to load base goo values", e);
        }
        effectiveValues.putAll(baseValues);
        Goo.LOGGER.info("Loaded {} base goo values", baseValues.size());
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
        Identifier location = Identifier.fromNamespaceAndPath("goo", "goo_values/base_values.json");
        List<Resource> stack = resourceManager.getResourceStack(location);

        if (stack.isEmpty()) {
            Goo.LOGGER.error("No datapack provides goo_values/base_values.json");
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
        Goo.LOGGER.info("Loaded {} base goo values from {} pack(s)", baseValues.size(), layers.size());
    }

    /** Parses each resource in the stack into a JsonObject, skipping failures. */
    private List<JsonObject> parseResourceLayers(List<Resource> stack) {
        List<JsonObject> layers = new ArrayList<>();
        for (Resource resource : stack) {
            try (BufferedReader reader = resource.openAsReader()) {
                layers.add(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException e) {
                Goo.LOGGER.warn("Failed to read base_values.json from pack {}: {}",
                        resource.sourcePackId(), e.getMessage());
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

    /** Applies one layer's entries onto the merged result. */
    private static void mergeOneLayer(JsonObject merged, JsonObject layer) {
        for (Map.Entry<String, JsonElement> entry : layer.entrySet()) {
            String key = entry.getKey();
            if ("_constants".equals(key) || "_groups".equals(key)) {
                mergeNestedObject(merged, key, entry.getValue().getAsJsonObject());
            } else {
                merged.add(key, entry.getValue());
            }
        }
    }

    /** Merges inner keys of a nested object (constants or groups) at key level. */
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
            if (key.startsWith("#")) {
                expandOneTag(key, entry.getValue(), tagResolver, result);
            } else {
                result.add(key, entry.getValue());
            }
        }
        return result;
    }

    /** Expands a MC tag key into per-member entries. Preserves unresolved keys for pseudo-tag handling. */
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

    /** Resolves an item tag to the set of item identifiers it contains. */
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

    /** Parses constants, groups, item entries, and conversions from an input stream. */
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
     */
    private void parseConstants(JsonObject json) {
        constants.clear();
        treeConstants.clear();
        if (!json.has("_constants")) return;
        JsonObject obj = json.getAsJsonObject("_constants");
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
        Goo.LOGGER.info("Loaded {} constants ({} scalar, {} tree)",
                constants.size() + treeConstants.size(), constants.size(), treeConstants.size());
    }

    /** True if the expression string contains a $ref that's a known tree constant. */
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

    /** Parses _groups first (pseudo-tags), then item entries. Resolves #name against pseudo-tags. */
    private void parseItemValues(JsonObject json) {
        parseGroups(json);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_")) continue;
            if (key.startsWith("#")) {
                expandPseudoTag(key.substring(1), entry.getValue());
                continue;
            }
            assignItemValue(Identifier.parse(key), entry.getValue());
        }
    }

    /** Pattern for parallel copy with optional scale: "#source * N / M" or just "#source". */
    private static final java.util.regex.Pattern PARALLEL_COPY_PATTERN = java.util.regex.Pattern.compile(
            "#(\\w+)(?:\\s*\\*\\s*(\\d+)\\s*/\\s*(\\d+))?\\s*");

    /** Expands a #name key against pseudo-tags, assigning the value to each member. */
    private void expandPseudoTag(String name, JsonElement value) {
        Set<Identifier> targetMembers = resolvePseudoTag(name);
        if (targetMembers == null || targetMembers.isEmpty()) {
            Goo.LOGGER.warn("Pseudo-tag #{} resolved to no members, skipping", name);
            return;
        }

        // Check for parallel copy: "#source * N / M"
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            java.util.regex.Matcher m = PARALLEL_COPY_PATTERN.matcher(value.getAsString().trim());
            if (m.matches()) {
                String sourceName = m.group(1);
                int multiplier = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
                int divisor = m.group(3) != null ? Integer.parseInt(m.group(3)) : 1;
                parallelCopyBaseValues(name, sourceName, targetMembers, multiplier, divisor);
                return;
            }
        }

        for (Identifier member : targetMembers) {
            assignItemValue(member, value);
        }
    }

    /** Resolves a pseudo-tag name to its members, trying exact then parsed path. */
    private Set<Identifier> resolvePseudoTag(String name) {
        Set<Identifier> members = pseudoTags.get(name);
        if (members == null) {
            members = pseudoTags.get(Identifier.parse(name).getPath());
        }
        return members;
    }

    /** Parallel copy from source tag to target tag, with optional scale, during base value parsing. */
    private void parallelCopyBaseValues(String targetName, String sourceName,
                                         Set<Identifier> targetMembers,
                                         int multiplier, int divisor) {
        Set<Identifier> sourceMembers = resolvePseudoTag(sourceName);
        if (sourceMembers == null || sourceMembers.isEmpty()) {
            Goo.LOGGER.error("Parallel copy source #{} resolved to no members", sourceName);
            return;
        }
        List<Identifier> targets = new ArrayList<>(targetMembers);
        List<Identifier> sources = new ArrayList<>(sourceMembers);
        if (targets.size() != sources.size()) {
            Goo.LOGGER.error("Parallel copy size mismatch: #{} has {} items, #{} has {} items",
                    targetName, targets.size(), sourceName, sources.size());
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            GooValue sourceVal = baseValues.get(sources.get(i));
            if (sourceVal == null || sourceVal.isEmpty()) {
                Goo.LOGGER.warn("Parallel copy: source {} has no value, skipping target {}",
                        sources.get(i), targets.get(i));
                continue;
            }
            try {
                GooValue scaled = sourceVal.multiply(multiplier).divideExact(divisor);
                if (scaled.hasNegative()) {
                    Goo.LOGGER.error("Parallel copy produced negative for {}: {}", targets.get(i), scaled);
                    continue;
                }
                baseValues.put(targets.get(i), scaled);
            } catch (ArithmeticException e) {
                Goo.LOGGER.error("Parallel copy scale failed for {} (from {}): {}",
                        targets.get(i), sources.get(i), e.getMessage());
            }
        }
    }

    /** Assigns a value or denial to a single item. */
    private void assignItemValue(Identifier itemId, JsonElement value) {
        if (isDeniedEntry(value)) {
            deniedItems.add(itemId);
        } else {
            GooValue resolved = resolveItemEntry(value);
            if (resolved.hasNegative()) {
                Goo.LOGGER.error("Negative goo in base value for {}: {} -- skipped", itemId, resolved);
            } else {
                baseValues.put(itemId, resolved);
            }
        }
    }

    /**
     * Resolves a non-denied item entry: JSON object -> explicit GooValue,
     * string -> item reference expression.
     */
    private GooValue resolveItemEntry(JsonElement value) {
        if (value.isJsonObject()) {
            return GooValueJsonFormat.parseGooValue(value.getAsJsonObject(), constants, baseValues, treeConstants);
        }
        // String expression referencing other items
        return GooValueExpression.evaluate(value.getAsString().trim(), baseValues, constants, treeConstants);
    }

    /** Parses _groups into pseudo-tags: each key maps to an array of item IDs. */
    private void parseGroups(JsonObject json) {
        if (!json.has("_groups")) return;
        JsonObject groups = json.getAsJsonObject("_groups");
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

    /** Parses both _conversions and _post_conversions blocks. */
    private void parseConversions(JsonObject json) {
        preConversions = parseConversionBlock(json, "_conversions");
        postConversions = parseConversionBlock(json, "_post_conversions");
    }

    /** Parses a single conversion block by key name, with constant resolution. */
    private GooConversion.ParsedConversions parseConversionBlock(JsonObject json, String key) {
        if (!json.has(key)) return null;
        JsonObject block = json.getAsJsonObject(key);
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
            entries.put(entry.getKey(), entry.getValue().getAsString());
        }
        return GooConversion.parseBlock(entries, constants, treeConstants);
    }

    /** Applies a parsed conversion set to a values map. */
    private void applyConversions(GooConversion.ParsedConversions parsed,
                                   Map<Identifier, GooValue> values) {
        if (parsed == null) return;
        for (GooConversion.Assignment assignment : parsed.assignments()) {
            List<Identifier> targetItems = resolveConversionTarget(assignment.target());
            if (targetItems.isEmpty()) {
                Goo.LOGGER.warn("Conversion target {} resolved to no items", assignment.target());
                continue;
            }
            List<Identifier> sourceItems = null;
            if (assignment.parallelSource() != null) {
                sourceItems = resolveConversionTarget(assignment.parallelSource());
                if (sourceItems.isEmpty()) {
                    Goo.LOGGER.warn("Parallel source {} resolved to no items",
                            assignment.parallelSource());
                    continue;
                }
            }
            GooConversion.applyAssignment(values, targetItems, sourceItems,
                    assignment, parsed.formulas(), parsed.additives());
        }
    }

    /** Resolves a conversion target (item ID or #tag) to an ordered list of item IDs. */
    private List<Identifier> resolveConversionTarget(String target) {
        if (target.startsWith("#")) {
            String name = target.substring(1);
            Set<Identifier> members = pseudoTags.get(name);
            if (members == null) {
                members = pseudoTags.get(Identifier.parse(name).getPath());
            }
            return members != null ? new ArrayList<>(members) : List.of();
        }
        return List.of(Identifier.parse(target));
    }

    /** Returns true if the JSON value is the string "denied". */
    private boolean isDeniedEntry(JsonElement element) {
        return element.isJsonPrimitive() && "denied".equals(element.getAsString());
    }

    /**
     * Loads effective values from the flat cache file. The cache contains
     * pre-resolved integer values per goo type, so no expression evaluation
     * or base value merging is needed.
     */
    public void loadEffectiveCache() {
        if (effectiveCachePath == null || !Files.exists(effectiveCachePath)) return;

        try (Reader reader = Files.newBufferedReader(effectiveCachePath, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            effectiveValues.clear();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                Identifier itemId = Identifier.parse(entry.getKey());
                effectiveValues.put(itemId, GooValueJsonFormat.parseGooValue(entry.getValue().getAsJsonObject()));
            }
            Goo.LOGGER.info("Loaded {} effective goo values from cache", effectiveValues.size());
        } catch (Exception e) {
            Goo.LOGGER.warn("Failed to load effective goo value cache", e);
        }
    }

    /**
     * Saves the complete effective value map to the cache file.
     * Written by /goo regen so startup can load a flat, pre-resolved file.
     */
    public void saveEffectiveValues() {
        if (effectiveCachePath == null) return;

        try {
            Files.createDirectories(effectiveCachePath.getParent());
            JsonObject json = serializeValues(effectiveValues);
            Files.writeString(effectiveCachePath, GSON.toJson(json), StandardCharsets.UTF_8);
            Goo.LOGGER.info("Saved {} effective goo values to cache", effectiveValues.size());
        } catch (IOException e) {
            Goo.LOGGER.error("Failed to save effective goo value cache", e);
        }
    }

    /** Serializes a value map to a sorted JSON object. */
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
     */
    public int deriveFromRecipes(MinecraftServer server) {
        HolderLookup.Provider registries = server.registryAccess();
        lastRecipes = adaptRecipes(server.getRecipeManager().getRecipes(), registries);
        boolean baseOverride = GooConfig.BASE_VALUES_OVERRIDE_RECIPES.get();
        int derived = deriveFromRecipeInputs(lastRecipes, baseOverride);
        Goo.LOGGER.info("Derived {} goo values from recipes", derived);
        return derived;
    }

    /** Converts MC RecipeHolders into MC-free RecipeInputs. */
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

    /** Converts a single MC recipe to a RecipeInput, or null if unsupported. */
    @Nullable
    private RecipeInput adaptOneRecipe(Recipe<?> recipe, HolderLookup.Provider registries) {
        ItemStack resultStack = getRecipeResult(recipe, registries);
        if (resultStack == null || resultStack.isEmpty()) return null;

        Identifier outputId = BuiltInRegistries.ITEM.getKey(resultStack.getItem());
        AdaptedIngredients adapted = adaptIngredients(recipe);
        Map<Identifier, Identifier> containers = adaptContainerItems(recipe);
        return new RecipeInput(outputId, resultStack.getCount(),
                adapted.slots(), containers, adapted.tagIds());
    }

    /** Builds a map of ingredient item ID to its crafting remainder item ID. */
    private Map<Identifier, Identifier> adaptContainerItems(Recipe<?> recipe) {
        Map<Identifier, Identifier> containers = new HashMap<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) continue;
            collectContainersFromIngredient(ingredient, containers);
        }
        return containers;
    }

    /** Inspects each alternative in an ingredient for a crafting remainder. */
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

    /** Converts MC Ingredients to sets of item Identifiers, capturing tag identity per slot. */
    private AdaptedIngredients adaptIngredients(Recipe<?> recipe) {
        List<Set<Identifier>> slots = new ArrayList<>();
        List<Optional<Identifier>> tagIds = new ArrayList<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) continue;
            Set<Identifier> alternatives = adaptOneIngredient(ingredient);
            if (!alternatives.isEmpty()) {
                slots.add(alternatives);
                tagIds.add(extractTagId(ingredient));
            }
        }
        return new AdaptedIngredients(slots, tagIds);
    }

    /** Extracts the tag identity from a standard ingredient's HolderSet, if present. */
    private Optional<Identifier> extractTagId(Ingredient ingredient) {
        if (ingredient.isCustom()) return Optional.empty();
        HolderSet<Item> holderSet = ingredient.getValues();
        return holderSet.unwrapKey()
                .map(TagKey::location);
    }

    /** Resolves a single MC Ingredient to its set of item IDs. */
    private Set<Identifier> adaptOneIngredient(Ingredient ingredient) {
        Set<Identifier> alternatives = new HashSet<>();
        for (Holder<Item> holder : resolveIngredientItems(ingredient)) {
            alternatives.add(BuiltInRegistries.ITEM.getKey(holder.value()));
        }
        return alternatives;
    }

    /** Resolves an ingredient's item holders, using getValues() for standard ingredients. */
    private List<Holder<Item>> resolveIngredientItems(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return ingredient.getCustomIngredient().items().toList();
        }
        return ingredient.getValues().stream().toList();
    }

    /**
     * Gets the result ItemStack from a recipe using the public assemble() API.
     * Vanilla implementations ignore the input and return a copy of the stored result.
     */
    @SuppressWarnings("unchecked")
    static ItemStack getRecipeResult(Recipe<?> recipe, HolderLookup.Provider registries) {
        try {
            if (recipe instanceof CraftingRecipe crafting) {
                return crafting.assemble(CraftingInput.EMPTY);
            } else if (recipe instanceof SingleItemRecipe single) {
                return single.assemble(new SingleRecipeInput(ItemStack.EMPTY));
            }
            Goo.LOGGER.debug("Unsupported recipe type: {}", recipe.getClass().getName());
        } catch (Exception e) {
            Goo.LOGGER.warn("Failed to get result from {}: {}",
                recipe.getClass().getSimpleName(), e.getMessage());
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
     */
    public GooValue lookup(Identifier itemId) {
        return effectiveValues.get(itemId);
    }

    /**
     * Looks up the goo value for an item stack. Falls back to component-based
     * value computation if the item implements {@link IComponentValueProvider}.
     */
    public GooValue lookup(ItemStack stack) {
        if (stack.isEmpty()) return null;
        GooValue base = lookup(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (base != null) return base;
        if (stack.getItem() instanceof IComponentValueProvider provider) {
            return provider.computeComponentValue(stack, this);
        }
        return null;
    }

    /** Returns the total number of items with effective goo values. */
    public int size() {
        return effectiveValues.size();
    }

    /** Returns the number of hand-keyed base values. */
    public int baseSize() {
        return baseValues.size();
    }

    /** Returns the number of recipe-derived values. */
    public int derivedSize() {
        return lastDerivation != null ? lastDerivation.derivedValues().size() : 0;
    }

    /** Returns the last detected recipe dependency cycles. */
    public List<RecipeCycle> getLastCycles() {
        return lastDerivation != null ? lastDerivation.cycles() : List.of();
    }

    /** Returns the last detected base/derived value conflicts. */
    public List<ValueConflict> getLastConflicts() {
        return lastDerivation != null ? lastDerivation.conflicts() : List.of();
    }

    /** Returns the last detected divisibility losses. */
    public List<DivisibilityLoss> getLastDivisibilityLosses() {
        return lastDerivation != null ? lastDerivation.divisibilityLosses() : List.of();
    }

    /** Returns true if the item has a hand-keyed base value. */
    public boolean hasBaseValue(Identifier itemId) {
        return baseValues.containsKey(itemId);
    }

    /** Returns true if the item is on the deny list. */
    public boolean isDenied(Identifier itemId) {
        return deniedItems.contains(itemId);
    }

    /** Returns all identifiers referenced in base_values.json (valued + denied). */
    public Set<Identifier> getAllReferencedIds() {
        Set<Identifier> all = new HashSet<>(baseValues.keySet());
        all.addAll(deniedItems);
        return Collections.unmodifiableSet(all);
    }

    /** Returns the recipe that produced the derived value for an item, or null. */
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
     */
    void setDeniedItems(Set<Identifier> items) {
        deniedItems.clear();
        deniedItems.addAll(items);
    }

    /**
     * Sets base values directly, bypassing JSON loading. For test use only.
     */
    void setBaseValues(Map<Identifier, GooValue> values) {
        baseValues.clear();
        baseValues.putAll(values);
        effectiveValues.clear();
        effectiveValues.putAll(values);
    }

    /** Returns a snapshot of current derived values. For test assertions. */
    Map<Identifier, GooValue> getDerivedValues() {
        return lastDerivation != null ? lastDerivation.derivedValues() : Map.of();
    }

    /** Returns a snapshot of current effective values. */
    public Map<Identifier, GooValue> getEffectiveValues() {
        return Collections.unmodifiableMap(effectiveValues);
    }

    /**
     * Replaces effective values wholesale with server-provided data.
     * Used on the client side to receive synced values over the network.
     */
    public void receiveClientValues(Map<Identifier, GooValue> values) {
        effectiveValues.clear();
        effectiveValues.putAll(values);
        Goo.LOGGER.debug("Client received {} effective goo values", values.size());
    }

    /**
     * Generates scaffold by collecting recipes fresh from the server.
     * Does not require a prior regen.
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldFresh(MinecraftServer server) {
        HolderLookup.Provider registries = server.registryAccess();
        List<RecipeInput> recipes = adaptRecipes(server.getRecipeManager().getRecipes(), registries);
        // Include conversion targets as valued so they don't appear as roots
        Map<Identifier, GooValue> holistic = holisticValuedItems();
        List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                recipes, holistic, deniedItems);
        return ScaffoldGenerator.generateScaffold(roots, recipes);
    }

    /**
     * Generates scaffold from cached recipes (requires a prior regen or load).
     * Shows only roots that are still missing after the last derivation pass.
     * Uses effective values (post-derivation, post-conversion) for completeness.
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldMissing() {
        List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                lastRecipes, effectiveValues, deniedItems);
        return ScaffoldGenerator.generateScaffold(roots, lastRecipes);
    }

    /**
     * Builds a holistic valued-items map: base values plus all items that will
     * receive values from conversion assignments (parallel copies, additives).
     * Used by scaffold to avoid flagging conversion targets as missing.
     */
    private Map<Identifier, GooValue> holisticValuedItems() {
        Map<Identifier, GooValue> valued = new HashMap<>(baseValues);
        collectConversionTargets(preConversions, valued);
        collectConversionTargets(postConversions, valued);
        return valued;
    }

    /** Adds placeholder values for all conversion assignment targets. */
    private void collectConversionTargets(GooConversion.ParsedConversions parsed,
                                           Map<Identifier, GooValue> valued) {
        if (parsed == null) return;
        for (GooConversion.Assignment assignment : parsed.assignments()) {
            for (Identifier item : resolveConversionTarget(assignment.target())) {
                valued.putIfAbsent(item, GooValue.EMPTY);
            }
        }
    }

    /** Copies base values into effective values. For test use after parseBaseValuesFromStream. */
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
            warnings.add("No base values loaded. Run /goo regen first.");
            return warnings;
        }
        validateJson(lastMergedBaseValues, warnings);
        return warnings;
    }

    /** Validates a JSON string for expression mistakes. For testing. */
    List<String> validateJsonString(String jsonString) {
        List<String> warnings = new ArrayList<>();
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        validateJson(json, warnings);
        return warnings;
    }

    /** Walks the JSON checking expression tokens for mistakes. */
    private void validateJson(JsonObject json, List<String> warnings) {
        Set<String> knownConstants = new LinkedHashSet<>();
        // Validate _constants block
        if (json.has("_constants")) {
            JsonObject obj = json.getAsJsonObject("_constants");
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    validateExprTokens(entry.getValue().getAsString(), knownConstants,
                            new LinkedHashSet<>(), "_constants." + entry.getKey(), warnings);
                }
                knownConstants.add(entry.getKey());
            }
        }
        // Validate individual item entries
        Set<String> knownItems = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getKey().startsWith("_") || entry.getKey().startsWith("#")) continue;
            String itemKey = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                validateValueObject(value.getAsJsonObject(), knownConstants, knownItems, itemKey, warnings);
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                       && !"denied".equals(value.getAsString())) {
                validateExprTokens(value.getAsString(), knownConstants, knownItems, itemKey, warnings);
            }
            knownItems.add(itemKey);
        }
        // Validate _groups (new format: arrays of item IDs)
        if (json.has("_groups")) {
            JsonObject groups = json.getAsJsonObject("_groups");
            for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
                if (!group.getValue().isJsonArray()) {
                    warnings.add("_groups." + group.getKey() + ": expected an array of item IDs");
                    continue;
                }
                com.google.gson.JsonArray items = group.getValue().getAsJsonArray();
                for (JsonElement item : items) {
                    knownItems.add(item.getAsString());
                }
            }
        }
        // Validate _conversions and _post_conversions
        validateConversionBlock(json, "_conversions", knownConstants, warnings);
        validateConversionBlock(json, "_post_conversions", knownConstants, warnings);
    }

    /** Validates a conversion block for unknown @refs and malformed formulas. */
    private void validateConversionBlock(JsonObject json, String blockKey,
                                          Set<String> knownConstants, List<String> warnings) {
        if (!json.has(blockKey)) return;
        JsonObject block = json.getAsJsonObject(blockKey);
        Set<String> knownRefs = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().isJsonPrimitive()) {
                warnings.add(blockKey + "." + key + ": expected a string value");
                continue;
            }
            String value = entry.getValue().getAsString();
            if ("denied".equals(value)) continue;
            String ctx = blockKey + "." + key;
            if (value.contains("->")) {
                // Formula: validate types
                try {
                    GooConversion.parseFormula(value);
                } catch (IllegalArgumentException e) {
                    warnings.add(ctx + ": invalid formula: " + e.getMessage());
                }
                knownRefs.add(key);
            } else if (value.startsWith("+")) {
                // Additive: validate constant ref
                String ref = value.substring(1).trim();
                if (ref.startsWith("$") && !knownConstants.contains(ref.substring(1))) {
                    warnings.add(ctx + ": unknown constant " + ref);
                }
                knownRefs.add(key);
            } else if (value.contains("@")) {
                // Stack or assignment: validate @refs exist
                for (String part : value.trim().split("\\s+")) {
                    if (part.startsWith("@")) {
                        String refName = part.substring(1).replaceAll("^\\d+\\s*", "");
                        if (!knownRefs.contains(refName)) {
                            warnings.add(ctx + ": unknown conversion reference @" + refName);
                        }
                    }
                }
                if (!key.startsWith("#") && !key.contains(":")) {
                    knownRefs.add(key);
                }
            }
        }
    }

    /** Validates per-type expressions in a value object like { "metal": "$iron * 3" }. */
    private void validateValueObject(JsonObject obj, Set<String> knownConstants,
                                     Set<String> knownItems, String context,
                                     List<String> warnings) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                validateExprTokens(entry.getValue().getAsString(), knownConstants, knownItems,
                        context + "." + entry.getKey(), warnings);
            }
        }
    }

    /** Checks expression tokens for forgotten $ prefixes, unknown constants, and out-of-order items. */
    private void validateExprTokens(String expr, Set<String> knownConstants,
                                    Set<String> knownItems, String context,
                                    List<String> warnings) {
        List<String> tokens = GooValueExpression.tokenize(expr);
        for (String token : tokens) {
            // Skip operators and parens
            if (token.length() == 1 && "+-*/()".contains(token)) continue;

            if (token.startsWith("$")) {
                // Check unknown constant
                String name = token.substring(1);
                if (!knownConstants.contains(name)) {
                    warnings.add(context + ": unknown constant $" + name);
                }
            } else if (token.contains(":")) {
                // Namespaced item reference - check order
                String itemId = token;
                // Strip .type suffix for lookup
                int colonIdx = token.indexOf(':');
                int dotIdx = token.lastIndexOf('.');
                if (dotIdx > colonIdx && dotIdx < token.length() - 1) {
                    itemId = token.substring(0, dotIdx);
                }
                if (!knownItems.contains(itemId)) {
                    warnings.add(context + ": references " + itemId + " which is not defined above it");
                }
            } else if (!Character.isDigit(token.charAt(0))) {
                // Bare word: could be a minecraft: item ref, a dot-notation ref, or a forgotten $ prefix
                String bareItem = token;
                int dotIdx = token.lastIndexOf('.');
                if (dotIdx > 0 && dotIdx < token.length() - 1) {
                    bareItem = token.substring(0, dotIdx);
                }
                String qualifiedItem = bareItem.contains(":") ? bareItem : "minecraft:" + bareItem;
                if (knownItems.contains(qualifiedItem) || knownItems.contains(bareItem)) {
                    // Valid bare-word item reference (defaults to minecraft: namespace)
                } else if (knownConstants.contains(token)) {
                    warnings.add(context + ": '" + token + "' looks like a constant missing its $ prefix (should be $" + token + ")");
                } else {
                    warnings.add(context + ": unrecognized token '" + token + "'");
                }
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
