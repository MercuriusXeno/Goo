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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
import java.util.List;
import java.util.Map;
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

    /** Result of the last derivation or cache load. Null before first derivation. */
    @Nullable
    private DerivationResult lastDerivation;

    private Path derivedCachePath;

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

    /** Sets the path for the derived value cache file. */
    public void setDerivedCachePath(Path path) {
        this.derivedCachePath = path;
    }

    // ── JSON loading / caching ──────────────────────────────────────────

    /**
     * Loads base values from the embedded JSON resource.
     */
    public void loadBaseValues() {
        baseValues.clear();
        deniedItems.clear();
        effectiveValues.clear();
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

    /** Parses constants and item entries (values + denials) from an input stream. */
    private void parseBaseValuesFromStream(InputStream is) throws IOException {
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            parseConstants(json);
            parseItemValues(json);
        }
    }

    /** Parses the _constants object from the JSON root, if present. */
    private void parseConstants(JsonObject json) {
        constants.clear();
        if (!json.has("_constants")) return;
        JsonObject obj = json.getAsJsonObject("_constants");
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            constants.put(entry.getKey(), entry.getValue().getAsInt());
        }
        Goo.LOGGER.info("Loaded {} constants", constants.size());
    }

    /** Parses item entries from the JSON root: objects become values, "denied" strings become denials. */
    private void parseItemValues(JsonObject json) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getKey().startsWith("_")) continue;
            Identifier itemId = Identifier.parse(entry.getKey());
            if (isDeniedEntry(entry.getValue())) {
                deniedItems.add(itemId);
            } else {
                baseValues.put(itemId, GooValueJsonFormat.parseGooValue(entry.getValue().getAsJsonObject(), constants));
            }
        }
    }

    /** Returns true if the JSON value is the string "denied". */
    private boolean isDeniedEntry(JsonElement element) {
        return element.isJsonPrimitive() && "denied".equals(element.getAsString());
    }

    /**
     * Loads previously derived values from the cache file.
     */
    public void loadDerivedCache() {
        if (derivedCachePath == null || !Files.exists(derivedCachePath)) return;

        try (Reader reader = Files.newBufferedReader(derivedCachePath, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            Map<Identifier, GooValue> cached = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                Identifier itemId = Identifier.parse(entry.getKey());
                cached.put(itemId, GooValueJsonFormat.parseGooValue(entry.getValue().getAsJsonObject(), constants));
            }
            boolean baseOverride = GooConfig.BASE_VALUES_OVERRIDE_RECIPES.get();
            Map<Identifier, GooValue> rebuilt = GooValueDerivation.buildEffectiveValues(baseValues, cached, baseOverride);
            lastDerivation = new DerivationResult(
                Map.copyOf(cached), Map.of(), rebuilt, List.of(), List.of(), List.of());
            effectiveValues.clear();
            effectiveValues.putAll(rebuilt);
            Goo.LOGGER.info("Loaded {} cached derived goo values", cached.size());
        } catch (Exception e) {
            Goo.LOGGER.warn("Failed to load derived goo value cache", e);
        }
    }

    /**
     * Saves derived values to the cache file.
     */
    public void saveDerivedCache() {
        if (derivedCachePath == null || lastDerivation == null) return;

        try {
            Files.createDirectories(derivedCachePath.getParent());
            JsonObject json = serializeDerivedValues(lastDerivation.derivedValues());
            Files.writeString(derivedCachePath, GSON.toJson(json), StandardCharsets.UTF_8);
            Goo.LOGGER.info("Saved {} derived goo values to cache", lastDerivation.derivedValues().size());
        } catch (IOException e) {
            Goo.LOGGER.error("Failed to save derived goo value cache", e);
        }
    }

    /** Serializes derived values to a sorted JSON object. */
    private JsonObject serializeDerivedValues(Map<Identifier, GooValue> derivedValues) {
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
        List<RecipeInput> recipes = adaptRecipes(server.getRecipeManager().getRecipes(), registries);
        boolean baseOverride = GooConfig.BASE_VALUES_OVERRIDE_RECIPES.get();
        int derived = deriveFromRecipeInputs(recipes, baseOverride);
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
        List<Set<Identifier>> ingredients = adaptIngredients(recipe);
        Map<Identifier, Identifier> containers = adaptContainerItems(recipe);
        return new RecipeInput(outputId, resultStack.getCount(), ingredients, containers);
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

    /** Converts MC Ingredients to sets of item Identifiers. */
    private List<Set<Identifier>> adaptIngredients(Recipe<?> recipe) {
        List<Set<Identifier>> result = new ArrayList<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) continue;
            Set<Identifier> alternatives = adaptOneIngredient(ingredient);
            if (!alternatives.isEmpty()) {
                result.add(alternatives);
            }
        }
        return result;
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
     * Full reload: base values + derived cache.
     */
    public void reload() {
        loadBaseValues();
        loadDerivedCache();
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
     * Clears all internal state. Used on client disconnect to prevent stale data.
     */
    public void clearAll() {
        baseValues.clear();
        effectiveValues.clear();
        deniedItems.clear();
        constants.clear();
        lastDerivation = null;
    }

}
