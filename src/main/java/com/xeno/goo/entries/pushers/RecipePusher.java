package com.xeno.goo.entries.pushers;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.GooEntry;
import com.xeno.goo.library.EntryHelper;
import com.xeno.goo.library.ProgressState;
import com.xeno.goo.entries.EntryPhase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

import static com.xeno.goo.library.Compare.recipeGooEntryWeightComparator;
import static com.xeno.goo.library.Compare.stringLexicographicalComparator;
import static com.xeno.goo.library.GooEntry.*;
import static com.xeno.goo.library.GooEntry.UNKNOWN;
import static com.xeno.goo.library.EntryHelper.name;

public class RecipePusher extends EntryPusher
{
    private final RecipeManager recipeManager;
    public RecipePusher(ServerWorld world)
    {
        super(EntryPhase.DERIVED, "", world);
        this.recipeManager = world.getRecipeManager();
    }

    @Override
    protected boolean load()
    {
        return true;
    }

    @Override
    protected void save()
    {
        // NO OP
    }

    @Override
    public void process()
    {
        // NO OP
    }

    @Override
    protected void clearProcessing()
    {
        // NO OP
    }

    @Override
    protected void seedDefaults()
    {
        // NO OP
    }

    @Override
    public ProgressState pushTo(Map<String, GooEntry> target)
    {
        // maybe a little confusing.
        // similar to how tracked push returns whether it was able to improve a mapping
        // the recipe derivation routine uses tracked push too, to determine whether it's
        // flat-mapped the best possible results. It doesn't stop until it stagnates, and then
        // it passes the end result to the final mapping values, to see if they can be improved.
        // Thus, two separate progress tracking states. One for the recipe flattening, the other
        // is the end result.
        boolean isFirstRun = true;
        ProgressState state = ProgressState.STAGNANT;
        while (state == ProgressState.IMPROVED || isFirstRun) {
            isFirstRun = false;
            state = seedRecipeDerivedEntries();
        }
        return super.pushTo(target);
    }

    private ProgressState seedRecipeDerivedEntries() {
        // try to marry the recipe mappings to our current values and report whether we're stagnant
        return EntryHelper.trackedPush(reduceRecipesToFlatMap(), values);
    }

    private Map<String, GooEntry> reduceRecipesToFlatMap() {
        // instantiate a new map representing all the processed recipes and their respective GooValueEntries
        Map<IRecipe<?>, GooEntry> rawRecipeEntries = new HashMap<>();
        recipeManager.getRecipes().forEach(r -> {
            // the input mapping will come back either valid or unknown.
            GooEntry inputEntry = processRecipe(r, rawRecipeEntries);
            rawRecipeEntries.put(r, inputEntry);
        });

        // flatten this to a map where only the best values for each item are permitted
        Map<String, GooEntry> recipeEntries = new TreeMap<>(stringLexicographicalComparator);
        for(Map.Entry<IRecipe<?>, GooEntry> e : rawRecipeEntries.entrySet()) {
            GooEntry mapping = e.getValue();
            if (mapping.isUnknown()) {
                continue;
            }
            IRecipe<?> recipe = e.getKey();
            String name = name(recipe);
            GooEntry result = !recipeEntries.containsKey(name) || mapping.isStrongerThan(recipeEntries.get(name)) ? mapping : recipeEntries.get(name);
            recipeEntries.put(name,  result);
        }

        // return the reduced map
        return recipeEntries;
    }

    private GooEntry processRecipe(IRecipe<?> r, Map<IRecipe<?>, GooEntry> recipeEntries)
    {
        ItemStack output = r.getRecipeOutput();
        if (output.isEmpty()) {
            return UNKNOWN;
        }
        List<Ingredient> inputs = r.getIngredients();
        GooEntry product = EMPTY;
        for (Ingredient g : inputs)
        {
            // ingredient is air or otherwise unmapped.
            if (g.hasNoMatchingItems())
            {
                continue;
            }

            // get the lowest weight of this slot's inputs: any potential ingredient might not be worth as much as cognates. We want the lowest.
            GooEntry inputWorth = getLowestDensityEntry(recipeEntries, g);

            product = product.add(inputWorth);
        }

        // divide the recipe net weight by its yield count, as so far we've only summed up the input values.
        product = product.divide(output.getCount());
        if (product.isEmpty()) {
            return UNKNOWN;
        }
        return product;
    }


    private GooEntry getLowestDensityEntry(Map<IRecipe<?>, GooEntry> recipeEntries, Ingredient g) {
        GooEntry lowestResult = UNKNOWN;
        for(ItemStack s : g.getMatchingStacks()) {
            GooEntry result = findLowestItemStackValueInExistingOrTheoreticalEntries(recipeEntries, s);
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
     * Basis of comparison is almost exclusively weight (goo quantity of the composition of all items in the recipe, whatever inputs are the cheapest).
     * @param recipeEntries Entries found by scraping world recipe management for inputs and outputs and their child values.
     * @param stack The itemstack output we're analyzing to find the lowest value for.
     * @return The goo mapping determined to be the strongest (lowest weight) out of all the mappings available to produce it.
     */
    private GooEntry findLowestItemStackValueInExistingOrTheoreticalEntries(Map<IRecipe<?>, GooEntry> recipeEntries, ItemStack stack) {
        // if the stack is empty, return the zero mapping, meaning it is worth nothing. No result can be worth less than this.
        if (stack.isEmpty()) {
            return GooEntry.EMPTY;
        }

        GooEntry decidedEntry = pickStrongerEntry(GooMod.handler.get(name(stack)), getLowestOutputEntry(stack, recipeEntries));

        if (decidedEntry.weight() == 0d) {
            return GooEntry.UNKNOWN;
        }

        // the item is a container item which means crafting with it is "less" the container item in terms of input value.
        if (stack.hasContainerItem()) {
            GooEntry containerEntry = findLowestItemStackValueInExistingOrTheoreticalEntries(recipeEntries, stack.getContainerItem());
            // if we can't determine the value of the container item, the whole stack must causally be null.
            if (containerEntry.isUnknown()) {
                return GooEntry.UNKNOWN;
            }

            decidedEntry = decidedEntry.subtract(containerEntry);
        }

        return decidedEntry;
    }

    private GooEntry pickStrongerEntry(GooEntry existingEntry, GooEntry theoreticalEntry) {
        if (existingEntry.isUnusable()) {
            return theoreticalEntry;
        } else {
            if (theoreticalEntry.isUnknown()) {
                return existingEntry;
            }
        }
        return existingEntry.isStrongerThan(theoreticalEntry) ? existingEntry : theoreticalEntry;
    }

    private GooEntry getLowestOutputEntry(String output, Map<IRecipe<?>, GooEntry> recipeEntries) {
        Map.Entry<IRecipe<?>, GooEntry> lowestResult = recipeEntries.entrySet().stream()
                .filter(m -> Objects.requireNonNull(m.getKey().getRecipeOutput().getItem().getRegistryName()).toString().equals(output) &&
                        !m.getValue().isUnknown() && !m.getValue().isDenied() && !m.getValue().isEmpty())
                .min(recipeGooEntryWeightComparator)
                .orElse(null);

        if (lowestResult == null) {
            return UNKNOWN;
        }

        return lowestResult.getValue();
    }

    private GooEntry getLowestOutputEntry(ItemStack output, Map<IRecipe<?>, GooEntry> recipeEntries) {
        return getLowestOutputEntry(name(output), recipeEntries);
    }
}
