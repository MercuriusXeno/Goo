package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.data.*;
import net.minecraft.item.Items;
import net.minecraftforge.common.Tags;

import java.util.function.Consumer;

public class RecipesProvider extends RecipeProvider {
    public RecipesProvider(DataGenerator generatorIn) {
        super(generatorIn);
    }

    @Override
    protected void registerRecipes(Consumer<IFinishedRecipe> consumer) {
        registerBookRecipe(consumer);
        registerGasketRecipe(consumer);
        registerGauntletRecipe(consumer);
        registerBasinRecipe(consumer);

        registerGooifierRecipe(consumer);
        registerSolidifierRecipe(consumer);
        registerGooPumpRecipe(consumer);
        registerMixerRecipe(consumer);
        registerCrucibleRecipe(consumer);
        registerGooBulbRecipe(consumer);
        registerLobberRecipe(consumer);
        registerDrainRecipe(consumer);
    }

    private void registerGauntletRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(ItemsRegistry.Gauntlet.get())
                .patternLine("ii ")
                .patternLine("sls")
                .patternLine("igi")
                .key('i', Items.IRON_NUGGET)
                .key('s', Items.NETHERITE_SCRAP)
                .key('l', Items.LEATHER)
                .key('g', ItemsRegistry.Gasket.get())
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    private void registerBasinRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(ItemsRegistry.Basin.get())
                .patternLine(" g ")
                .patternLine("scs")
                .patternLine(" b ")
                .key('s', Items.NETHERITE_SCRAP)
                .key('g', ItemsRegistry.Gasket.get())
                .key('b', Items.MAGMA_CREAM)
                .key('c', Items.BUCKET)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    // doesn't work, needs a custom serializer to get the NBT on the stack to identify
    // it isn't just any patchouli book, but goo's patchouli book. leaving this a manual recipe for the time being.
    private void registerBookRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(ItemsRegistry.GooAndYou.get())
                .addIngredient(ItemsRegistry.Gasket.get())
                .addIngredient(Items.BOOK)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    private void registerGasketRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(ItemsRegistry.Gasket.get(), 3)
                .addIngredient(Items.HONEYCOMB)
                .addIngredient(Items.GOLD_INGOT)
                .addIngredient(Items.MAGMA_CREAM)
                .addCriterion("honeycomb", InventoryChangeTrigger.Instance.forItems(Items.HONEYCOMB))
                .build(consumer);
    }

    private void registerGooBulbRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.GooBulb.get())
                .patternLine("oeo")
                .patternLine("o#o")
                .patternLine("oeo")
                .key('o', ItemsRegistry.Gasket.get())
                .key('e', Items.ENDER_PEARL)
                .key('#', Tags.Items.GLASS)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    private void registerGooPumpRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.GooPump.get())
                .patternLine("so ")
                .patternLine("pg ")
                .patternLine("bof")
                .key('s', Items.POLISHED_BLACKSTONE_SLAB)
                .key('o', ItemsRegistry.Gasket.get())
                .key('p', Items.PISTON)
                .key('g', Items.GLASS)
                .key('f', Items.ITEM_FRAME)
                .key('b', Items.POLISHED_BASALT)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    private void registerGooifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Gooifier.get())
                .patternLine("non")
                .patternLine("obo")
                .patternLine("sfs")
                .key('n', Items.CRYING_OBSIDIAN)
                .key('o', ItemsRegistry.Gasket.get())
                .key('b', Items.BREWING_STAND)
                .key('f', Items.BLAST_FURNACE)
                .key('s', Items.POLISHED_BLACKSTONE)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    private void registerSolidifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Solidifier.get())
                .patternLine("ro ")
                .patternLine("nxp")
                .patternLine("o#o")
                .key('r', Items.REDSTONE_LAMP)
                .key('n', Items.NETHER_BRICKS)
                .key('o', ItemsRegistry.Gasket.get())
                .key('p', Items.STICKY_PISTON)
                .key('x', Items.DISPENSER)
                .key('#', Items.ITEM_FRAME)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    private void registerMixerRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Mixer.get())
                .patternLine("n n")
                .patternLine("bcb")
                .patternLine("npn")
                .key('b', BlocksRegistry.GooBulb.get())
                .key('c', Items.CAULDRON)
                .key('n', Items.NETHERITE_SCRAP)
                .key('p', BlocksRegistry.GooPump.get())
                .addCriterion("netherite", InventoryChangeTrigger.Instance.forItems(Items.NETHERITE_SCRAP))
                .build(consumer);
    }

    private void registerCrucibleRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Crucible.get())
                .patternLine("xsx")
                .patternLine("scs")
                .patternLine("fbf")
                .key('x', Items.IRON_BARS)
                .key('s', Items.NETHERITE_SCRAP)
                .key('c', Items.CAULDRON)
                .key('f', Items.BLAST_FURNACE)
                .key('b', BlocksRegistry.GooBulb.get())
                .addCriterion("netherite", InventoryChangeTrigger.Instance.forItems(Items.NETHERITE_SCRAP))
                .build(consumer);
    }

    private void registerDrainRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Drain.get())
                .patternLine("ibi")
                .patternLine("bgb")
                .patternLine("ibi")
                .key('b', Items.IRON_BARS)
                .key('i', Items.IRON_INGOT)
                .key('g', ItemsRegistry.Gasket.get())
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.Gasket.get()))
                .build(consumer);
    }

    private void registerLobberRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Lobber.get())
                .patternLine("sgs")
                .patternLine("gdg")
                .patternLine("grg")
                .key('d', Items.DISPENSER)
                .key('s', Items.NETHERITE_SCRAP)
                .key('g', ItemsRegistry.Gasket.get())
                .key('r', Items.REDSTONE)
                .addCriterion("netherite", InventoryChangeTrigger.Instance.forItems(Items.NETHERITE_SCRAP))
                .build(consumer);
    }

}
