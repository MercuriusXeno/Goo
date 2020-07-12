package com.xeno.goop.datagen;

import com.xeno.goop.setup.Registration;
import net.minecraft.data.*;
import net.minecraft.item.Items;
import net.minecraft.tags.ItemTags;

import java.util.function.Consumer;

public class Recipes extends RecipeProvider {
    public Recipes(DataGenerator generatorIn) {
        super(generatorIn);
    }

    @Override
    protected void registerRecipes(Consumer<IFinishedRecipe> consumer) {

        ShapelessRecipeBuilder.shapelessRecipe(Registration.GASKET.get())
                .addIngredient(Items.HONEY_BLOCK)
                .addIngredient(Items.SLIME_BLOCK)
                .addIngredient(ItemTags.WOOL)

    }
}
