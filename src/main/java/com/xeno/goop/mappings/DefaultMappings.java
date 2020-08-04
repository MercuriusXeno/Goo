package com.xeno.goop.mappings;

import com.xeno.goop.library.*;
import net.minecraft.item.Foods;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.*;

import static com.xeno.goop.library.Helper.*;

public class DefaultMappings {
    private Map<String, GoopMapping> values = new HashMap<>();

    public DefaultMappings() {
        this.init();
    }

    public ProgressState pushTo(Map<String, GoopMapping> target) { return Helper.trackedPush(values, target); }

    private void addMapping(MappingGroup g, GoopValue... args) {
        for(Item item : g.items) {
            addMapping(item.getRegistryName().toString(), args);
        }
    }

    private void addMapping(Item item, GoopValue... args) {
        addMapping(item.getRegistryName().toString(), args);
    }

    private void addMapping(String resourceLocation, GoopValue... args) {
        // mappings in defaults are "fixed", meaning they can't be overwritten by improvements during a tracked push
        values.put(resourceLocation, new GoopMapping(Arrays.asList(args), true));
    }

    private void init() {
        addMapping(MappingGroups.leaves, floral(1));
        addMapping(MappingGroups.logs, floral(72));
        addMapping(MappingGroups.strippedLogs, floral(72));
        addMapping(MappingGroups.saplings, floral(8));
        addMapping(MappingGroups.singleDyeFlowers, floral(12));
        addMapping(MappingGroups.doubleDyeFlowers, floral(24));
        addMapping(MappingGroups.dyes, chromatic(8));
        addMapping(MappingGroups.coral, faunal(1), aquatic(1));
        addMapping(MappingGroups.coralBlocks, faunal(4), aquatic(4));
        addMapping(MappingGroups.coralFans, faunal(1), aquatic(1));
        addMapping(MappingGroups.oneBlockFoliage, floral(1));
        addMapping(MappingGroups.twoBlockFoliage, floral(2));
        addMapping(MappingGroups.earthenBlocks, earthen(3));

        addMapping(Items.APPLE, floral(1), vital(Foods.APPLE));
        addMapping(Items.BAMBOO, floral(4.5));
        addMapping(Items.BEEF, faunal(1), vital(Foods.COOKED_BEEF));
        addMapping(Items.BEETROOT, floral(1), vital(Foods.BEETROOT), chromatic(8));
        addMapping(Items.BEETROOT_SEEDS, floral(1));
        addMapping(Items.BLAZE_ROD, molten(Items.BLAZE_ROD));
        addMapping(Items.BONE, faunal(3), chromatic(24));
        addMapping(Items.BROWN_MUSHROOM, fungal(1));
        addMapping(Items.BROWN_MUSHROOM_BLOCK, fungal(0.2d));
        addMapping(Items.CACTUS, floral(1), chromatic(8));
        addMapping(Items.CARROT, floral(1), vital(Foods.CARROT));
        addMapping(Items.CARVED_PUMPKIN, floral(1), esoteric(1));
        addMapping(Items.CHICKEN, faunal(1), vital(Foods.COOKED_CHICKEN));
        addMapping(Items.CHORUS_FLOWER, floral(4), esoteric(4));
        addMapping(Items.CHORUS_FRUIT, floral(6), esoteric(1));
        addMapping(Items.CLAY_BALL, earthen(0.25d));
        addMapping(Items.COAL, molten(Items.COAL));
        addMapping(Items.COBWEB, faunal(4));
        addMapping(Items.COD, faunal(1), aquatic(1), vital(Foods.COOKED_COD));
        addMapping(Items.DIAMOND, earthen(72), regal(96), molten(96), esoteric(96));
        addMapping(Items.EGG, faunal(1), vital(5));
        addMapping(Items.EMERALD, earthen(96), regal(96));
        addMapping(Items.END_STONE, earthen(1.5d), esoteric(0.5d));
        addMapping(Items.ENDER_PEARL, faunal(1), esoteric(3));
        addMapping(Items.FEATHER, faunal(1));
        addMapping(Items.GHAST_TEAR, faunal(4), esoteric(8), regal(2));
        addMapping(Items.GLASS, earthen(1), regal(1)); // glass is special
        addMapping(Items.GLOWSTONE_DUST, esoteric(3));
        addMapping(Items.GOLD_INGOT, metal(36), regal(36), esoteric(18));
        addMapping(Items.GRASS_BLOCK, earthen(1.5d), floral(0.5d));
        addMapping(Items.GUNPOWDER, molten(9d), faunal(3), esoteric(3));
        addMapping(Items.HONEYCOMB, floral(4), faunal(4), regal(4));
        addMapping(Items.ICE, aquatic(1));
        addMapping(Items.INK_SAC, faunal(1), chromatic(8));
        addMapping(Items.IRON_INGOT, earthen(9d), metal(18d));
        addMapping(Items.KELP, floral(1), aquatic(1), vital(Foods.DRIED_KELP));
        addMapping(Items.LAPIS_LAZULI, regal(2), esoteric(2), chromatic(8));
        addMapping(Items.LILY_PAD, floral(1), aquatic(1));
        addMapping(Items.MELON_SLICE, floral(1d), vital(Foods.MELON_SLICE));
        addMapping(Items.MUSHROOM_STEM, fungal(1));
        addMapping(Items.MUTTON, faunal(1), vital(Foods.COOKED_MUTTON));
        addMapping(Items.MYCELIUM, earthen(0.5d), fungal(1d));
        addMapping(Items.NETHER_WART, esoteric(1d), fungal(1d));
        addMapping(Items.NETHERRACK, earthen(0.5d), molten(0.5d));
        addMapping(Items.OBSIDIAN, earthen(4), regal(1));
        addMapping(Items.PHANTOM_MEMBRANE, faunal(6), esoteric(6));
        addMapping(Items.POISONOUS_POTATO, floral(1), esoteric(1), vital(Foods.POISONOUS_POTATO));
        addMapping(Items.PORKCHOP, faunal(1), vital(Foods.COOKED_PORKCHOP));
        addMapping(Items.POTATO, floral(1), vital(Foods.BAKED_POTATO));
        addMapping(Items.PRISMARINE_CRYSTALS, faunal(1), regal(2), aquatic(1));
        addMapping(Items.PRISMARINE_SHARD, faunal(1), regal(2), aquatic(2));
        addMapping(Items.PUFFERFISH, faunal(1), aquatic(2), esoteric(1));
        addMapping(Items.PUMPKIN, floral(1));
        addMapping(Items.QUARTZ, earthen(1d), regal(2), esoteric(1d));
        addMapping(Items.RABBIT, faunal(1), vital(Foods.COOKED_RABBIT));
        addMapping(Items.RABBIT_FOOT, faunal(1), esoteric(3));
        addMapping(Items.RABBIT_HIDE, faunal(0.25d));
        addMapping(Items.RED_MUSHROOM, fungal(1));
        addMapping(Items.RED_MUSHROOM_BLOCK, fungal(0.2d));
        addMapping(Items.REDSTONE, esoteric(3d));
        addMapping(Items.ROTTEN_FLESH, faunal(1), vital(Foods.ROTTEN_FLESH), esoteric(1d));
        addMapping(Items.SALMON, faunal(1), aquatic(1), vital(Foods.COOKED_SALMON));
        addMapping(Items.SCUTE, faunal(1), aquatic(1), esoteric(6));
        addMapping(Items.SEA_PICKLE, faunal(3), aquatic(3), chromatic(8));
        addMapping(Items.SEAGRASS, floral(1), aquatic(1));
        addMapping(Items.SHULKER_SHELL, faunal(3), esoteric(7));
        addMapping(Items.SLIME_BALL, faunal(4), esoteric(2));
        addMapping(Items.SNOWBALL, aquatic(1d));
        addMapping(Items.SOUL_SAND, earthen(1), esoteric(1));
        addMapping(Items.SPIDER_EYE, faunal(1), esoteric(1), vital(Foods.SPIDER_EYE));
        addMapping(Items.STRING, faunal(1.5d));
        addMapping(Items.SUGAR_CANE, floral(1));
        addMapping(Items.SWEET_BERRIES, floral(1), vital(Foods.SWEET_BERRIES));
        addMapping(Items.TROPICAL_FISH, faunal(1), aquatic(3), regal(3));
        addMapping(Items.TURTLE_EGG, faunal(3), aquatic(15), vital(20));
        addMapping(Items.VINE, floral(1));
        addMapping(Items.WET_SPONGE, aquatic(1), esoteric(1), faunal(2), vital(1));
        addMapping(Items.WHEAT, floral(0.5d));
        addMapping(Items.WHEAT_SEEDS, (floral(0.2d)));
    }
}
