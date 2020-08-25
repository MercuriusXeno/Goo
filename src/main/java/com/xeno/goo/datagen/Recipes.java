package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.block.Blocks;
import net.minecraft.data.*;
import net.minecraft.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraftforge.common.Tags;

import java.util.function.Consumer;

public class Recipes extends RecipeProvider {
    public Recipes(DataGenerator generatorIn) {
        super(generatorIn);
    }

    @Override
    protected void registerRecipes(Consumer<IFinishedRecipe> consumer) {
        registerGasketRecipe(consumer);
        registerGooBulbRecipe(consumer);
        registerGooifierRecipe(consumer);
        registerSolidifierRecipe(consumer);
    }

    private void registerGasketRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(Registry.GASKET.get(), 3)
                .addIngredient(Items.HONEYCOMB)
                .addIngredient(Items.GOLD_NUGGET)
                .addIngredient(Items.MAGMA_CREAM)
                .setGroup(GooMod.MOD_ID)
                .addCriterion("honeycomb", InventoryChangeTrigger.Instance.forItems(Items.HONEYCOMB))
                .build(consumer);
    }

    private void registerGooBulbRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOO_BULB.get())
                .patternLine("oeo")
                .patternLine("o#o")
                .patternLine("oeo")
                .key('o', Registry.GASKET.get())
                .key('e', Items.ENDER_PEARL)
                .key('#', Tags.Items.GLASS)
                .setGroup(GooMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerGooifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOOIFIER.get())
                .patternLine("non")
                .patternLine("pcp")
                .patternLine("opo")
                .key('n', Items.NETHER_BRICKS)
                .key('o', Registry.GASKET.get())
                .key('p', Items.PISTON)
                .key('c', Items.MAGMA_BLOCK)
                .setGroup(GooMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerSolidifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.SOLIDIFIER.get())
                .patternLine("ror")
                .patternLine("pxp")
                .patternLine("o#o")
                .key('r', Items.REDSTONE_LAMP)
                .key('o', Registry.GASKET.get())
                .key('p', Items.STICKY_PISTON)
                .key('x', Items.DISPENSER)
                .key('#', Items.ITEM_FRAME)
                .setGroup(GooMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }
}
