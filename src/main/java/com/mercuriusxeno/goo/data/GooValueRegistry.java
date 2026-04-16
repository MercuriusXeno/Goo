package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for all item goo values. Loads base values from JSON,
 * derives values from all recipe types using LCD rule, and persists derived values.
 *
 * <p>The derivation engine is split into two layers: a thin Minecraft adapter
 * (recipe/ingredient resolution) and a pure logic core that operates on
 * {@link RecipeInput} records, enabling unit testing without a running server.</p>
 *
 * <p>Heavy lifting is delegated to package-private helpers:
 * {@link GooValueLoader} (parsing), {@link GooValueMerger} (datapack merging),
 * {@link GooValueValidator} (authoring checks), {@link GooValueRecipeAdapter}
 * (MC recipe conversion), and {@link GooValueCache} (disk persistence).</p>
 */
public class GooValueRegistry implements IGooValueLookup {

    static final int MAX_DERIVATION_PASSES = 20;

    /** Log: derived values from recipes. */
    private static final String LOG_DERIVED = "Derived {} goo values from recipes";
    /** Log: client received values. */
    private static final String LOG_CLIENT_RECEIVED = "Client received {} effective goo values";
    /** Warning: no base values loaded yet. */
    private static final String WARN_NO_BASE_VALUES = "No base values loaded. Run /goo regen first.";
    /** Error: no datapack provides base values. */
    private static final String ERROR_NO_DATAPACK = "No datapack provides goo_values/base_values.json";

    // --- Instance state (package-private for test access) ---

    final Map<Identifier, GooValue> baseValues = new HashMap<>();
    /** Effective values after LCD comparison between base and derived. */
    final Map<Identifier, GooValue> effectiveValues = new HashMap<>();
    /** Items explicitly denied a value (e.g. ore blocks - fortune makes them unvaluable). */
    final Set<Identifier> deniedItems = new HashSet<>();
    /** Items restricted from plexer reconstitution but still decomposable. */
    final Set<Identifier> restrictedItems = new HashSet<>();
    /** Named constants from _constants block, resolved during value parsing. */
    final Map<String, Integer> constants = new HashMap<>();
    /** Tree constants from _constants block: GooValue objects keyed by name. */
    final Map<String, GooValue> treeConstants = new HashMap<>();
    /** Pseudo-tags from _groups block: group name to item set. */
    final Map<String, Set<Identifier>> pseudoTags = new HashMap<>();
    /** Pre-derivation conversions from _conversions block. */
    GooConversion.ParsedConversions preConversions;
    /** Post-derivation conversions from _post_conversions block. */
    GooConversion.ParsedConversions postConversions;

    /** Result of the last derivation or cache load. Null before first derivation. */
    @Nullable
    DerivationResult lastDerivation;

    /** Cached recipe inputs from the last derivation, for scaffold generation. */
    List<RecipeInput> lastRecipes = List.of();

    /** Last merged base_values JSON from regen, retained for validation. */
    @Nullable
    JsonObject lastMergedBaseValues;

    private Path effectiveCachePath;

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
     * Snapshot of diagnostic data from the last derivation run.
     * Returned by {@link #diagnostics()} to consolidate accessors.
     *
     * @param baseSize           number of hand-keyed base values
     * @param derivedSize        number of recipe-derived values
     * @param cycles             recipe dependency cycles
     * @param conflicts          base/derived value conflicts
     * @param divisibilityLosses recipes with integer division loss
     * @param allReferencedIds   all item IDs in base_values.json (valued + denied + restricted)
     * @param derivationSources  map from derived item ID to the recipe that produced its value
     */
    public record DiagnosticSnapshot(
            int baseSize, int derivedSize,
            List<RecipeCycle> cycles,
            List<ValueConflict> conflicts,
            List<DivisibilityLoss> divisibilityLosses,
            Set<Identifier> allReferencedIds,
            Map<Identifier, RecipeInput> derivationSources
    ) {}


    /**
     * Sets the path for the effective value cache file.
     *
     * @param path the filesystem path for caching effective values
     */
    public void setEffectiveCachePath(Path path) {
        this.effectiveCachePath = path;
    }

    /**
     * Loads base values from the embedded JSON resource.
     */
    public void loadBaseValues() {
        baseValues.clear();
        deniedItems.clear();
        restrictedItems.clear();
        effectiveValues.clear();
        treeConstants.clear();
        GooValueLoader.loadBaseValuesFromClasspath(new GooValueLoader.ParseState(
                baseValues, effectiveValues, deniedItems, restrictedItems,
                constants, treeConstants, pseudoTags));
        effectiveValues.putAll(baseValues);
    }

    /**
     * Loads base values by merging all datapack layers via the server's ResourceManager.
     *
     * @param server the running server whose resource manager provides the pack stack
     */
    public void loadBaseValuesFromPacks(MinecraftServer server) {
        var state = createParseState();
        GooValueLoader.clearRegistryState(state);
        List<Resource> stack = loadResourceStack(server);
        if (stack.isEmpty()) {
            Goo.LOGGER.error(ERROR_NO_DATAPACK);
            return;
        }
        applyPackLayers(stack, state);
    }

    /**
     * Creates a fresh ParseState backed by this registry's maps.
     * @return a new ParseState wired to this registry's mutable maps
     */
    private GooValueLoader.ParseState createParseState() {
        return new GooValueLoader.ParseState(
                baseValues, effectiveValues, deniedItems, restrictedItems,
                constants, treeConstants, pseudoTags);
    }

    /**
     * Loads the datapack resource stack for base_values.json.
     *
     * @param server the server providing the resource manager
     * @return the ordered resource stack
     */
    private static List<Resource> loadResourceStack(MinecraftServer server) {
        ResourceManager resourceManager = server.getResourceManager();
        Identifier location = Identifier.fromNamespaceAndPath(
                GooValueLoader.modNamespace(), GooValueLoader.baseValuesResource());
        return resourceManager.getResourceStack(location);
    }

    /**
     * Parses and applies merged datapack layers, storing conversion state.
     *
     * @param stack the resource stack to merge
     * @param state the parse state to populate
     */
    private void applyPackLayers(List<Resource> stack, GooValueLoader.ParseState state) {
        var layers = GooValueLoader.parseResourceLayers(stack);
        GooValueLoader.applyMergedLayers(layers, state);
        preConversions = state.preConversions;
        postConversions = state.postConversions;
        lastMergedBaseValues = state.lastMergedBaseValues;
    }

    /**
     * Derives goo values from all server recipes using the LCD rule.
     *
     * @param server the running server providing recipes
     * @return the number of items that received derived values
     */
    public int deriveFromRecipes(MinecraftServer server) {
        HolderLookup.Provider registries = server.registryAccess();
        lastRecipes = GooValueRecipeAdapter.adaptRecipes(
                server.getRecipeManager().getRecipes(), registries);
        boolean baseOverride = GooConfig.BASE_VALUES_OVERRIDE_RECIPES.get();
        int derived = deriveFromRecipeInputs(lastRecipes, baseOverride);
        Goo.LOGGER.info(LOG_DERIVED, derived);
        return derived;
    }

    /**
     * Loads effective values from the flat cache file.
     * Also serves as the reload entry point.
     */
    public void loadEffectiveCache() {
        GooValueCache.loadEffectiveCache(effectiveCachePath, effectiveValues);
    }

    /**
     * Saves the complete effective value map to the cache file.
     */
    public void saveEffectiveValues() {
        GooValueCache.saveEffectiveValues(effectiveCachePath, effectiveValues);
    }

    /**
     * Validates the last-loaded base_values.json for authoring mistakes.
     *
     * @return list of validation warnings
     */
    public List<String> validateBaseValues() {
        List<String> warnings = new ArrayList<>();
        if (lastMergedBaseValues == null) {
            warnings.add(WARN_NO_BASE_VALUES);
            return warnings;
        }
        GooValueValidator.validateJson(lastMergedBaseValues, warnings);
        return warnings;
    }

    /**
     * Replaces effective values wholesale with server-provided data.
     *
     * @param values the server-synced effective values
     */
    public void receiveClientValues(Map<Identifier, GooValue> values) {
        effectiveValues.clear();
        effectiveValues.putAll(values);
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_CLIENT_RECEIVED, values.size()); }
    }

    /**
     * Clears all internal state. Used on client disconnect to prevent stale data.
     */
    public void clearAll() {
        baseValues.clear();
        effectiveValues.clear();
        deniedItems.clear();
        restrictedItems.clear();
        constants.clear();
        treeConstants.clear();
        lastDerivation = null;
        lastMergedBaseValues = null;
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
        List<RecipeInput> recipes = GooValueRecipeAdapter.adaptRecipes(
                server.getRecipeManager().getRecipes(), registries);
        Set<Identifier> allItems = BuiltInRegistries.ITEM.keySet();
        List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                recipes, baseValues, deniedItems, allItems);
        return ScaffoldGenerator.generateScaffold(roots, recipes, bare);
    }

    /**
     * Generates scaffold from cached recipes (requires a prior regen or load).
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


    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int size() { return effectiveValues.size(); }

    /** {@inheritDoc} */
    @Override
    public boolean hasBaseValue(Identifier itemId) { return baseValues.containsKey(itemId); }

    /** {@inheritDoc} */
    @Override
    public boolean isDenied(Identifier itemId) { return deniedItems.contains(itemId); }

    /** {@inheritDoc} */
    @Override
    public boolean isRestricted(Identifier itemId) { return restrictedItems.contains(itemId); }

    /** {@inheritDoc} */
    @Override
    public Map<Identifier, GooValue> getEffectiveValues() {
        return Collections.unmodifiableMap(effectiveValues);
    }

    /**
     * Returns a snapshot of diagnostic data from the last derivation run.
     * Consolidates baseSize, derivedSize, cycles, conflicts, divisibility losses,
     * derivation sources, and all referenced IDs into one accessor.
     *
     * @return diagnostic snapshot (never null; counts are zero before first derivation)
     */
    public DiagnosticSnapshot diagnostics() {
        Set<Identifier> allRefs = new HashSet<>(baseValues.keySet());
        allRefs.addAll(deniedItems);
        allRefs.addAll(restrictedItems);
        return buildSnapshot(Collections.unmodifiableSet(allRefs));
    }

    /**
     * Builds the diagnostic snapshot, using empty defaults when no derivation has run yet.
     *
     * @param allRefs the complete set of referenced item identifiers
     * @return the diagnostic snapshot
     */
    private DiagnosticSnapshot buildSnapshot(Set<Identifier> allRefs) {
        if (lastDerivation == null) {
            return new DiagnosticSnapshot(
                    baseValues.size(), 0, List.of(), List.of(), List.of(), allRefs, Map.of());
        }
        return new DiagnosticSnapshot(
                baseValues.size(),
                lastDerivation.derivedValues().size(),
                lastDerivation.cycles(),
                lastDerivation.conflicts(),
                lastDerivation.divisibilityLosses(),
                allRefs,
                lastDerivation.derivationSources()
        );
    }


    /**
     * Derives goo values from MC-free recipe inputs using the LCD rule.
     *
     * @param recipes      all recipes to consider
     * @param baseOverride when true, base values always win over derived values
     * @return the number of items that received derived values
     */
    int deriveFromRecipeInputs(List<RecipeInput> recipes, boolean baseOverride) {
        lastDerivation = GooValueDerivation.derive(recipes, baseValues, deniedItems, baseOverride);
        effectiveValues.clear();
        effectiveValues.putAll(lastDerivation.effectiveValues());
        GooConversionLoader.applyConversions(postConversions, effectiveValues, pseudoTags);
        return lastDerivation.derivedValues().size();
    }

}
