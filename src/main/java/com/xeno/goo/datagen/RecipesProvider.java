package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalBlock;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.items.ItemsRegistry;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.data.*;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fml.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RecipesProvider extends RecipeProvider {
    public RecipesProvider(DataGenerator generatorIn) {
        super(generatorIn);
    }

    @Override
    protected void registerRecipes(Consumer<IFinishedRecipe> consumer) {
        registerNetheriteAshRecipe(consumer);
        registerPassivatedNuggetRecipe(consumer);
        registerPassivatedIngotRecipe(consumer);
        registerPassivatedBlockRecipe(consumer);
        registerBookRecipe(consumer);
        registerGasketRecipe(consumer);
        registerGauntletRecipe(consumer);
        registerVesselRecipe(consumer);
        registerGooifierRecipe(consumer);
        registerSolidifierRecipe(consumer);
        registerGooPumpRecipe(consumer);
        registerMixerRecipe(consumer);
        registerDegraderRecipe(consumer);
        registerGooBulbRecipe(consumer);
        registerLobberRecipe(consumer);
        registerDrainRecipe(consumer);
        registerCrystalNestRecipe(consumer);
        registerTroughRecipe(consumer);
        registerPadRecipe(consumer);

        registerDecorativeBlocks(consumer);
    }

    private void registerPassivatedNuggetRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(ItemsRegistry.PASSIVATED_NUGGET.get(), 9)
                .addIngredient(ItemsRegistry.PASSIVATED_INGOT.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerPassivatedIngotRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(ItemsRegistry.PASSIVATED_INGOT.get())
                .patternLine("nnn")
                .patternLine("nnn")
                .patternLine("nnn")
                .key('n', ItemsRegistry.PASSIVATED_NUGGET.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer, new ResourceLocation(GooMod.MOD_ID, "passivated_ingot_from_nuggets"));
        ShapelessRecipeBuilder.shapelessRecipe(ItemsRegistry.PASSIVATED_INGOT.get(), 9)
                .addIngredient(BlocksRegistry.PassivatedBlock.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer, new ResourceLocation(GooMod.MOD_ID, "passivated_ingot_from_block"));
    }

    private void registerPassivatedBlockRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.PassivatedBlock.get())
                .patternLine("iii")
                .patternLine("iii")
                .patternLine("iii")
                .key('i', ItemsRegistry.PASSIVATED_INGOT.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerNetheriteAshRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(ItemsRegistry.NETHERITE_ASH.get())
                .addIngredient(Items.NETHERITE_SCRAP)
                .addIngredient(ItemsRegistry.STYGIAN_WEEPINGS.get())
                .addCriterion("netherite_scrap", InventoryChangeTrigger.Instance.forItems(Items.NETHERITE_SCRAP))
                .build(consumer);
    }

    private void registerGauntletRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(ItemsRegistry.GAUNTLET.get())
                .patternLine("nn ")
                .patternLine("nln")
                .patternLine("ngn")
                .key('n', ItemsRegistry.PASSIVATED_NUGGET.get())
                .key('l', Items.LEATHER)
                .key('g', ItemsRegistry.GASKET.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerVesselRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(ItemsRegistry.VESSEL.get())
                .addIngredient(Items.LAVA_BUCKET)
                .addIngredient(ItemsRegistry.NETHERITE_ASH.get())
                .addCriterion("netherite_ash", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.NETHERITE_ASH.get()))
                .build(consumer);
    }

    // doesn't work, needs a custom serializer to get the NBT on the stack to identify
    // it isn't just any patchouli book, but goo's patchouli book. leaving this a manual recipe for the time being.
    private void registerBookRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapelessRecipe(ItemsRegistry.GOO_AND_YOU.get())
                .addIngredient(ItemsRegistry.GASKET.get())
                .addIngredient(Items.BOOK)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerGasketRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(ItemsRegistry.GASKET.get(), 6)
                .patternLine(" n ")
                .patternLine("n n")
                .patternLine(" n ")
                .key('n', ItemsRegistry.PASSIVATED_NUGGET.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerGooBulbRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Bulb.get())
                .patternLine("non")
                .patternLine(" g ")
                .patternLine("non")
                .key('n', ItemsRegistry.PASSIVATED_NUGGET.get())
                .key('o', ItemsRegistry.GASKET.get())
                .key('g', Tags.Items.GLASS)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerGooPumpRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Pump.get())
                .patternLine("so ")
                .patternLine("pg ")
                .patternLine("bof")
                .key('s', Items.POLISHED_BLACKSTONE_SLAB)
                .key('o', ItemsRegistry.GASKET.get())
                .key('p', Items.PISTON)
                .key('g', Items.GLASS)
                .key('f', Items.ITEM_FRAME)
                .key('b', Items.POLISHED_BASALT)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerGooifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Gooifier.get())
                .patternLine("non")
                .patternLine("obo")
                .patternLine("sfs")
                .key('n', Items.CRYING_OBSIDIAN)
                .key('o', ItemsRegistry.GASKET.get())
                .key('b', Items.BREWING_STAND)
                .key('f', Items.BLAST_FURNACE)
                .key('s', Items.POLISHED_BLACKSTONE)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerSolidifierRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Solidifier.get())
                .patternLine("ro ")
                .patternLine("nxp")
                .patternLine("o#o")
                .key('r', Items.REDSTONE_LAMP)
                .key('n', Items.NETHER_BRICKS)
                .key('o', ItemsRegistry.GASKET.get())
                .key('p', Items.STICKY_PISTON)
                .key('x', Items.DISPENSER)
                .key('#', Items.ITEM_FRAME)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerMixerRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Mixer.get())
                .patternLine("gsg")
                .patternLine("obo")
                .patternLine("fgp")
                .key('g', Items.GLASS)
                .key('s', ItemsRegistry.PASSIVATED_INGOT.get())
                .key('o', ItemsRegistry.GASKET.get())
                .key('b', BlocksRegistry.PassivatedBlock.get())
                .key('f', Items.BLAST_FURNACE)
                .key('p', Items.PISTON)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerDegraderRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Degrader.get())
                .patternLine("sos")
                .patternLine("sgs")
                .patternLine("fos")
                .key('s', ItemsRegistry.PASSIVATED_INGOT.get())
                .key('o', ItemsRegistry.GASKET.get())
                .key('f', Items.BLAST_FURNACE)
                .key('g', Items.GLASS)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerDrainRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Drain.get())
                .patternLine("ibi")
                .patternLine("bgb")
                .patternLine("ibi")
                .key('b', Items.IRON_BARS)
                .key('i', Items.IRON_INGOT)
                .key('g', ItemsRegistry.GASKET.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerLobberRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Lobber.get())
                .patternLine("sgs")
                .patternLine("gdg")
                .patternLine("grg")
                .key('d', Items.DISPENSER)
                .key('s', Items.NETHERITE_SCRAP)
                .key('g', ItemsRegistry.GASKET.get())
                .key('r', Items.REDSTONE)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerCrystalNestRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.CrystalNest.get())
                .patternLine("ccc")
                .patternLine("cnc")
                .patternLine("ccc")
                .key('c', ItemsRegistry.CRYSTALLIZED_GOO
                        .get(new ResourceLocation(GooMod.MOD_ID, "crystal_goo_shard")).get())
                .key('n', Items.BEE_NEST)
                .addCriterion("bee_nest", InventoryChangeTrigger.Instance.forItems(Items.BEE_NEST))
                .build(consumer);
    }

    private void registerTroughRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Trough.get())
                .patternLine("  w")
                .patternLine("w g")
                .patternLine("ddd")
                .key('w', Items.DIORITE_WALL)
                .key('d', Items.DIORITE_SLAB)
                .key('g', ItemsRegistry.GASKET.get())
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerPadRecipe(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shapedRecipe(BlocksRegistry.Pad.get())
                .patternLine(" o ")
                .patternLine("igi")
                .patternLine(" o ")
                .key('o', ItemsRegistry.GASKET.get())
                .key('i', ItemsRegistry.PASSIVATED_INGOT.get())
                .key('g', Items.GLASS)
                .addCriterion("passivated_ingot", InventoryChangeTrigger.Instance.forItems(ItemsRegistry.PASSIVATED_INGOT.get()))
                .build(consumer);
    }

    private void registerDecorativeBlocks(Consumer<IFinishedRecipe> consumer) {
        BlocksRegistry.CrystalBlocks.forEach((k, v) -> registerDecorativeBlockRecipe(k, v, consumer));
    }

    private void registerDecorativeBlockRecipe(ResourceLocation resourceLocation, RegistryObject<CrystalBlock> crystalBlockRegistryObject, Consumer<IFinishedRecipe> consumer) {
        if (!crystalBlockRegistryObject.isPresent()) {
            return;
        }
        String baseKey = resourceLocation.getPath();
        int index = baseKey.indexOf("_goo_") + 4;
        String streamKey = resourceLocation.getPath().substring(0, index);
        String crystalInputKey = streamKey + "_shard";
        ResourceLocation crystalInput =  new ResourceLocation(GooMod.MOD_ID, crystalInputKey);
        Supplier<CrystallizedGooAbstract> item = ItemsRegistry.CRYSTALLIZED_GOO.get(crystalInput);

        if (resourceLocation.getPath().contains("_smooth")) {
            ShapedRecipeBuilder
                    .shapedRecipe(crystalBlockRegistryObject.get())
                    .patternLine("cc")
                    .patternLine("cc")
                    .key('c', item.get())
                    .addCriterion(crystalInputKey, InventoryChangeTrigger.Instance.forItems(item.get()))
                    .build(consumer);
        }
        Ingredient ingredients = ingredientsForStreamKey(streamKey);
        SingleItemRecipeBuilder
                .stonecuttingRecipe(ingredients, crystalBlockRegistryObject.get())
                .addCriterion(crystalInputKey, InventoryChangeTrigger.Instance.forItems(item.get()))
                .build(consumer, new ResourceLocation(GooMod.MOD_ID, resourceLocation.getPath() + "_cutting"));
    }

    private Ingredient ingredientsForStreamKey(String streamKey) {
        List<IItemProvider> blocks = new ArrayList<>();
        ItemsRegistry.CRYSTAL_BLOCKS.forEach((k, v) -> {
            if (k.getPath().contains(streamKey)) {
                blocks.add(v.get());
            }
        });
        return Ingredient.fromItems(blocks.toArray(new IItemProvider[0]));
    }
}
