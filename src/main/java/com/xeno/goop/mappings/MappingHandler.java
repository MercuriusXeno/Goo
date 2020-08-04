package com.xeno.goop.mappings;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.xeno.goop.library.*;
import com.xeno.goop.network.GoopValueSyncPacketData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

import static com.xeno.goop.library.Compare.*;
import static com.xeno.goop.library.Helper.*;
import static com.xeno.goop.library.GoopMapping.*;
import static com.xeno.goop.library.SolvedState.SOLVED;
import static com.xeno.goop.library.SolvedState.UNSOLVED;


public class MappingHandler {
    private static final String MAPPING_SAVE_DATA_FILENAME = "goopMappings.json";
    private Map<String, GoopMapping> values = new TreeMap<>(stringLexicographicalComparator);
    private Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
    private Type jsonSerializerType = new TypeToken<TreeMap<String, GoopMapping>>(){}.getType();

    public void reloadMappings(@Nonnull World world) {
        tryLoadFromFile(world);
    }

    /**
     * @param world The world we're using to scrape for recipes, primarily, but also the save file we're using.
     * @return true if the load didn't fail for any reason.
     */
    private boolean tryLoadFromFile(World world) {
        File mappingsFile = FileHelper.getOrCreateMappingDirectoryWithFileName(world, MAPPING_SAVE_DATA_FILENAME);

        if (mappingsFile == null) {
            return false;
        }

        values = readFromJsonMappingsFile(mappingsFile);

        initializeBaselineValues();

        SolvedState solvedState = solvedStateOf(values);

        // begin the iterative mapping solution
        while (solvedState == UNSOLVED) {
            // seed mappings where the recipe has an input that has an explicit denial as one of the inputs
            // and no suitable replacements with undeniable mappings. These outputs must implicitly be denied as well.
            ProgressState denialProgress = seedRecipeDeniedMappings(world);

            // then seed based on recipe inputs and their respective outputs
            ProgressState recipeDerivationProgress = seedRecipeDerivedMappings(world);

            // seed item values that are predicated on container items, which are usually the result of some recipe
            // so we try to fire this after recipes, but we're not promised to see an improvement here.
            ProgressState containerItemProgress = seedContainerItemValues();

            // seed the forced equivalencies, for circumstances where recipe equivalence doesn't exist.
            ForcedEquivalencies forcedEquivalencies = new ForcedEquivalencies();
            ProgressState forcedEquivalencyProgress = forcedEquivalencies.pushTo(values);

            solvedState = Helper.anyProgress(denialProgress, recipeDerivationProgress, containerItemProgress, forcedEquivalencyProgress) ? UNSOLVED : SOLVED;
        }

        // todo log all unknowns?

        // any remaining unknowns are impossible to map, for whatever reason. Change them from unknown to denials.
        denyAllUnknown();

        // todo scan recipes for exploit loops or disparate quantity yields?

        tryWritingMappingFile(mappingsFile);

        return true;
    }

    private void denyAllUnknown()
    {
        for(Map.Entry<String, GoopMapping> e : values.entrySet()) {
            if (e.getValue().isUnknown()) {
                values.put(e.getKey(), DENIED);
            }
        }
    }

    private void tryWritingMappingFile(File mappingsFile) {
        try (FileWriter writer = new FileWriter(mappingsFile.getAbsolutePath())) {
            String jsonString = gsonInstance.toJson(values, jsonSerializerType);
            writer.write(jsonString);
            writer.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void initializeBaselineValues() {
        // seed universal unknowns. Everything should at least have an unknown value, but don't replace existing.
        ForgeRegistries.ITEMS.getValues().forEach(i -> values.put(name(i), values.getOrDefault(name(i), UNKNOWN)));

        // seed defaults first, this won't get everything, but it hits a lot of base items
        new DefaultMappings().pushTo(values);

        // seed denial mappings, this prevents false positives in the set that we don't desire mappings for.
        new DeniedMappings().pushTo(values);

    }

    private SolvedState solvedStateOf(Map<String, GoopMapping> values) {
        return values.entrySet().stream().noneMatch(v -> v.getValue().isUnknown()) ? SOLVED : UNSOLVED;
    }

    private Map<String, GoopMapping> readFromJsonMappingsFile(File mappingsFile) {
        JsonElement element = new JsonObject();
        if (mappingsFile.length() > 0) {
            try (FileReader reader = new FileReader(mappingsFile.getAbsolutePath())) {
                JsonStreamParser parser = new JsonStreamParser(reader);
                if (parser.hasNext()) {
                    element = parser.next().getAsJsonObject();
                }
            } catch (EOFException eof) {
                System.out.println("EOF on mappings file - not a real error, you just didn't have Goop mappings. This is fine!");
                eof.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        return gsonInstance.fromJson(element, jsonSerializerType);
    }

    public ProgressState seedContainerItemValues() {
        boolean hasProgress = false;
        hasProgress = hasProgress || tryAddingContainerItemValue(name(Items.MILK_BUCKET), values.get(name(Items.BUCKET)).add(faunal(1)));
        hasProgress = hasProgress || tryAddingContainerItemValue(name(Items.WATER_BUCKET), values.get(name(Items.BUCKET)).add(aquatic(1)));
        hasProgress = hasProgress || tryAddingContainerItemValue(name(Items.LAVA_BUCKET), values.get(name(Items.BUCKET)).add(Helper.molten(Items.LAVA_BUCKET)));

        // honey bottle + (sugar * 3) + glass bottle
        hasProgress = hasProgress || tryAddingContainerItemValue(name(Items.HONEY_BOTTLE), values.get(name(Items.GLASS_BOTTLE)).add(values.get(name(Items.SUGAR)).multiply(3)));

        // stews tend to be special too.

        return hasProgress ? ProgressState.IMPROVED : ProgressState.STAGNANT;
    }

    public boolean tryAddingContainerItemValue(String name, GoopMapping mapping) {
        if (!values.containsKey(name) || values.get(name).isUnknown() || mapping.isStrongerThan(values.get(name))) {
            values.put(name, mapping);
            return true;
        }
        return false;
    }

    private ProgressState seedRecipeDeniedMappings(World world) {
        boolean hasProgress = false;
        RecipeManager recMan = world.getRecipeManager();
        for(IRecipe<?> r : recMan.getRecipes()) {
            ItemStack output = r.getRecipeOutput();
            String name = name(output);
            // are we already denied?
            if (values.get(name).isDenied()) {
                continue;
            }
            // if the output already has a mapping it means we're explicitly giving it one, by this point
            // so don't deny it, even if the inputs are all denied; this is true of ore blocks and will cause stupidity
            if (!values.get(name).isEmpty()) {
                continue;
            }
            List<Ingredient> inputs = r.getIngredients();
            for (Ingredient g : inputs) {
                if (g.hasNoMatchingItems()) {
                    continue;
                }
                if (onlyDeniedMappingsExist(g)) {
                    hasProgress = true;
                    values.put(name, DENIED);
                }
            }
        }
        return hasProgress ? ProgressState.IMPROVED : ProgressState.STAGNANT;
    }

    private boolean onlyDeniedMappingsExist(Ingredient g) {
        for(ItemStack s : g.getMatchingStacks()) {
            if (s.isEmpty()) {
                // empty list is acceptable here - there is no input to map.
                // anything empty is a valid [matching] ingredient for must inherently also be empty or valueless.
                return false;
            }
            String name = name(s);

            if (values.containsKey(name)) {
                GoopMapping currentInputMapping = values.get(name);
                if (!currentInputMapping.isDenied()) {
                    return false;
                }
            } else {
                return false;
            }
        }
        // having reached this point we were only able to find denied mappings, so the result should be an implicit denial based on this one ingredient.
        return true;
    }

    private ProgressState seedRecipeDerivedMappings(World world) {
        // try to marry the recipe mappings to our current values and report whether we're stagnant
        return Helper.trackedPush(reduceRecipesToFlatMap(world.getRecipeManager()), values);
    }

    private Map<String, GoopMapping> reduceRecipesToFlatMap(RecipeManager recMan) {
        // instantiate a new map representing all the processed recipes and their respective GoopValueMappings
        Map<IRecipe<?>, GoopMapping> rawRecipeMappings = new HashMap<>();
        recMan.getRecipes().forEach(r -> {
            // the input mapping will come back either valid or unknown.
            GoopMapping inputMapping = processRecipe(r, rawRecipeMappings);
            rawRecipeMappings.put(r, inputMapping);
        });

        // flatten this to a map where only the best values for each item are permitted
        Map<String, GoopMapping> recipeMappings = new TreeMap<>(stringLexicographicalComparator);
        for(Map.Entry<IRecipe<?>, GoopMapping> e : rawRecipeMappings.entrySet()) {
            GoopMapping mapping = e.getValue();
            if (mapping.isUnknown()) {
                continue;
            }
            IRecipe<?> recipe = e.getKey();
            String name = name(recipe);
            GoopMapping result = !recipeMappings.containsKey(name) || mapping.isStrongerThan(recipeMappings.get(name)) ? mapping : recipeMappings.get(name);
            recipeMappings.put(name,  result);
        }

        // return the reduced map
        return recipeMappings;
    }

    private GoopMapping processRecipe(IRecipe<?> r, Map<IRecipe<?>, GoopMapping> recipeMappings)
    {
        ItemStack output = r.getRecipeOutput();
        if (output.isEmpty()) {
            return UNKNOWN;
        }
        List<Ingredient> inputs = r.getIngredients();
        GoopMapping product = EMPTY;
        for (Ingredient g : inputs)
        {
            // ingredient is air or otherwise unmapped.
            if (g.hasNoMatchingItems())
            {
                continue;
            }

            // get the lowest weight of this slot's inputs: any potential ingredient might not be worth as much as cognates. We want the lowest.
            GoopMapping inputWorth = getLowestDensityMapping(recipeMappings, g);

            product = product.add(inputWorth);
        }

        product = product.divide(output.getCount());
        if (product.isEmpty()) {
            return UNKNOWN;
        }
        // divide the recipe net weight by its yield count, as so far we've only summed up the input values.
        // if this results in a bad division the mapping gets unknown'd
        return product;
    }


    private GoopMapping getLowestDensityMapping(Map<IRecipe<?>, GoopMapping> recipeMappings, Ingredient g) {
        GoopMapping lowestResult = UNKNOWN;
        for(ItemStack s : g.getMatchingStacks()) {
            GoopMapping result = findLowestItemStackValueInExistingOrTheoreticalMappings(recipeMappings, s);
            // something about this result isn't usable, we have to skip this input as a potential candidate.
            // if we're being pessimistic, this mapping is invalidated.
            if (result.isUnknown()) {
                continue;
            }
            if (lowestResult == UNKNOWN || result.isStrongerThan(lowestResult)) {
                lowestResult = result;
            }
        }

        return lowestResult;
    }

    /**
     * Returns the mapping from either existing mapping sources or our current library of recipe mappings, whichever we deem to be stronger.
     * Basis of comparison is almost exclusively weight (goop quantity of the composition of all items in the recipe, whatever inputs are the cheapest).
     * @param recipeMappings Mappings found by scraping world recipe management for inputs and outputs and their child values.
     * @param stack The itemstack output we're analyzing to find the lowest value for.
     * @return The goop mapping determined to be the strongest (lowest weight) out of all the mappings available to produce it.
     */
    private GoopMapping findLowestItemStackValueInExistingOrTheoreticalMappings(Map<IRecipe<?>, GoopMapping> recipeMappings, ItemStack stack) {
        // if the stack is empty, return the zero mapping, meaning it is worth nothing. No result can be worth less than this.
        if (stack.isEmpty()) {
            return GoopMapping.EMPTY;
        }

        GoopMapping decidedMapping = pickStrongerMapping(values.get(name(stack)), getLowestOutputMapping(stack, recipeMappings));

        if (decidedMapping.weight() == 0d) {
            return GoopMapping.UNKNOWN;
        }

        // the item is a container item which means crafting with it is "less" the container item in terms of input value.
        if (stack.hasContainerItem()) {
            GoopMapping containerMapping = findLowestItemStackValueInExistingOrTheoreticalMappings(recipeMappings, stack.getContainerItem());
            // if we can't determine the value of the container item, the whole stack must causally be null.
            if (containerMapping.isUnknown()) {
                return GoopMapping.UNKNOWN;
            }

            decidedMapping = decidedMapping.subtract(containerMapping);
        }

        return decidedMapping;
    }

    private GoopMapping pickStrongerMapping(GoopMapping existingMapping, GoopMapping theoreticalMapping) {
        if (existingMapping.isUnusable()) {
            return theoreticalMapping;
        } else {
            if (theoreticalMapping.isUnknown()) {
                return existingMapping;
            }
        }
        return existingMapping.isStrongerThan(theoreticalMapping) ? existingMapping : theoreticalMapping;
    }

    private GoopMapping getLowestOutputMapping(String output, Map<IRecipe<?>, GoopMapping> recipeMappings) {
        Map.Entry<IRecipe<?>, GoopMapping> lowestResult = recipeMappings.entrySet().stream()
                .filter(m -> Objects.requireNonNull(m.getKey().getRecipeOutput().getItem().getRegistryName()).toString().equals(output) &&
                        !m.getValue().isUnknown() && !m.getValue().isDenied() && !m.getValue().isEmpty())
                .min(recipeGoopMappingWeightComparator)
                .orElse(null);

        if (lowestResult == null) {
            return UNKNOWN;
        }

        return lowestResult.getValue();
    }

    private GoopMapping getLowestOutputMapping(ItemStack output, Map<IRecipe<?>, GoopMapping> recipeMappings) {
        return getLowestOutputMapping(name(output), recipeMappings);
    }

    public GoopValueSyncPacketData[] createPacketData() {
        return new GoopValueSyncPacketData[0];
    }

    public void fromPacket(GoopValueSyncPacketData[] data) {
        // TODO
    }

    public GoopMapping get(Item item) {
        return get(Objects.requireNonNull(item.getRegistryName()).toString());
    }

    public GoopMapping get(String registryName)
    {
        if (values.containsKey(registryName)) {
            return values.get(registryName);
        }

        return UNKNOWN;
    }

    public boolean has(Item item)
    {
        return has(Objects.requireNonNull(item.getRegistryName()).toString());
    }

    public boolean has(String string) {
        return values.containsKey(string);
    }
}
