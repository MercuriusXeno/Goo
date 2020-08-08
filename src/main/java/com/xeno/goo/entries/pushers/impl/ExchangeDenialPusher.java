package com.xeno.goo.entries.pushers.impl;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.GooEntry;
import com.xeno.goo.library.ProgressState;
import com.xeno.goo.entries.EntryPhase;
import com.xeno.goo.entries.pushers.EntryPusher;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

import static com.xeno.goo.library.GooEntry.*;
import static com.xeno.goo.library.EntryHelper.name;

/**
 * A recipe denial is the result of an input being denied and no alternative input existing.
 * Such a denial is also referred to as an implicit denial.
 */
public class ExchangeDenialPusher extends EntryPusher
{
    private final RecipeManager recipeManager;
    public ExchangeDenialPusher(ServerWorld world)
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
        this.resolve();
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
        seedRecipeDeniedMappings();
        return super.pushTo(target);
    }

    private void seedRecipeDeniedMappings() {
        for(IRecipe<?> r : recipeManager.getRecipes()) {
            ItemStack output = r.getRecipeOutput();
            String name = name(output);
            // are we already denied?
            if (GooMod.mappingHandler.get(name).isDenied()) {
                continue;
            }
            // if the output already has a mapping it means we're explicitly giving it one, by this point
            // so don't deny it, even if the inputs are all denied; this is true of ore blocks and will cause stupidity
            if (!GooMod.mappingHandler.get(name).isEmpty()) {
                continue;
            }
            List<Ingredient> inputs = r.getIngredients();
            for (Ingredient g : inputs) {
                if (g.hasNoMatchingItems()) {
                    continue;
                }
                if (onlyDeniedMappingsExist(g)) {
                    values.put(name, DENIED);
                }
            }
        }
    }

    private boolean onlyDeniedMappingsExist(Ingredient g) {
        for(ItemStack s : g.getMatchingStacks()) {
            if (s.isEmpty()) {
                // empty list is acceptable here - there is no input to map.
                // anything empty is a valid [matching] ingredient for must inherently also be empty or valueless.
                return false;
            }
            String name = name(s);

            if (GooMod.mappingHandler.has(name)) {
                GooEntry currentInputMapping = GooMod.mappingHandler.get(name);
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
}
