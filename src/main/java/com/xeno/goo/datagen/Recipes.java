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
        registerGauntletRecipe(consumer);
        registerComboGauntletRecipe(consumer);
        registerCrucibleRecipe(consumer);
        registerMobiusCrucibleRecipe(consumer);
    }

    private void registerGasketRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(Registry.GASKET.get())
                .addIngredient(Items.HONEY_BLOCK)
                .addIngredient(ItemTags.WOOL)
                .setGroup(GooMod.MOD_ID)
                .addCriterion("honey_block", InventoryChangeTrigger.Instance.forItems(Blocks.HONEY_BLOCK))
                .build(consumer);
    }

    private void registerGauntletRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.GAUNTLET.get())
                .patternLine("nn ")
                .patternLine("nln")
                .patternLine("ngn")
                .key('n', Items.IRON_INGOT)
                .key('l', Items.LEATHER)
                .key('g', Registry.GASKET.get())
                .setGroup(GooMod.MOD_ID)
                .addCriterion("gasket", InventoryChangeTrigger.Instance.forItems(Registry.GASKET.get()))
                .build(consumer);
    }

    private void registerComboGauntletRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.COMBO_GAUNTLET.get())
                .patternLine("nnn")
                .patternLine("ngn")
                .patternLine("nnn")
                .key('n', Items.NETHERITE_INGOT)
                .key('g', Registry.GAUNTLET.get())
                .setGroup(GooMod.MOD_ID)
                .addCriterion("gauntlet", InventoryChangeTrigger.Instance.forItems(Registry.GAUNTLET.get()))
                .build(consumer);
    }

    private void registerCrucibleRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.CRUCIBLE.get())
                .patternLine("ngn")
                .patternLine("nbn")
                .patternLine("ncn")
                .key('n', Items.NETHERITE_INGOT)
                .key('g', Registry.GASKET.get())
                .key('b', Registry.GOO_BULB.get())
                .key('c', Items.CAULDRON)
                .setGroup(GooMod.MOD_ID)
                .addCriterion("goo_bulb", InventoryChangeTrigger.Instance.forItems(Registry.GOO_BULB.get()))
                .build(consumer);
    }

    private void registerMobiusCrucibleRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(Registry.MOBIUS_CRUCIBLE.get())
                .patternLine("ccc")
                .patternLine("csc")
                .patternLine("ccc")
                .key('s', Items.SHULKER_SHELL)
                .key('c', Registry.CRUCIBLE.get())
                .setGroup(GooMod.MOD_ID)
                .addCriterion("crucible", InventoryChangeTrigger.Instance.forItems(Registry.CRUCIBLE.get()))
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
