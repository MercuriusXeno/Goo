package com.xeno.goop.datagen;

import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.Registration;
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
        ShapelessRecipeBuilder.shapelessRecipe(Registration.GASKET.get())
                .addIngredient(Items.HONEY_BLOCK)
                .addIngredient(Items.SLIME_BLOCK)
                .addIngredient(ItemTags.WOOL)
                .setGroup(GoopMod.MOD_ID)
                // interacting with honey for the first time should typically involve getting comb for the first time
                // or the bottle; picking comb here sort of arbitrarily.
                .addCriterion("honey", InventoryChangeTrigger.Instance.forItems(Items.HONEYCOMB))
                .build(consumer);
    }

    private void registerGoopBulbRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registration.GOOP_BULB.get())
                .patternLine("oeo")
                .patternLine("o#o")
                .patternLine("oeo")
                .key('o', Registration.GASKET.get())
                .key('e', Items.ENDER_PEARL)
                .key('#', Tags.Items.GLASS)
                .setGroup(GoopMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registration.GASKET.get()))
                .build(consumer);
    }

    private void registerGoopifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registration.GOOPIFIER.get())
                .patternLine("non")
                .patternLine("pcp")
                .patternLine("opo")
                .key('n', Items.NETHER_BRICKS)
                .key('o', Registration.GASKET.get())
                .key('p', Items.PISTON)
                .key('c', Items.MAGMA_BLOCK)
                .setGroup(GoopMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registration.GASKET.get()))
                .build(consumer);
    }

    private void registerSolidifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registration.SOLIDIFIER.get())
                .patternLine("ror")
                .patternLine("pxp")
                .patternLine("o#o")
                .key('r', Items.REDSTONE_LAMP)
                .key('o', Registration.GASKET.get())
                .key('p', Items.STICKY_PISTON)
                .key('x', Items.DISPENSER)
                .key('#', Items.ITEM_FRAME)
                .setGroup(GoopMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registration.GASKET.get()))
                .build(consumer);
    }
}
