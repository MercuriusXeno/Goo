package com.xeno.goop.datagen;

import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.Registry;
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
        registerGoopBulbRecipe(consumer);
        registerGoopifierRecipe(consumer);
        registerSolidifierRecipe(consumer);
    }

    private void registerGasketRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(Registry.GASKET.get())
                .addIngredient(Items.HONEY_BLOCK)
                .addIngredient(Items.SLIME_BLOCK)
                .addIngredient(ItemTags.WOOL)
                .setGroup(GoopMod.MOD_ID)
                .addCriterion("honey_block", InventoryChangeTrigger.Instance.forItems(Blocks.HONEY_BLOCK))
                .build(consumer);
    }

    private void registerGoopBulbRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOOP_BULB.get())
                .patternLine("oeo")
                .patternLine("o#o")
                .patternLine("oeo")
                .key('o', Registry.GASKET.get())
                .key('e', Items.ENDER_PEARL)
                .key('#', Tags.Items.GLASS)
                .setGroup(GoopMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerGoopifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOOPIFIER.get())
                .patternLine("non")
                .patternLine("pcp")
                .patternLine("opo")
                .key('n', Items.NETHER_BRICKS)
                .key('o', Registry.GASKET.get())
                .key('p', Items.PISTON)
                .key('c', Items.MAGMA_BLOCK)
                .setGroup(GoopMod.MOD_ID)
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
                .setGroup(GoopMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }
}
