package com.xeno.goo.datagen;

import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.datagen.ForcedInformationProvider;
import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.xeno.goo.GooMod;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class AequivaleoInformationsProvider extends ForcedInformationProvider
{
    AequivaleoInformationsProvider(final DataGenerator dataGenerator)
    {
        super(GooMod.MOD_ID, dataGenerator);
    }

    @Override
    public void calculateDataToSave()
    {
        saveData(newLinkedHashSet(Items.ACACIA_LEAVES, new ItemStack(Items.ACACIA_LEAVES)), floral(5), vital(1));
        saveData(newLinkedHashSet(Items.BIRCH_LEAVES, new ItemStack(Items.BIRCH_LEAVES)), floral(5), vital(1));
        saveData(newLinkedHashSet(Items.DARK_OAK_LEAVES, new ItemStack(Items.DARK_OAK_LEAVES)), floral(5), vital(1));
        saveData(newLinkedHashSet(Items.JUNGLE_LEAVES, new ItemStack(Items.JUNGLE_LEAVES)), floral(5), vital(1));
        saveData(newLinkedHashSet(Items.OAK_LEAVES, new ItemStack(Items.OAK_LEAVES)), floral(5), vital(1));
        saveData(newLinkedHashSet(Items.SPRUCE_LEAVES, new ItemStack(Items.SPRUCE_LEAVES)), floral(5), vital(1));
        saveData(newLinkedHashSet(Items.ACACIA_LOG, new ItemStack(Items.ACACIA_LOG)), floral(128), vital(16));
        saveData(newLinkedHashSet(Items.BIRCH_LOG, new ItemStack(Items.BIRCH_LOG)), floral(128), vital(16));
        saveData(newLinkedHashSet(Items.DARK_OAK_LOG, new ItemStack(Items.DARK_OAK_LOG)), floral(128), vital(16));
        saveData(newLinkedHashSet(Items.JUNGLE_LOG, new ItemStack(Items.JUNGLE_LOG)), floral(128), vital(16));
        saveData(newLinkedHashSet(Items.OAK_LOG, new ItemStack(Items.OAK_LOG)), floral(128), vital(16));
        saveData(newLinkedHashSet(Items.SPRUCE_LOG, new ItemStack(Items.SPRUCE_LOG)), floral(128), vital(16));
        saveData(newLinkedHashSet(Items.CRIMSON_STEM, new ItemStack(Items.CRIMSON_STEM)), fungal(16), chromatic(128), vital (16));
        saveData(newLinkedHashSet(Items.WARPED_STEM, new ItemStack(Items.WARPED_STEM)), fungal(16), chromatic(128), vital (16));
        saveData(newLinkedHashSet(Items.STRIPPED_ACACIA_LOG, new ItemStack(Items.STRIPPED_ACACIA_LOG)), floral(128));
        saveData(newLinkedHashSet(Items.STRIPPED_BIRCH_LOG, new ItemStack(Items.STRIPPED_BIRCH_LOG)), floral(128));
        saveData(newLinkedHashSet(Items.STRIPPED_DARK_OAK_LOG, new ItemStack(Items.STRIPPED_DARK_OAK_LOG)), floral(128));
        saveData(newLinkedHashSet(Items.STRIPPED_JUNGLE_LOG, new ItemStack(Items.STRIPPED_JUNGLE_LOG)), floral(128));
        saveData(newLinkedHashSet(Items.STRIPPED_OAK_LOG, new ItemStack(Items.STRIPPED_OAK_LOG)), floral(128));
        saveData(newLinkedHashSet(Items.STRIPPED_SPRUCE_LOG, new ItemStack(Items.STRIPPED_SPRUCE_LOG)), floral(128));
        saveData(newLinkedHashSet(Items.STRIPPED_CRIMSON_STEM, new ItemStack(Items.STRIPPED_CRIMSON_STEM)), fungal(16), chromatic(128));
        saveData(newLinkedHashSet(Items.STRIPPED_WARPED_STEM, new ItemStack(Items.STRIPPED_WARPED_STEM)), fungal(16), chromatic(128));
        saveData(newLinkedHashSet(Items.ACACIA_SAPLING, new ItemStack(Items.ACACIA_SAPLING)), floral(394), vital(16));
        saveData(newLinkedHashSet(Items.BIRCH_SAPLING, new ItemStack(Items.BIRCH_SAPLING)), floral(394), vital(16));
        saveData(newLinkedHashSet(Items.DARK_OAK_SAPLING, new ItemStack(Items.DARK_OAK_SAPLING)), floral(394), vital(16));
        saveData(newLinkedHashSet(Items.JUNGLE_SAPLING, new ItemStack(Items.JUNGLE_SAPLING)), floral(394), vital(16));
        saveData(newLinkedHashSet(Items.OAK_SAPLING, new ItemStack(Items.OAK_SAPLING)), floral(394), vital(16));
        saveData(newLinkedHashSet(Items.SPRUCE_SAPLING, new ItemStack(Items.SPRUCE_SAPLING)), floral(394), vital(16));
        saveData(newLinkedHashSet(Items.ALLIUM, new ItemStack(Items.ALLIUM)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.AZURE_BLUET, new ItemStack(Items.AZURE_BLUET)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.BLUE_ORCHID, new ItemStack(Items.BLUE_ORCHID)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.COCOA_BEANS, new ItemStack(Items.COCOA_BEANS)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.CORNFLOWER, new ItemStack(Items.CORNFLOWER)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.DANDELION, new ItemStack(Items.DANDELION)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.LILY_OF_THE_VALLEY, new ItemStack(Items.LILY_OF_THE_VALLEY)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.ORANGE_TULIP, new ItemStack(Items.ORANGE_TULIP)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.OXEYE_DAISY, new ItemStack(Items.OXEYE_DAISY)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.PINK_TULIP, new ItemStack(Items.PINK_TULIP)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.POPPY, new ItemStack(Items.POPPY)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.RED_TULIP, new ItemStack(Items.RED_TULIP)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.WHITE_TULIP, new ItemStack(Items.WHITE_TULIP)), floral(5), chromatic(240), vital(1));
        saveData(newLinkedHashSet(Items.LILAC, new ItemStack(Items.LILAC)), floral(10), chromatic(480), vital(2));
        saveData(newLinkedHashSet(Items.PEONY, new ItemStack(Items.PEONY)), floral(10), chromatic(480), vital(2));
        saveData(newLinkedHashSet(Items.ROSE_BUSH, new ItemStack(Items.ROSE_BUSH)), floral(10), chromatic(480), vital(2));
        saveData(newLinkedHashSet(Items.SUNFLOWER, new ItemStack(Items.SUNFLOWER)), floral(10), chromatic(480), vital(2));
        saveData(newLinkedHashSet(Items.BLACK_DYE, new ItemStack(Items.BLACK_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.BLUE_DYE, new ItemStack(Items.BLUE_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.BROWN_DYE, new ItemStack(Items.BROWN_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.CYAN_DYE, new ItemStack(Items.CYAN_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.GRAY_DYE, new ItemStack(Items.GRAY_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.GREEN_DYE, new ItemStack(Items.GREEN_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.LIGHT_BLUE_DYE, new ItemStack(Items.LIGHT_BLUE_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.LIGHT_GRAY_DYE, new ItemStack(Items.LIGHT_GRAY_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.LIME_DYE, new ItemStack(Items.LIME_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.MAGENTA_DYE, new ItemStack(Items.MAGENTA_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.ORANGE_DYE, new ItemStack(Items.ORANGE_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.PINK_DYE, new ItemStack(Items.PINK_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.PURPLE_DYE, new ItemStack(Items.PURPLE_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.RED_DYE, new ItemStack(Items.RED_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.WHITE_DYE, new ItemStack(Items.WHITE_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.YELLOW_DYE, new ItemStack(Items.YELLOW_DYE)), chromatic(240));
        saveData(newLinkedHashSet(Items.BRAIN_CORAL, new ItemStack(Items.BRAIN_CORAL)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.BUBBLE_CORAL, new ItemStack(Items.BUBBLE_CORAL)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.DEAD_BRAIN_CORAL, new ItemStack(Items.DEAD_BRAIN_CORAL)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_BUBBLE_CORAL, new ItemStack(Items.DEAD_BUBBLE_CORAL)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_FIRE_CORAL, new ItemStack(Items.DEAD_FIRE_CORAL)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_HORN_CORAL, new ItemStack(Items.DEAD_HORN_CORAL)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_TUBE_CORAL, new ItemStack(Items.DEAD_TUBE_CORAL)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.FIRE_CORAL, new ItemStack(Items.FIRE_CORAL)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.HORN_CORAL, new ItemStack(Items.HORN_CORAL)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.TUBE_CORAL, new ItemStack(Items.TUBE_CORAL)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.BRAIN_CORAL_BLOCK, new ItemStack(Items.BRAIN_CORAL_BLOCK)), faunal(20), vital(4));
        saveData(newLinkedHashSet(Items.BUBBLE_CORAL_BLOCK, new ItemStack(Items.BUBBLE_CORAL_BLOCK)), faunal(20), vital(4));
        saveData(newLinkedHashSet(Items.DEAD_BRAIN_CORAL_BLOCK, new ItemStack(Items.DEAD_BRAIN_CORAL_BLOCK)), faunal(20), decay(20));
        saveData(newLinkedHashSet(Items.DEAD_BUBBLE_CORAL_BLOCK, new ItemStack(Items.DEAD_BUBBLE_CORAL_BLOCK)), faunal(20), decay(20));
        saveData(newLinkedHashSet(Items.DEAD_FIRE_CORAL_BLOCK, new ItemStack(Items.DEAD_FIRE_CORAL_BLOCK)), faunal(20), decay(20));
        saveData(newLinkedHashSet(Items.DEAD_HORN_CORAL_BLOCK, new ItemStack(Items.DEAD_HORN_CORAL_BLOCK)), faunal(20), decay(20));
        saveData(newLinkedHashSet(Items.DEAD_TUBE_CORAL_BLOCK, new ItemStack(Items.DEAD_TUBE_CORAL_BLOCK)), faunal(20), decay(20));
        saveData(newLinkedHashSet(Items.FIRE_CORAL_BLOCK, new ItemStack(Items.FIRE_CORAL_BLOCK)), faunal(20), vital(4));
        saveData(newLinkedHashSet(Items.HORN_CORAL_BLOCK, new ItemStack(Items.HORN_CORAL_BLOCK)), faunal(20), vital(4));
        saveData(newLinkedHashSet(Items.TUBE_CORAL_BLOCK, new ItemStack(Items.TUBE_CORAL_BLOCK)), faunal(20), vital(4));
        saveData(newLinkedHashSet(Items.BRAIN_CORAL_FAN, new ItemStack(Items.BRAIN_CORAL_FAN)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.BUBBLE_CORAL_FAN, new ItemStack(Items.BUBBLE_CORAL_FAN)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.DEAD_BRAIN_CORAL_FAN, new ItemStack(Items.DEAD_BRAIN_CORAL_FAN)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_BUBBLE_CORAL_FAN, new ItemStack(Items.DEAD_BUBBLE_CORAL_FAN)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_FIRE_CORAL_FAN, new ItemStack(Items.DEAD_FIRE_CORAL_FAN)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_HORN_CORAL_FAN, new ItemStack(Items.DEAD_HORN_CORAL_FAN)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.DEAD_TUBE_CORAL_FAN, new ItemStack(Items.DEAD_TUBE_CORAL_FAN)), faunal(5), decay(5));
        saveData(newLinkedHashSet(Items.FIRE_CORAL_FAN, new ItemStack(Items.FIRE_CORAL_FAN)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.HORN_CORAL_FAN, new ItemStack(Items.HORN_CORAL_FAN)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.TUBE_CORAL_FAN, new ItemStack(Items.TUBE_CORAL_FAN)), faunal(5), vital(1));
        saveData(newLinkedHashSet(Items.DEAD_BUSH, new ItemStack(Items.DEAD_BUSH)), floral(15), decay(5));
        saveData(newLinkedHashSet(Items.FERN, new ItemStack(Items.FERN)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.GRASS, new ItemStack(Items.GRASS)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.CRIMSON_ROOTS, new ItemStack(Items.CRIMSON_ROOTS)), fungal(15), vital(1));
        saveData(newLinkedHashSet(Items.NETHER_SPROUTS, new ItemStack(Items.NETHER_SPROUTS)), fungal(15), vital(1));
        saveData(newLinkedHashSet(Items.WARPED_ROOTS, new ItemStack(Items.WARPED_ROOTS)), fungal(15), vital(1));
        saveData(newLinkedHashSet(Items.LARGE_FERN, new ItemStack(Items.LARGE_FERN)), floral(30), vital(2));
        saveData(newLinkedHashSet(Items.TALL_GRASS, new ItemStack(Items.TALL_GRASS)), floral(30), vital(2));
        saveData(newLinkedHashSet(Items.BLACKSTONE, new ItemStack(Items.BLACKSTONE)), earthen(180));
        saveData(newLinkedHashSet(Items.COBBLESTONE, new ItemStack(Items.COBBLESTONE)), earthen(180));
        saveData(newLinkedHashSet(Items.DIRT, new ItemStack(Items.DIRT)), earthen(180));
        saveData(newLinkedHashSet(Items.FLINT, new ItemStack(Items.FLINT)), earthen(180));
        saveData(newLinkedHashSet(Items.GRAVEL, new ItemStack(Items.GRAVEL)), earthen(180));
        saveData(newLinkedHashSet(Items.PODZOL, new ItemStack(Items.PODZOL)), earthen(180), fungal(15));
        saveData(newLinkedHashSet(Items.RED_SAND, new ItemStack(Items.RED_SAND)), earthen(180));
        saveData(newLinkedHashSet(Items.SAND, new ItemStack(Items.SAND)), earthen(180));
        saveData(newLinkedHashSet(Items.APPLE, new ItemStack(Items.APPLE)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.BAMBOO, new ItemStack(Items.BAMBOO)), floral(8));
        saveData(newLinkedHashSet(Items.BASALT, new ItemStack(Items.BASALT)), earthen(180), obsidian(6));
        saveData(newLinkedHashSet(Items.BEEF, new ItemStack(Items.BEEF)), faunal(15), vital(1));
        saveData(newLinkedHashSet(Items.BEETROOT, new ItemStack(Items.BEETROOT)), floral(15), vital(1), chromatic(240));
        saveData(newLinkedHashSet(Items.BEETROOT_SEEDS, new ItemStack(Items.BEETROOT_SEEDS)), floral(15));
        saveData(newLinkedHashSet(Items.BLAZE_ROD, new ItemStack(Items.BLAZE_ROD)), molten(120));
        saveData(newLinkedHashSet(Items.BONE, new ItemStack(Items.BONE)), faunal(30), chromatic(720), vital(3), decay(60));
        saveData(newLinkedHashSet(Items.BROWN_MUSHROOM, new ItemStack(Items.BROWN_MUSHROOM)), fungal(15), vital(1));
        saveData(newLinkedHashSet(Items.BROWN_MUSHROOM_BLOCK, new ItemStack(Items.BROWN_MUSHROOM_BLOCK)), fungal(5));
        saveData(newLinkedHashSet(Items.CACTUS, new ItemStack(Items.CACTUS)), floral(120), chromatic(240), vital (1));
        saveData(newLinkedHashSet(Items.CARROT, new ItemStack(Items.CARROT)), floral(60), vital(1));
        saveData(newLinkedHashSet(Items.CARVED_PUMPKIN, new ItemStack(Items.CARVED_PUMPKIN)), floral(5), weird(1));
        saveData(newLinkedHashSet(Items.CHARCOAL, new ItemStack(Items.CHARCOAL)), molten(80), floral(32));
        saveData(newLinkedHashSet(Items.CHICKEN, new ItemStack(Items.CHICKEN)), faunal(15), vital(1));
        saveData(newLinkedHashSet(Items.CHORUS_FLOWER, new ItemStack(Items.CHORUS_FLOWER)), floral(30), weird(6), vital(1));
        saveData(newLinkedHashSet(Items.CHORUS_FRUIT, new ItemStack(Items.CHORUS_FRUIT)), floral(15), weird(4), vital(1));
        saveData(newLinkedHashSet(Items.CLAY_BALL, new ItemStack(Items.CLAY_BALL)), earthen(60));
        saveData(newLinkedHashSet(Items.COAL, new ItemStack(Items.COAL)), molten(80), earthen(16));
        saveData(newLinkedHashSet(Items.COBWEB, new ItemStack(Items.COBWEB)), faunal(15));
        saveData(newLinkedHashSet(Items.COD, new ItemStack(Items.COD)), faunal(15), vital(1));
        saveData(newLinkedHashSet(Items.CRIMSON_NYLIUM, new ItemStack(Items.CRIMSON_NYLIUM)), earthen(120), fungal(15), chromatic(60));
        saveData(newLinkedHashSet(Items.CRIMSON_FUNGUS, new ItemStack(Items.CRIMSON_FUNGUS)), fungal(15));
        saveData(newLinkedHashSet(Items.CRYING_OBSIDIAN, new ItemStack(Items.CRYING_OBSIDIAN)), weird(30), obsidian(960));
        saveData(newLinkedHashSet(Items.DIAMOND, new ItemStack(Items.DIAMOND)), crystal(240));
        saveData(newLinkedHashSet(Items.EGG, new ItemStack(Items.EGG)), faunal(15), vital(15));
        saveData(newLinkedHashSet(Items.EMERALD, new ItemStack(Items.EMERALD)), regal(60), crystal(60));
        saveData(newLinkedHashSet(Items.END_ROD, new ItemStack(Items.END_ROD)), radiant(20));
        saveData(newLinkedHashSet(Items.END_STONE, new ItemStack(Items.END_STONE)), earthen(120), weird(5), vital(1));
        saveData(newLinkedHashSet(Items.ENDER_PEARL, new ItemStack(Items.ENDER_PEARL)), weird(30));
        saveData(newLinkedHashSet(Items.FEATHER, new ItemStack(Items.FEATHER)), faunal(15));
        saveData(newLinkedHashSet(Items.GHAST_TEAR, new ItemStack(Items.GHAST_TEAR)), weird(30), crystal(24));
        saveData(newLinkedHashSet(Items.GILDED_BLACKSTONE, new ItemStack(Items.GILDED_BLACKSTONE)), metal(240), regal(240), weird(60), earthen(240), obsidian(240));
        saveData(newLinkedHashSet(Items.GLOWSTONE_DUST, new ItemStack(Items.GLOWSTONE_DUST)), radiant(10));
        saveData(newLinkedHashSet(Items.GOLD_INGOT, new ItemStack(Items.GOLD_INGOT)), metal(36), regal(72));
        saveData(newLinkedHashSet(Items.BELL, new ItemStack(Items.BELL)), earthen(360), regal(216), metal(180), floral(128));
        saveData(newLinkedHashSet(Items.GRASS_BLOCK, new ItemStack(Items.GRASS_BLOCK)), earthen(180), floral(15));
        saveData(newLinkedHashSet(Items.GUNPOWDER, new ItemStack(Items.GUNPOWDER)), energetic(120));
        saveData(newLinkedHashSet(Items.HONEYCOMB, new ItemStack(Items.HONEYCOMB)), honey(40), regal(32));
        saveData(newLinkedHashSet(Items.HONEY_BLOCK, new ItemStack(Items.HONEY_BLOCK)), honey(120), regal(96));
        saveData(newLinkedHashSet(Items.ICE, new ItemStack(Items.ICE)), snow(60));
        saveData(newLinkedHashSet(Items.INK_SAC, new ItemStack(Items.INK_SAC)), faunal(15), chromatic(240));
        saveData(newLinkedHashSet(Items.IRON_INGOT, new ItemStack(Items.IRON_INGOT)), metal(72));
        saveData(newLinkedHashSet(Items.KELP, new ItemStack(Items.KELP)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.LAPIS_LAZULI, new ItemStack(Items.LAPIS_LAZULI)), weird(4), chromatic(240), crystal(6));
        saveData(newLinkedHashSet(Items.LILY_PAD, new ItemStack(Items.LILY_PAD)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.MAGMA_BLOCK, new ItemStack(Items.MAGMA_BLOCK)), slime(10), molten(10), earthen(180), decay(30));
        saveData(newLinkedHashSet(Items.MELON_SLICE, new ItemStack(Items.MELON_SLICE)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.MUSHROOM_STEM, new ItemStack(Items.MUSHROOM_STEM)), fungal(5));
        saveData(newLinkedHashSet(Items.MUTTON, new ItemStack(Items.MUTTON)), faunal(15), vital(1));
        saveData(newLinkedHashSet(Items.MYCELIUM, new ItemStack(Items.MYCELIUM)), earthen(180), fungal(30));
        saveData(newLinkedHashSet(Items.NETHER_WART, new ItemStack(Items.NETHER_WART)), weird(1), fungal(5));
        saveData(newLinkedHashSet(Items.WARPED_WART_BLOCK, new ItemStack(Items.WARPED_WART_BLOCK)), weird(9), fungal(45));
        saveData(newLinkedHashSet(Items.NETHERITE_SCRAP, new ItemStack(Items.NETHERITE_SCRAP)), metal(960), obsidian(120));
        saveData(newLinkedHashSet(Items.NETHERRACK, new ItemStack(Items.NETHERRACK)), earthen(180), molten(5), decay(5));
        saveData(newLinkedHashSet(Items.OBSIDIAN, new ItemStack(Items.OBSIDIAN)), obsidian(120), molten(60), earthen(180));
        saveData(newLinkedHashSet(Items.PHANTOM_MEMBRANE, new ItemStack(Items.PHANTOM_MEMBRANE)), decay(60), vital(1), weird(60));
        saveData(newLinkedHashSet(Items.POISONOUS_POTATO, new ItemStack(Items.POISONOUS_POTATO)), floral(15), weird(3));
        saveData(newLinkedHashSet(Items.PORKCHOP, new ItemStack(Items.PORKCHOP)), faunal(15), vital(1));
        saveData(newLinkedHashSet(Items.POTATO, new ItemStack(Items.POTATO)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.PRISMARINE_CRYSTALS, new ItemStack(Items.PRISMARINE_CRYSTALS)), aquatic(120), crystal(3), weird(3), radiant(20));
        saveData(newLinkedHashSet(Items.PRISMARINE_SHARD, new ItemStack(Items.PRISMARINE_SHARD)), aquatic(120), crystal(2), weird(2), radiant(10));
        saveData(newLinkedHashSet(Items.PUFFERFISH, new ItemStack(Items.PUFFERFISH)), faunal(15), weird(6));
        saveData(newLinkedHashSet(Items.PUMPKIN, new ItemStack(Items.PUMPKIN)), floral(60));
        saveData(newLinkedHashSet(Items.QUARTZ, new ItemStack(Items.QUARTZ)), earthen(60), crystal (2));
        saveData(newLinkedHashSet(Items.RABBIT, new ItemStack(Items.RABBIT)), faunal(15), vital(1));
        saveData(newLinkedHashSet(Items.RABBIT_FOOT, new ItemStack(Items.RABBIT_FOOT)), faunal(30), weird(30));
        saveData(newLinkedHashSet(Items.RABBIT_HIDE, new ItemStack(Items.RABBIT_HIDE)), faunal(15));
        saveData(newLinkedHashSet(Items.RED_MUSHROOM, new ItemStack(Items.RED_MUSHROOM)), fungal(15), vital(1));
        saveData(newLinkedHashSet(Items.RED_MUSHROOM_BLOCK, new ItemStack(Items.RED_MUSHROOM_BLOCK)), fungal(5));
        saveData(newLinkedHashSet(Items.REDSTONE, new ItemStack(Items.REDSTONE)), logic(15));
        saveData(newLinkedHashSet(Items.ROTTEN_FLESH, new ItemStack(Items.ROTTEN_FLESH)), faunal(5), decay(60));
        saveData(newLinkedHashSet(Items.SALMON, new ItemStack(Items.SALMON)), faunal(15), vital(1));
        saveData(newLinkedHashSet(Items.SCUTE, new ItemStack(Items.SCUTE)), faunal(60), weird(6), vital(1));
        saveData(newLinkedHashSet(Items.SEA_PICKLE, new ItemStack(Items.SEA_PICKLE)), faunal(30), aquatic(60), chromatic(240));
        saveData(newLinkedHashSet(Items.SEAGRASS, new ItemStack(Items.SEAGRASS)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.SHULKER_SHELL, new ItemStack(Items.SHULKER_SHELL)), faunal(60), weird(180));
        saveData(newLinkedHashSet(Items.SHROOMLIGHT, new ItemStack(Items.SHROOMLIGHT)), radiant(40), fungal(60));
        saveData(newLinkedHashSet(Items.SLIME_BALL, new ItemStack(Items.SLIME_BALL)), slime(60));
        saveData(newLinkedHashSet(Items.SNOWBALL, new ItemStack(Items.SNOWBALL)), snow(15));
        saveData(newLinkedHashSet(Items.SOUL_SAND, new ItemStack(Items.SOUL_SAND)), earthen(180), vital(15), decay(60));
        saveData(newLinkedHashSet(Items.SOUL_SOIL, new ItemStack(Items.SOUL_SOIL)), earthen(180), vital(15), decay(60));
        saveData(newLinkedHashSet(Items.SOUL_CAMPFIRE, new ItemStack(Items.SOUL_CAMPFIRE)), radiant(80), decay(60));
        saveData(newLinkedHashSet(Items.SOUL_TORCH, new ItemStack(Items.SOUL_TORCH)), radiant(5), decay(15));
        saveData(newLinkedHashSet(Items.SPIDER_EYE, new ItemStack(Items.SPIDER_EYE)), faunal(15), weird(5));
        saveData(newLinkedHashSet(Items.STICK, new ItemStack(Items.STICK)), floral(16));
        saveData(newLinkedHashSet(Items.STRING, new ItemStack(Items.STRING)), faunal(15));
        saveData(newLinkedHashSet(Items.CAMPFIRE, new ItemStack(Items.CAMPFIRE)), radiant(80));
        saveData(newLinkedHashSet(Items.TORCH, new ItemStack(Items.TORCH)), radiant(5));
        // wool is too dang strong
        saveData(newLinkedHashSet(Items.WHITE_WOOL, new ItemStack(Items.WHITE_WOOL)), faunal(15));
        saveData(newLinkedHashSet(Items.RED_WOOL, new ItemStack(Items.RED_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.ORANGE_WOOL, new ItemStack(Items.ORANGE_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.YELLOW_WOOL, new ItemStack(Items.YELLOW_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.GREEN_WOOL, new ItemStack(Items.GREEN_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.BLUE_WOOL, new ItemStack(Items.BLUE_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.PURPLE_WOOL, new ItemStack(Items.PURPLE_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.PINK_WOOL, new ItemStack(Items.PINK_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.LIME_WOOL, new ItemStack(Items.LIME_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.CYAN_WOOL, new ItemStack(Items.CYAN_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.GRAY_WOOL, new ItemStack(Items.GRAY_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.LIGHT_GRAY_WOOL, new ItemStack(Items.LIGHT_GRAY_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.BLACK_WOOL, new ItemStack(Items.BLACK_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.BROWN_WOOL, new ItemStack(Items.BROWN_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.LIGHT_BLUE_WOOL, new ItemStack(Items.LIGHT_BLUE_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.MAGENTA_WOOL, new ItemStack(Items.MAGENTA_WOOL)), faunal(15), chromatic(15));
        saveData(newLinkedHashSet(Items.SUGAR_CANE, new ItemStack(Items.SUGAR_CANE)), floral(15));
        saveData(newLinkedHashSet(Items.SWEET_BERRIES, new ItemStack(Items.SWEET_BERRIES)), floral(5), vital(1));
        saveData(newLinkedHashSet(Items.TROPICAL_FISH, new ItemStack(Items.TROPICAL_FISH)), faunal(15), vital(15));
        saveData(newLinkedHashSet(Items.TURTLE_EGG, new ItemStack(Items.TURTLE_EGG)), faunal(30), vital(30), weird(60));
        saveData(newLinkedHashSet(Items.TWISTING_VINES, new ItemStack(Items.TWISTING_VINES)), fungal(5));
        saveData(newLinkedHashSet(Items.VINE, new ItemStack(Items.VINE)), floral(15));
        saveData(newLinkedHashSet(Items.WARPED_NYLIUM, new ItemStack(Items.WARPED_NYLIUM)), earthen(180), molten(60), fungal(15));
        saveData(newLinkedHashSet(Items.WARPED_FUNGUS, new ItemStack(Items.WARPED_FUNGUS)), fungal(15));
        saveData(newLinkedHashSet(Items.WEEPING_VINES, new ItemStack(Items.WEEPING_VINES)), fungal(5));
        saveData(newLinkedHashSet(Items.WET_SPONGE, new ItemStack(Items.WET_SPONGE)), aquatic(60), weird(6), faunal(30), vital(15));
        saveData(newLinkedHashSet(Items.WHEAT, new ItemStack(Items.WHEAT)), floral(15), vital(1));
        saveData(newLinkedHashSet(Items.WHEAT_SEEDS, new ItemStack(Items.WHEAT_SEEDS)), floral(15), vital(1));

        // containers
        saveData(newLinkedHashSet(Items.LAVA_BUCKET, new ItemStack(Items.LAVA_BUCKET)), metal(216), molten(1000));
        saveData(newLinkedHashSet(Items.MILK_BUCKET, new ItemStack(Items.MILK_BUCKET)), metal(216), faunal(15));
        saveData(newLinkedHashSet(Items.WATER_BUCKET, new ItemStack(Items.WATER_BUCKET)), metal(216), aquatic(60));

        ItemsRegistry.CrystallizedGoo.forEach(this::registerLockedInfoForCrystallizedGoo);
    }

    private void registerLockedInfoForCrystallizedGoo(ResourceLocation resourceLocation, RegistryObject<CrystallizedGooAbstract> crystallizedGooAbstractRegistryObject) {
        Fluid f = crystallizedGooAbstractRegistryObject.get().gooType();

        final CompoundInstance instance = compoundsFromFluid(f, crystallizedGooAbstractRegistryObject.get().amount());
        if (instance == null)
            return;

        saveData(newLinkedHashSet(crystallizedGooAbstractRegistryObject.get(), new ItemStack(crystallizedGooAbstractRegistryObject.get())), instance);
    }

    private LinkedHashSet<Object> newLinkedHashSet(final Object... internal) {
        return new LinkedHashSet<>(Arrays.asList(internal));
    }

    @Nullable
    private CompoundInstance compoundsFromFluid(Fluid f, int amount) {
        final ICompoundType type = Registry.compoundFromFluid(f);
        if (type == null)
            return null;

        return new CompoundInstance(Registry.compoundFromFluid(f), amount);
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
    private static CompoundInstance radiant(double d) { return new CompoundInstance(Registry.RADIANT.get(), d); }
    private static CompoundInstance regal(double d) { return new CompoundInstance(Registry.REGAL.get(), d); }
    private static CompoundInstance slime(double d) { return new CompoundInstance(Registry.SLIME.get(), d); }
    private static CompoundInstance snow(double d) { return new CompoundInstance(Registry.SNOW.get(), d); }
    private static CompoundInstance vital(double d) { return new CompoundInstance(Registry.VITAL.get(), d); }
    private static CompoundInstance weird(double d) { return new CompoundInstance(Registry.WEIRD.get(), d); }

    private static CompoundInstance forbidden(double d) { return new CompoundInstance(Registry.FORBIDDEN.get(), d); }
}
