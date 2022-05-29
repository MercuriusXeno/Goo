package com.xeno.goo.datagen;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.datagen.ForcedInformationProvider;
import com.xeno.goo.GooMod;
import com.xeno.goo.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class AequivaleoInformationProvider extends ForcedInformationProvider
{

    private final int terrestrialBlocksEarth = 4;
    private final int smallPlantsNature = 1;
    private final int logsNature = 16;
    private final int flowersNature = 4;
    private final int tallPlantsMultiplier = 2;
    private final int mostFoodNature = 4;
    private final int fireAsFuel = 1;
    private final int  fishWater = 4;
    private final int  coralNature = 1;
    private final int  coralWater = 1;
    private final int  coralBlockMultiplier = 4;

    AequivaleoInformationProvider(final DataGenerator dataGenerator)
    {
        super(GooMod.MOD_ID, dataGenerator);
    }

    @Override
    public void calculateDataToSave()
    {
        // plants and stuff
        savePlantsAndStuff();

        // aquatic stuff and fish
        saveAquaticStuffAndFish();

        // rocks and dirt
        saveRocksAndDirt();

        // most normal foods
        saveMostFoods(nature(mostFoodNature));

        // ores, metal ingots, gems
        saveOresAndMinerals();

        // random terrestrial nonsense
        saveMiscTerrestrialStuff();

        // nether
        saveNetherStuff();

        // ender
        saveEnderStuff();

        // containers
        saveData(Items.WATER_BUCKET, water(64));
        saveData(Items.LAVA_BUCKET, fire(fireAsFuel * 100));

        // controversial
        saveData(Items.NETHER_STAR, light(1600), nether(1600), forbidden(1));
        saveData(Items.HEART_OF_THE_SEA, water(1600), forbidden(1));
    }

    private void saveMiscTerrestrialStuff() {
        saveData(Items.GLASS, earth(terrestrialBlocksEarth), fire(fireAsFuel), crystal(1));
        saveData(Items.CHARCOAL, fire(fireAsFuel * 8), nature(smallPlantsNature));
        saveData(Items.BRICK, earth(16), fire(fireAsFuel));
        saveData(Items.POISONOUS_POTATO, nature(mostFoodNature), nether(4));
        saveData(Items.CLAY_BALL, earth(16), water(4));
        saveData(Items.FEATHER, nature(16), air(64));
        saveData(Items.BROWN_MUSHROOM_BLOCK, nature(smallPlantsNature), air(4));
        saveData(Items.RED_MUSHROOM_BLOCK, nature(smallPlantsNature), air(4));
        saveData(Items.COBWEB, nature(16), air(4));
        saveData(Items.PUMPKIN, nature(64));
        saveData(Items.CARVED_PUMPKIN, nature(4), air(60));
        saveData(Items.BELL, earth(128), metal(192), nature(128));
        saveData(Items.GUNPOWDER, fire(64), lightning(64));
        saveData(Items.HONEYCOMB, nature(32));
        saveData(Items.HONEY_BLOCK, nature(96));
        saveData(Items.ICE, ice(16));
        saveData(Items.MAGMA_BLOCK, fire(64), earth(terrestrialBlocksEarth));
        saveData(Items.OBSIDIAN, fire(terrestrialBlocksEarth), earth(terrestrialBlocksEarth), dark(96));
        saveData(Items.PHANTOM_MEMBRANE, air(256), ender(64));
        saveData(Items.RABBIT_FOOT, nature(32));
        saveData(Items.ROTTEN_FLESH, nature(mostFoodNature), nether(4), dark(16));
        saveData(Items.SLIME_BALL, nature(32));
        saveData(Items.SNOWBALL, ice(4));
        saveData(Items.SPIDER_EYE, nature(mostFoodNature), dark(64));
    }

    private void saveEnderStuff() {
        saveData(Items.CHORUS_FLOWER, nature(32), ender(8));
        saveData(Items.CHORUS_FRUIT, nature(16), ender(4));
        saveData(Items.POPPED_CHORUS_FRUIT, nature(16), ender(4), fire(fireAsFuel));
        saveData(Items.END_ROD, light(fireAsFuel * 4), ender(1));
        saveData(Items.END_STONE, earth(terrestrialBlocksEarth), ender(1));
        saveData(Items.ENDER_PEARL, ender(32));
        saveData(Items.SHULKER_SHELL, air(640), ender(64));
    }

    private void saveNetherStuff() {
        saveData(Items.NETHER_WART, nether(4), nature(smallPlantsNature));
        saveData(Items.WARPED_WART_BLOCK, ender(36), nature(36));
        saveData(Items.NETHERRACK, earth((int)(terrestrialBlocksEarth / 4)), nether(4));
        saveData(Items.CRIMSON_NYLIUM, earth(terrestrialBlocksEarth), nature(logsNature), nether(16));
        saveData(Items.WARPED_NYLIUM, earth(terrestrialBlocksEarth), nature(logsNature), nether(16));
        saveData(Items.CRYING_OBSIDIAN, nether(64), water(16), earth(32), crystal(32));
        saveData(Items.GHAST_TEAR, nether(32), crystal(24));
        saveData(Items.GLOWSTONE_DUST, light(16));
        saveData(Items.SHROOMLIGHT, light(32), nature(32), nether(16));
        saveData(Items.SOUL_SAND, earth(terrestrialBlocksEarth), nether(64));
        saveData(Items.SOUL_SOIL, earth(terrestrialBlocksEarth), nether(64));
        saveData(Items.BASALT, earth(terrestrialBlocksEarth), nether(4));
        saveData(Items.BLAZE_ROD, fire(fireAsFuel * 12), nether(16));
    }

    private void saveOresAndMinerals() {
        // ore blocks
        saveData(Items.COAL_ORE, fire(fireAsFuel * 8), earth(terrestrialBlocksEarth + 16), forbidden(1));
        saveData(Items.COAL, fire(fireAsFuel * 8), earth(16));
        saveData(Items.DIAMOND_ORE, crystal(320), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.DIAMOND, crystal(320));
        saveData(Items.EMERALD_ORE, crystal(64), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.EMERALD, crystal(64));
        saveData(Items.NETHER_GOLD_ORE, metal(72), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.GILDED_BLACKSTONE, metal(72), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.GOLD_ORE, metal(72), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.GOLD_INGOT, metal(72), fire(fireAsFuel));
        saveData(Items.IRON_ORE, metal(72), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.IRON_INGOT, metal(72), fire(fireAsFuel));
        saveData(Items.COPPER_ORE, metal(72), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.COPPER_INGOT, metal(72), fire(fireAsFuel));
        saveData(Items.REDSTONE_ORE, lightning(32), earth(terrestrialBlocksEarth), forbidden(1));
        saveData(Items.REDSTONE, lightning(32));
        saveData(Items.LAPIS_ORE, nature(48), earth(terrestrialBlocksEarth), crystal(30), forbidden(1));
        saveData(Items.LAPIS_LAZULI, nature(48), crystal(32));
        saveData(Items.NETHER_QUARTZ_ORE, earth(terrestrialBlocksEarth + 16), crystal (8), forbidden(1));
        saveData(Items.QUARTZ, earth(16), crystal (8));
        saveData(Items.ANCIENT_DEBRIS, metal(720), fire(600), nether(720), dark(720), forbidden(1));
        saveData(Items.NETHERITE_SCRAP, metal(720), fire(600), nether(720), dark(720));
    }

    private void saveRocksAndDirt() {
        saveTerrestrialBlocks(earth(terrestrialBlocksEarth));

        saveData(Items.MOSSY_COBBLESTONE, earth(terrestrialBlocksEarth), nature(smallPlantsNature));
        saveData(Items.PODZOL, earth(terrestrialBlocksEarth), nature(smallPlantsNature));
        saveData(Items.GRASS_BLOCK, earth(terrestrialBlocksEarth), nature(smallPlantsNature));
        saveData(Items.MYCELIUM, earth(terrestrialBlocksEarth), nature(smallPlantsNature));
    }

    private void saveAquaticStuffAndFish() {
        // coral, there's a lot of it
        saveCoral(nature(coralNature), water(coralWater));
        saveCoralBlocks(nature(coralNature * coralBlockMultiplier), water(coralWater * coralBlockMultiplier));

        // other aquatic stuff
        saveData(Items.PRISMARINE_CRYSTALS, water(32), crystal(3), light(6));
        saveData(Items.PRISMARINE_SHARD, water(16), crystal(2), light(4));
        saveData(Items.SCUTE, nature(64), water(64));
        saveData(Items.SEA_PICKLE, water(48), nature(4), light(4), water(16));
        saveData(Items.SEAGRASS, nature(4), water(4));
        saveData(Items.TURTLE_EGG, nature(32), water(64));
        saveData(Items.INK_SAC, nature(48), dark(48), water(48));
        saveData(Items.GLOW_INK_SAC, nature(48), light(48), water(48));
        saveData(Items.WET_SPONGE, water(64), air(64), nature(32));
        saveData(Items.NAUTILUS_SHELL, water(320), nature(32));
        saveMostFish(nature(mostFoodNature), water(fishWater));
    }

    private void savePlantsAndStuff() {
        saveLeavesAndSmallPlants(nature(smallPlantsNature));
        saveTallPlants(nature(smallPlantsNature * tallPlantsMultiplier));
        saveLogsAndSaplings(nature(logsNature));
        saveFlowersAndDye(nature(flowersNature));
        saveDoubleFlowers(nature(flowersNature * 2));
        saveData(Items.BAMBOO, nature((int)(logsNature / 16)));
        saveData(Items.BONE, nature(flowersNature * 3), nether(48));
        saveData(Items.STRING, nature((int)(flowersNature / 4)));
    }

    private void saveMostFish(CompoundInstance nature, CompoundInstance water) {
        saveData(Items.SALMON, nature, water);
        saveData(Items.COD, nature, water);
        saveData(Items.PUFFERFISH, nature, water);
        saveData(Items.TROPICAL_FISH, nature, water);
    }

    private void saveMostFoods(CompoundInstance nature) {
        saveData(Items.APPLE, nature);
        saveData(Items.BEEF, nature);
        saveData(Items.BEETROOT_SEEDS, nature);
        saveData(Items.CRIMSON_FUNGUS, nature);
        saveData(Items.EGG, nature);
        saveData(Items.CARROT, nature);
        saveData(Items.CHICKEN, nature);
        saveData(Items.MELON_SLICE, nature);
        saveData(Items.MUTTON, nature);
        saveData(Items.RABBIT, nature);
        saveData(Items.SUGAR_CANE, nature);
        saveData(Items.WARPED_FUNGUS, nature);
        saveData(Items.WHEAT, nature);
        saveData(Items.WHEAT_SEEDS, nature);
        saveData(Items.MILK_BUCKET, nature);
        saveData(Items.PORKCHOP, nature);
        saveData(Items.POTATO, nature);
        saveData(Items.RABBIT_HIDE, nature);
        saveData(Items.RED_MUSHROOM, nature);
        saveData(Items.BROWN_MUSHROOM, nature);
    }

    private void saveTerrestrialBlocks(CompoundInstance earth) {
        saveData(Items.BLACKSTONE, earth);
        saveData(Items.COBBLESTONE, earth);
        saveData(Items.DIRT, earth);
        saveData(Items.FLINT, earth);
        saveData(Items.GRAVEL, earth);
        saveData(Items.RED_SAND, earth);
        saveData(Items.SAND, earth);
    }

    private void saveTallPlants(CompoundInstance nature) {

        saveData(Items.LARGE_FERN, nature);
        saveData(Items.TALL_GRASS, nature);
    }

    private void saveCoral(CompoundInstance nature, CompoundInstance water) {
        saveData(Items.BRAIN_CORAL, nature, water);
        saveData(Items.BUBBLE_CORAL, nature, water);
        saveData(Items.DEAD_BRAIN_CORAL, nature, water);
        saveData(Items.DEAD_BUBBLE_CORAL, nature, water);
        saveData(Items.DEAD_FIRE_CORAL, nature, water);
        saveData(Items.DEAD_HORN_CORAL, nature, water);
        saveData(Items.DEAD_TUBE_CORAL, nature, water);
        saveData(Items.FIRE_CORAL, nature, water);
        saveData(Items.HORN_CORAL, nature, water);
        saveData(Items.TUBE_CORAL, nature, water);
        saveData(Items.BRAIN_CORAL_FAN, nature, water);
        saveData(Items.BUBBLE_CORAL_FAN, nature, water);
        saveData(Items.DEAD_BRAIN_CORAL_FAN, nature, water);
        saveData(Items.DEAD_BUBBLE_CORAL_FAN, nature, water);
        saveData(Items.DEAD_FIRE_CORAL_FAN, nature, water);
        saveData(Items.DEAD_HORN_CORAL_FAN, nature, water);
        saveData(Items.DEAD_TUBE_CORAL_FAN, nature, water);
        saveData(Items.FIRE_CORAL_FAN, nature, water);
        saveData(Items.HORN_CORAL_FAN, nature, water);
        saveData(Items.TUBE_CORAL_FAN, nature, water);
    }

    private void saveCoralBlocks(CompoundInstance nature, CompoundInstance water) {
        saveData(Items.BRAIN_CORAL_BLOCK, nature, water);
        saveData(Items.BUBBLE_CORAL_BLOCK, nature, water);
        saveData(Items.DEAD_BRAIN_CORAL_BLOCK, nature, water);
        saveData(Items.DEAD_BUBBLE_CORAL_BLOCK, nature, water);
        saveData(Items.DEAD_FIRE_CORAL_BLOCK, nature, water);
        saveData(Items.DEAD_HORN_CORAL_BLOCK, nature, water);
        saveData(Items.DEAD_TUBE_CORAL_BLOCK, nature, water);
        saveData(Items.FIRE_CORAL_BLOCK, nature, water);
        saveData(Items.HORN_CORAL_BLOCK, nature, water);
        saveData(Items.TUBE_CORAL_BLOCK, nature, water);
    }

    private void saveDoubleFlowers(CompoundInstance nature) {
        saveData(Items.LILAC, nature);
        saveData(Items.PEONY, nature);
        saveData(Items.ROSE_BUSH, nature);
        saveData(Items.SUNFLOWER, nature);
    }

    private void saveFlowersAndDye(CompoundInstance nature) {

        saveData(Items.ALLIUM, nature);
        saveData(Items.AZURE_BLUET, nature);
        saveData(Items.BLUE_ORCHID, nature);
        saveData(Items.COCOA_BEANS, nature);
        saveData(Items.CORNFLOWER, nature);
        saveData(Items.DANDELION, nature);
        saveData(Items.LILY_OF_THE_VALLEY, nature);
        saveData(Items.ORANGE_TULIP, nature);
        saveData(Items.OXEYE_DAISY, nature);
        saveData(Items.PINK_TULIP, nature);
        saveData(Items.POPPY, nature);
        saveData(Items.RED_TULIP, nature);
        saveData(Items.WHITE_TULIP, nature);
        saveData(Items.BLACK_DYE, nature);
        saveData(Items.BLUE_DYE, nature);
        saveData(Items.BROWN_DYE, nature);
        saveData(Items.CYAN_DYE, nature);
        saveData(Items.GRAY_DYE, nature);
        saveData(Items.GREEN_DYE, nature);
        saveData(Items.LIGHT_BLUE_DYE, nature);
        saveData(Items.LIGHT_GRAY_DYE, nature);
        saveData(Items.LIME_DYE, nature);
        saveData(Items.MAGENTA_DYE, nature);
        saveData(Items.ORANGE_DYE, nature);
        saveData(Items.PINK_DYE, nature);
        saveData(Items.PURPLE_DYE, nature);
        saveData(Items.RED_DYE, nature);
        saveData(Items.WHITE_DYE, nature);
        saveData(Items.YELLOW_DYE, nature);

        // weird ones, not flowers, but still dyes
        saveData(Items.BEETROOT, nature);
        saveData(Items.CACTUS, nature);
        
        // weirder still, making wool equal to dyes
        saveData(Items.WHITE_WOOL, nature);
        saveData(Items.RED_WOOL, nature);
        saveData(Items.ORANGE_WOOL, nature);
        saveData(Items.YELLOW_WOOL, nature);
        saveData(Items.GREEN_WOOL, nature);
        saveData(Items.BLUE_WOOL, nature);
        saveData(Items.PURPLE_WOOL, nature);
        saveData(Items.PINK_WOOL, nature);
        saveData(Items.LIME_WOOL, nature);
        saveData(Items.CYAN_WOOL, nature);
        saveData(Items.GRAY_WOOL, nature);
        saveData(Items.LIGHT_GRAY_WOOL, nature);
        saveData(Items.BLACK_WOOL, nature);
        saveData(Items.BROWN_WOOL, nature);
        saveData(Items.LIGHT_BLUE_WOOL, nature);
        saveData(Items.MAGENTA_WOOL, nature);
    }

    private void saveLogsAndSaplings(CompoundInstance nature) {
        saveData(Items.ACACIA_LOG, nature);
        saveData(Items.BIRCH_LOG, nature);
        saveData(Items.DARK_OAK_LOG, nature);
        saveData(Items.JUNGLE_LOG, nature);
        saveData(Items.OAK_LOG, nature);
        saveData(Items.SPRUCE_LOG, nature);
        saveData(Items.CRIMSON_STEM, nature);
        saveData(Items.WARPED_STEM, nature);
        saveData(Items.STRIPPED_ACACIA_LOG, nature);
        saveData(Items.STRIPPED_BIRCH_LOG, nature);
        saveData(Items.STRIPPED_DARK_OAK_LOG, nature);
        saveData(Items.STRIPPED_JUNGLE_LOG, nature);
        saveData(Items.STRIPPED_OAK_LOG, nature);
        saveData(Items.STRIPPED_SPRUCE_LOG, nature);
        saveData(Items.STRIPPED_CRIMSON_STEM, nature);
        saveData(Items.STRIPPED_WARPED_STEM, nature);
        saveData(Items.ACACIA_SAPLING, nature);
        saveData(Items.BIRCH_SAPLING, nature);
        saveData(Items.DARK_OAK_SAPLING, nature);
        saveData(Items.JUNGLE_SAPLING, nature);
        saveData(Items.OAK_SAPLING, nature);
        saveData(Items.SPRUCE_SAPLING, nature);
    }

    private void saveLeavesAndSmallPlants(CompoundInstance nature) {
        saveData(Items.ACACIA_LEAVES, nature);
        saveData(Items.BIRCH_LEAVES, nature);
        saveData(Items.DARK_OAK_LEAVES, nature);
        saveData(Items.JUNGLE_LEAVES, nature);
        saveData(Items.OAK_LEAVES, nature);
        saveData(Items.SPRUCE_LEAVES, nature);

        saveData(Items.DEAD_BUSH, nature);
        saveData(Items.FERN, nature);
        saveData(Items.GRASS, nature);
        saveData(Items.CRIMSON_ROOTS, nature);
        saveData(Items.NETHER_SPROUTS, nature);
        saveData(Items.WARPED_ROOTS, nature);

        saveData(Items.KELP, nature);
        saveData(Items.LILY_PAD, nature);

        saveData(Items.SWEET_BERRIES, nature);
        saveData(Items.TWISTING_VINES, nature);
        saveData(Items.VINE, nature);
        saveData(Items.WEEPING_VINES, nature);
        saveData(Items.MUSHROOM_STEM, nature);
    }

    private void saveData(Item item, CompoundInstance... instances) {
        saveData(newLinkedHashSet(item, new ItemStack(item)), instances);
    }

    private LinkedHashSet<Object> newLinkedHashSet(final Object... internal) {
        return new LinkedHashSet<>(Arrays.asList(internal));
    }

    private void saveData(LinkedHashSet<Object> items, CompoundInstance... instances) {
        save(specFor(items).withCompounds(instances));
    }

    private static CompoundInstance earth(double d) { return new CompoundInstance(Registry.EARTH.get(), d); }
    private static CompoundInstance air(double d) { return new CompoundInstance(Registry.AIR.get(), d); }
    private static CompoundInstance fire(double d) { return new CompoundInstance(Registry.FIRE.get(), d); }
    private static CompoundInstance water(double d) { return new CompoundInstance(Registry.WATER.get(), d); }
    private static CompoundInstance ice(double d) { return new CompoundInstance(Registry.ICE.get(), d); }
    private static CompoundInstance lightning(double d) { return new CompoundInstance(Registry.LIGHTNING.get(), d); }
    private static CompoundInstance crystal(double d) { return new CompoundInstance(Registry.CRYSTAL.get(), d); }
    private static CompoundInstance metal(double d) { return new CompoundInstance(Registry.METAL.get(), d); }
    private static CompoundInstance dark(double d) { return new CompoundInstance(Registry.DARK.get(), d); }
    private static CompoundInstance light(double d) { return new CompoundInstance(Registry.LIGHT.get(), d); }
    private static CompoundInstance nature(double d) { return new CompoundInstance(Registry.NATURE.get(), d); }
    private static CompoundInstance ender(double d) { return new CompoundInstance(Registry.ENDER.get(), d); }
    private static CompoundInstance nether(double d) { return new CompoundInstance(Registry.NETHER.get(), d); }

    private static CompoundInstance forbidden(double d) { return new CompoundInstance(Registry.FORBIDDEN.get(), d); }
}
