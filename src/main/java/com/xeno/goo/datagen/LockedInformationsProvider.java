package com.xeno.goo.datagen;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.locked.LockedInformationProvider;
import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.item.Items;

public class LockedInformationsProvider extends LockedInformationProvider
{
    LockedInformationsProvider(final DataGenerator dataGenerator)
    {
        super(GooMod.MOD_ID, dataGenerator);
    }

    @Override
    public void calculateDataToSave()
    {
        saveData(Items.ACACIA_LEAVES, floral(5), vital(5));
        saveData(Items.BIRCH_LEAVES, floral(5), vital(5));
        saveData(Items.DARK_OAK_LEAVES, floral(5), vital(5));
        saveData(Items.JUNGLE_LEAVES, floral(5), vital(5));
        saveData(Items.OAK_LEAVES, floral(5), vital(5));
        saveData(Items.SPRUCE_LEAVES, floral(5), vital(5));
        saveData(Items.ACACIA_LOG, floral(960), vital(5));
        saveData(Items.BIRCH_LOG, floral(960), vital(5));
        saveData(Items.DARK_OAK_LOG, floral(960), vital(5));
        saveData(Items.JUNGLE_LOG, floral(960), vital(5));
        saveData(Items.OAK_LOG, floral(960), vital(5));
        saveData(Items.SPRUCE_LOG, floral(960), vital(5));
        saveData(Items.CRIMSON_STEM, fungal(960), chromatic(60), vital (5));
        saveData(Items.WARPED_STEM, fungal(960), chromatic(60), vital (5));
        saveData(Items.STRIPPED_ACACIA_LOG, floral(960));
        saveData(Items.STRIPPED_BIRCH_LOG, floral(960));
        saveData(Items.STRIPPED_DARK_OAK_LOG, floral(960));
        saveData(Items.STRIPPED_JUNGLE_LOG, floral(960));
        saveData(Items.STRIPPED_OAK_LOG, floral(960));
        saveData(Items.STRIPPED_SPRUCE_LOG, floral(960));
        saveData(Items.STRIPPED_CRIMSON_STEM, fungal(960), chromatic(60));
        saveData(Items.STRIPPED_WARPED_STEM, fungal(960), chromatic(60));
        saveData(Items.ACACIA_SAPLING, floral(960), vital(1));
        saveData(Items.BIRCH_SAPLING, floral(960), vital(1));
        saveData(Items.DARK_OAK_SAPLING, floral(960), vital(1));
        saveData(Items.JUNGLE_SAPLING, floral(960), vital(1));
        saveData(Items.OAK_SAPLING, floral(960), vital(1));
        saveData(Items.SPRUCE_SAPLING, floral(960), vital(1));
        saveData(Items.ALLIUM, floral(60), chromatic(240), vital(1));
        saveData(Items.AZURE_BLUET, floral(60), chromatic(240), vital(1));
        saveData(Items.BLUE_ORCHID, floral(60), chromatic(240), vital(1));
        saveData(Items.COCOA_BEANS, floral(60), chromatic(240), vital(1));
        saveData(Items.CORNFLOWER, floral(60), chromatic(240), vital(1));
        saveData(Items.DANDELION, floral(60), chromatic(240), vital(1));
        saveData(Items.LILY_OF_THE_VALLEY, floral(60), chromatic(240), vital(1));
        saveData(Items.ORANGE_TULIP, floral(60), chromatic(240), vital(1));
        saveData(Items.OXEYE_DAISY, floral(60), chromatic(240), vital(1));
        saveData(Items.PINK_TULIP, floral(60), chromatic(240), vital(1));
        saveData(Items.POPPY, floral(60), chromatic(240), vital(1));
        saveData(Items.RED_TULIP, floral(60), chromatic(240), vital(1));
        saveData(Items.WHITE_TULIP, floral(60), chromatic(240), vital(1));
        saveData(Items.LILAC, floral(120), chromatic(480), vital(2));
        saveData(Items.PEONY, floral(120), chromatic(480), vital(2));
        saveData(Items.ROSE_BUSH, floral(120), chromatic(480), vital(2));
        saveData(Items.SUNFLOWER, floral(120), chromatic(480), vital(2));
        saveData(Items.BLACK_DYE, chromatic(240));
        saveData(Items.BLUE_DYE, chromatic(240));
        saveData(Items.BROWN_DYE, chromatic(240));
        saveData(Items.CYAN_DYE, chromatic(240));
        saveData(Items.GRAY_DYE, chromatic(240));
        saveData(Items.GREEN_DYE, chromatic(240));
        saveData(Items.LIGHT_BLUE_DYE, chromatic(240));
        saveData(Items.LIGHT_GRAY_DYE, chromatic(240));
        saveData(Items.LIME_DYE, chromatic(240));
        saveData(Items.MAGENTA_DYE, chromatic(240));
        saveData(Items.ORANGE_DYE, chromatic(240));
        saveData(Items.PINK_DYE, chromatic(240));
        saveData(Items.PURPLE_DYE, chromatic(240));
        saveData(Items.RED_DYE, chromatic(240));
        saveData(Items.WHITE_DYE, chromatic(240));
        saveData(Items.YELLOW_DYE, chromatic(240));
        saveData(Items.BRAIN_CORAL, faunal(60), vital(1));
        saveData(Items.BUBBLE_CORAL, faunal(60), vital(1));
        saveData(Items.DEAD_BRAIN_CORAL, faunal(60));
        saveData(Items.DEAD_BUBBLE_CORAL, faunal(60));
        saveData(Items.DEAD_FIRE_CORAL, faunal(60));
        saveData(Items.DEAD_HORN_CORAL, faunal(60));
        saveData(Items.DEAD_TUBE_CORAL, faunal(60));
        saveData(Items.FIRE_CORAL, faunal(60), vital(1));
        saveData(Items.HORN_CORAL, faunal(60), vital(1));
        saveData(Items.TUBE_CORAL, faunal(60), vital(1));
        saveData(Items.BRAIN_CORAL_BLOCK, faunal(480), vital(4));
        saveData(Items.BUBBLE_CORAL_BLOCK, faunal(480), vital(4));
        saveData(Items.DEAD_BRAIN_CORAL_BLOCK, faunal(480));
        saveData(Items.DEAD_BUBBLE_CORAL_BLOCK, faunal(480));
        saveData(Items.DEAD_FIRE_CORAL_BLOCK, faunal(480));
        saveData(Items.DEAD_HORN_CORAL_BLOCK, faunal(480));
        saveData(Items.DEAD_TUBE_CORAL_BLOCK, faunal(480));
        saveData(Items.FIRE_CORAL_BLOCK, faunal(480), vital(4));
        saveData(Items.HORN_CORAL_BLOCK, faunal(480), vital(4));
        saveData(Items.TUBE_CORAL_BLOCK, faunal(480), vital(4));
        saveData(Items.BRAIN_CORAL_FAN, faunal(60), vital(1));
        saveData(Items.BUBBLE_CORAL_FAN, faunal(60), vital(1));
        saveData(Items.DEAD_BRAIN_CORAL_FAN, faunal(60));
        saveData(Items.DEAD_BUBBLE_CORAL_FAN, faunal(60));
        saveData(Items.DEAD_FIRE_CORAL_FAN, faunal(60));
        saveData(Items.DEAD_HORN_CORAL_FAN, faunal(60));
        saveData(Items.DEAD_TUBE_CORAL_FAN, faunal(60));
        saveData(Items.FIRE_CORAL_FAN, faunal(60), vital(1));
        saveData(Items.HORN_CORAL_FAN, faunal(60), vital(1));
        saveData(Items.TUBE_CORAL_FAN, faunal(60), vital(1));
        saveData(Items.DEAD_BUSH, floral(120));
        saveData(Items.FERN, floral(120), vital(1));
        saveData(Items.GRASS, floral(120), vital(1));
        saveData(Items.CRIMSON_ROOTS, fungal(120), vital(1));
        saveData(Items.NETHER_SPROUTS, fungal(120), vital(1));
        saveData(Items.WARPED_ROOTS, fungal(120), vital(1));
        saveData(Items.LARGE_FERN, floral(240), vital(2));
        saveData(Items.TALL_GRASS, floral(240), vital(2));
        saveData(Items.BLACKSTONE, earthen(1080));
        saveData(Items.COBBLESTONE, earthen(1080));
        saveData(Items.DIRT, earthen(1080));
        saveData(Items.FLINT, earthen(1080));
        saveData(Items.GRAVEL, earthen(1080));
        saveData(Items.PODZOL, earthen(1080));
        saveData(Items.RED_SAND, earthen(1080));
        saveData(Items.SAND, earthen(1080));
        saveData(Items.APPLE, floral(60), vital(1));
        saveData(Items.BAMBOO, floral(60));
        saveData(Items.BASALT, earthen(960), obsidian(6));
        saveData(Items.BEEF, faunal(60), vital(1));
        saveData(Items.BEETROOT, floral(60), vital(1), chromatic(240));
        saveData(Items.BEETROOT_SEEDS, floral(60));
        saveData(Items.BLAZE_ROD, molten(120), energetic(60), vital(1));
        saveData(Items.BONE, faunal(180), chromatic(360), vital(3), decay(60));
        saveData(Items.BROWN_MUSHROOM, fungal(60), vital(1));
        saveData(Items.BROWN_MUSHROOM_BLOCK, fungal(30));
        saveData(Items.CACTUS, floral(960), chromatic(240), vital (1));
        saveData(Items.CARROT, floral(60), vital(1));
        saveData(Items.CARVED_PUMPKIN, floral(60), weird(60));
        saveData(Items.CHARCOAL, molten(96), floral(96));
        saveData(Items.CHICKEN, faunal(60), vital(1));
        saveData(Items.CHORUS_FLOWER, floral(240), weird(240), vital(1));
        saveData(Items.CHORUS_FRUIT, floral(60), weird(60), vital(1));
        saveData(Items.CLAY_BALL, earthen(240));
        saveData(Items.COAL, molten(96), earthen(72));
        saveData(Items.COBWEB, faunal(120));
        saveData(Items.COD, faunal(60), vital(1));
        saveData(Items.CRIMSON_NYLIUM, earthen(960d), fungal(60d), chromatic(60));
        saveData(Items.CRIMSON_FUNGUS, fungal(15));
        saveData(Items.CRYING_OBSIDIAN, weird(60), obsidian(960));
        saveData(Items.DIAMOND, crystal(120));
        saveData(Items.EGG, faunal(60), vital(15));
        saveData(Items.EMERALD, regal(60), crystal(60));
        saveData(Items.END_STONE, earthen(720), weird(240), vital(1));
        saveData(Items.ENDER_PEARL, weird(120));
        saveData(Items.FEATHER, faunal(60));
        saveData(Items.GHAST_TEAR, weird(240), crystal(24));
        saveData(Items.GILDED_BLACKSTONE, metal(240), regal(240), weird(60), earthen(240), obsidian(240));
        saveData(Items.GLOWSTONE_DUST, weird(60), energetic(60));
        saveData(Items.GOLD_INGOT, metal(36), regal(72));
        saveData(Items.GRASS_BLOCK, earthen(960), floral(60));
        saveData(Items.GUNPOWDER, molten(60), energetic(120));
        saveData(Items.HONEYCOMB, honey(120), regal(24));
        saveData(Items.HONEY_BLOCK, honey(960), regal(60), crystal(4));
        saveData(Items.ICE, snow(60), aquatic(60));
        saveData(Items.INK_SAC, faunal(60), chromatic(240));
        saveData(Items.IRON_INGOT, metal(72));
        saveData(Items.KELP, floral(60), vital(1));
        saveData(Items.LAPIS_LAZULI, weird(48), chromatic(240), crystal(4));
        saveData(Items.LILY_PAD, floral(15), vital(1));
        saveData(Items.MELON_SLICE, floral(60), vital(1));
        saveData(Items.MUSHROOM_STEM, fungal(30));
        saveData(Items.MUTTON, faunal(60), vital(1));
        saveData(Items.MYCELIUM, earthen(960d), fungal(60));
        saveData(Items.NETHER_WART, weird(60), fungal(60));
        saveData(Items.NETHERITE_SCRAP, metal(960), obsidian(120));
        saveData(Items.NETHERRACK, earthen(960), molten(60));
        saveData(Items.OBSIDIAN, obsidian(120), molten(60), earthen(840));
        saveData(Items.PHANTOM_MEMBRANE, decay(60), vital(1), weird(60));
        saveData(Items.POISONOUS_POTATO, floral(60), weird(60));
        saveData(Items.PORKCHOP, faunal(60), vital(1));
        saveData(Items.POTATO, floral(60), vital(1));
        saveData(Items.PRISMARINE_CRYSTALS, aquatic(36), crystal(2), weird(36), energetic(72));
        saveData(Items.PRISMARINE_SHARD, aquatic(36), crystal(1), weird(72));
        saveData(Items.PUFFERFISH, faunal(60), weird(60));
        saveData(Items.PUMPKIN, floral(960));
        saveData(Items.QUARTZ, earthen(60), crystal (1));
        saveData(Items.RABBIT, faunal(60), vital(1));
        saveData(Items.RABBIT_FOOT, faunal(60), weird(180));
        saveData(Items.RABBIT_HIDE, faunal(60));
        saveData(Items.RED_MUSHROOM, fungal(60));
        saveData(Items.RED_MUSHROOM_BLOCK, fungal(30));
        saveData(Items.REDSTONE, logic(120));
        saveData(Items.ROTTEN_FLESH, faunal(60), decay(60));
        saveData(Items.SALMON, faunal(60), vital(1));
        saveData(Items.SCUTE, faunal(60), weird(60), vital(1));
        saveData(Items.SEA_PICKLE, faunal(60), aquatic(60), chromatic(240));
        saveData(Items.SEAGRASS, floral(60), vital(1));
        saveData(Items.SHULKER_SHELL, faunal(60), weird(180));
        saveData(Items.SHROOMLIGHT, weird(120), fungal(360));
        saveData(Items.SLIME_BALL, slime(240));
        saveData(Items.SNOWBALL, snow(15), aquatic(15));
        saveData(Items.SOUL_SAND, earthen(720), vital(15), decay(60));
        saveData(Items.SOUL_SOIL, earthen(720), vital(15), decay(60));
        saveData(Items.SPIDER_EYE, faunal(60), weird(60));
        saveData(Items.STRING, faunal(60));
        saveData(Items.SUGAR_CANE, floral(60));
        saveData(Items.SWEET_BERRIES, floral(60), vital(1));
        saveData(Items.TROPICAL_FISH, faunal(60), vital(15));
        saveData(Items.TURTLE_EGG, faunal(60), vital(180), weird(60));
        saveData(Items.TWISTING_VINES, fungal(60));
        saveData(Items.VINE, floral(15));
        saveData(Items.WARPED_NYLIUM, earthen(720), molten(60), fungal(240));
        saveData(Items.WARPED_FUNGUS, fungal(120));
        saveData(Items.WEEPING_VINES, fungal(60));
        saveData(Items.WET_SPONGE, aquatic(720), weird(60), faunal(120), vital(15));
        saveData(Items.WHEAT, floral(60));
        saveData(Items.WHEAT_SEEDS, floral(60));

        // containers
        saveData(Items.LAVA_BUCKET, metal(216), molten(1080));
        saveData(Items.MILK_BUCKET, metal(216), faunal(120));
        saveData(Items.WATER_BUCKET, metal(216), aquatic(960));
    }

    private static CompoundInstance aquatic(double d) { return new CompoundInstance(Registry.AQUATIC.get(), d); }
    private static CompoundInstance chromatic(double d) { return new CompoundInstance(Registry.CHROMATIC.get(), d); }
    private static CompoundInstance crystal(double d) { return new CompoundInstance(Registry.CRYSTAL.get(), d); }
    private static CompoundInstance decay(double d) { return new CompoundInstance(Registry.DECAY.get(), d); }
    private static CompoundInstance earthen(double d) { return new CompoundInstance(Registry.EARTHEN.get(), d); }
    private static CompoundInstance energetic(double d) { return new CompoundInstance(Registry.ENERGETIC.get(), d); }
    private static CompoundInstance faunal(double d) { return new CompoundInstance(Registry.FAUNAL.get(), d); }
    private static CompoundInstance floral(double d) { return new CompoundInstance(Registry.FLORAL.get(), d); }
    private static CompoundInstance fungal(double d) { return new CompoundInstance(Registry.FUNGAL.get(), d); }
    private static CompoundInstance honey(double d) { return new CompoundInstance(Registry.HONEY.get(), d); }
    private static CompoundInstance logic(double d) { return new CompoundInstance(Registry.LOGIC.get(), d); }
    private static CompoundInstance metal(double d) { return new CompoundInstance(Registry.METAL.get(), d); }
    private static CompoundInstance molten(double d) { return new CompoundInstance(Registry.MOLTEN.get(), d); }
    private static CompoundInstance obsidian(double d) { return new CompoundInstance(Registry.OBSIDIAN.get(), d); }
    private static CompoundInstance regal(double d) { return new CompoundInstance(Registry.REGAL.get(), d); }
    private static CompoundInstance slime(double d) { return new CompoundInstance(Registry.SLIME.get(), d); }
    private static CompoundInstance snow(double d) { return new CompoundInstance(Registry.SNOW.get(), d); }
    private static CompoundInstance vital(double d) { return new CompoundInstance(Registry.VITAL.get(), d); }
    private static CompoundInstance weird(double d) { return new CompoundInstance(Registry.WEIRD.get(), d); }
}
