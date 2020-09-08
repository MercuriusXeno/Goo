package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.block.Blocks;
import net.minecraft.data.*;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.ShapelessRecipe;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.Tags;
import vazkii.patchouli.api.PatchouliAPI;

import javax.sound.midi.Patch;
import java.util.function.Consumer;

public class Recipes extends RecipeProvider {
    public Recipes(DataGenerator generatorIn) {
        super(generatorIn);
    }

    @Override
    protected void registerRecipes(Consumer<IFinishedRecipe> consumer) {
        registerBookRecipe(consumer);
        registerGasketRecipe(consumer);
        registerGooifierRecipe(consumer);
        registerSolidifierRecipe(consumer);
        registerGooPumpRecipe(consumer);
        registerMixerRecipe(consumer);
        registerCrucibleRecipe(consumer);
        registerGooBulbRecipe(consumer);
        registerGooBulbMk2Recipe(consumer);
        registerGooBulbMk3Recipe(consumer);
        registerGooBulbMk4Recipe(consumer);
        registerGooBulbMk5Recipe(consumer);
    }

    // doesn't work, needs a custom serializer to get the NBT on the stack to identify
    // it isn't just any patchouli book, but goo's patchouli book. leaving this a manual recipe for the time being.
    private void registerBookRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(Registry.GOO_AND_YOU.get())
                .addIngredient(Registry.GASKET.get())
                .addIngredient(Items.BOOK)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerGasketRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(Registry.GASKET.get(), 3)
                .addIngredient(Items.HONEYCOMB)
                .addIngredient(Items.GOLD_INGOT)
                .addIngredient(Items.MAGMA_CREAM)
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
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerGooBulbMk2Recipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOO_BULB_MK2.get())
                .patternLine("geg")
                .patternLine("gbg")
                .patternLine("ggg")
                .key('g', Items.GLASS_PANE)
                .key('b', Registry.GOO_BULB.get())
                .key('e', Items.ENDER_EYE)
                .addCriterion("goo_bulb", InventoryChangeTrigger.Instance.forItems(Registry.GOO_BULB.get()))
                .build(consumer);
    }

    private void registerGooBulbMk3Recipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOO_BULB_MK3.get())
                .patternLine("geg")
                .patternLine("gbg")
                .patternLine("ggg")
                .key('g', Items.GLASS_PANE)
                .key('b', Registry.GOO_BULB_MK2.get())
                .key('e', Items.END_ROD)
                .addCriterion("goo_bulb_mk2", InventoryChangeTrigger.Instance.forItems(Registry.GOO_BULB_MK2.get()))
                .build(consumer);
    }

    private void registerGooBulbMk4Recipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOO_BULB_MK4.get())
                .patternLine("geg")
                .patternLine("gbg")
                .patternLine("ggg")
                .key('g', Items.GLASS_PANE)
                .key('b', Registry.GOO_BULB_MK3.get())
                .key('e', Items.ENDER_CHEST)
                .addCriterion("goo_bulb_mk3", InventoryChangeTrigger.Instance.forItems(Registry.GOO_BULB_MK3.get()))
                .build(consumer);
    }

    private void registerGooBulbMk5Recipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOO_BULB_MK5.get())
                .patternLine("geg")
                .patternLine("gbg")
                .patternLine("ggg")
                .key('g', Items.GLASS_PANE)
                .key('b', Registry.GOO_BULB_MK4.get())
                .key('e', Items.SHULKER_BOX)
                .addCriterion("goo_bulb_mk4", InventoryChangeTrigger.Instance.forItems(Registry.GOO_BULB_MK4.get()))
                .build(consumer);
    }

    private void registerGooPumpRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOO_PUMP.get())
                .patternLine("so ")
                .patternLine("pg ")
                .patternLine("bof")
                .key('s', Items.POLISHED_BLACKSTONE_SLAB)
                .key('o', Registry.GASKET.get())
                .key('p', Items.PISTON)
                .key('g', Items.GLASS)
                .key('f', Items.ITEM_FRAME)
                .key('b', Items.POLISHED_BASALT)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerGooifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GOOIFIER.get())
                .patternLine("non")
                .patternLine("pfp")
                .patternLine("obo")
                .key('n', Items.CRYING_OBSIDIAN)
                .key('o', Registry.GASKET.get())
                .key('b', Items.BREWING_STAND)
                .key('p', Items.PISTON)
                .key('f', Items.BLAST_FURNACE)
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
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerMixerRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.MIXER.get())
                .patternLine("n n")
                .patternLine("bcb")
                .patternLine("npn")
                .key('b', Registry.GOO_BULB.get())
                .key('c', Items.CAULDRON)
                .key('n', Items.NETHERITE_INGOT)
                .key('p', Registry.GOO_PUMP.get())
                .addCriterion("netherite", InventoryChangeTrigger.Instance.forItems(Items.NETHERITE_INGOT))
                .build(consumer);
    }

    private void registerCrucibleRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.CRUCIBLE.get())
                .patternLine("xix")
                .patternLine("ici")
                .patternLine("fbf")
                .key('x', Items.IRON_BARS)
                .key('i', Items.NETHERITE_INGOT)
                .key('c', Items.CAULDRON)
                .key('f', Items.BLAST_FURNACE)
                .key('b', Registry.GOO_BULB.get())
                .addCriterion("netherite", InventoryChangeTrigger.Instance.forItems(Items.NETHERITE_INGOT))
                .build(consumer);
    }
}
