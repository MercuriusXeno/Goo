package com.xeno.goop.setup;

import com.xeno.goop.library.*;
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
        addMapping(MappingGroups.leaves, floral(15));
        addMapping(MappingGroups.logs, floral(960));
        addMapping(MappingGroups.strippedLogs, floral(960));
        addMapping(MappingGroups.saplings, floral(45));
        addMapping(MappingGroups.singleDyeFlowers, floral(120));
        addMapping(MappingGroups.doubleDyeFlowers, floral(240));
        addMapping(MappingGroups.dyes, floral(120));
        addMapping(MappingGroups.coral, faunal(15), aquatic(15));
        addMapping(MappingGroups.coralBlocks, faunal(60), aquatic(60));
        addMapping(MappingGroups.coralFans, faunal(15), aquatic(15));
        addMapping(MappingGroups.oneBlockFoliage, floral(15));
        addMapping(MappingGroups.twoBlockFoliage, floral(30));
        addMapping(MappingGroups.earthenBlocks, earthen(240));

        addMapping(Items.APPLE, floral(180));
        addMapping(Items.BAMBOO, floral(60));
        addMapping(Items.BEE_NEST, floral(1125));
        addMapping(Items.BEEF, faunal(360));
        addMapping(Items.BEETROOT, floral(120));
        addMapping(Items.BEETROOT_SEEDS, floral(60));
        addMapping(Items.BLAZE_ROD, burning(360));
        addMapping(Items.BONE, faunal(480) );
        addMapping(Items.BROWN_MUSHROOM, fungal(135));
        addMapping(Items.BROWN_MUSHROOM_BLOCK, fungal(30));
        addMapping(Items.CACTUS, floral(120));
        addMapping(Items.CARROT, floral(135));
        addMapping(Items.CARVED_PUMPKIN, floral(15), esoteric(15));
        addMapping(Items.CHICKEN, faunal(270));
        addMapping(Items.CHORUS_FLOWER, floral(90), esoteric(135));
        addMapping(Items.CHORUS_FRUIT, floral(120), esoteric(60));
        addMapping(Items.CLAY_BALL, earthen(60));
        addMapping(Items.COAL, burning(240));
        addMapping(Items.COBWEB, faunal(135));
        addMapping(Items.COD, faunal(180), aquatic(45));
        addMapping(Items.DIAMOND, earthen(7200), regal(9600), burning(7200), esoteric(7200));
        addMapping(Items.EGG, faunal(150));
        addMapping(Items.EMERALD, earthen(7200), regal(9600));
        addMapping(Items.END_STONE, earthen(180), esoteric(60));
        addMapping(Items.ENDER_PEARL, faunal(30), esoteric(90));
        addMapping(Items.FEATHER, faunal(120));
        addMapping(Items.GHAST_TEAR, faunal(270), esoteric(180), regal(180));
        addMapping(Items.GLASS, earthen(120), regal(120)); // glass is special
        addMapping(Items.GLOWSTONE_DUST, esoteric(45));
        addMapping(Items.GOLD_INGOT, regal(1080), esoteric(540));
        addMapping(Items.GRASS_BLOCK, earthen(180), floral(60));
        addMapping(Items.GUNPOWDER, burning(90), faunal(30), esoteric(30));
        addMapping(Items.HONEYCOMB, floral(135));
        addMapping(Items.ICE, aquatic(120), regal(15));
        addMapping(Items.INK_SAC, faunal(120));
        addMapping(Items.IRON_INGOT, earthen(360), regal(720));
        addMapping(Items.KELP, floral(45), aquatic(45));
        addMapping(Items.LAPIS_LAZULI, regal(120), esoteric(120));
        addMapping(Items.LILY_PAD, floral(15), aquatic(15));
        addMapping(Items.MELON_SLICE, floral(90));
        addMapping(Items.MUSHROOM_STEM, fungal(30));
        addMapping(Items.MUTTON, faunal(270));
        addMapping(Items.MYCELIUM, earthen(120), fungal(120));
        addMapping(Items.NETHER_WART, esoteric(30), fungal(30));
        addMapping(Items.NETHERRACK, earthen(120), burning(120));
        addMapping(Items.OBSIDIAN, earthen(720), regal(180));
        addMapping(Items.PHANTOM_MEMBRANE, faunal(270), esoteric(360));
        addMapping(Items.POISONOUS_POTATO, floral(90), esoteric(30));
        addMapping(Items.PORKCHOP, faunal(360));
        addMapping(Items.POTATO, floral(45));
        addMapping(Items.PRISMARINE_CRYSTALS, faunal(60), regal(120), aquatic(60));
        addMapping(Items.PRISMARINE_SHARD, faunal(60), regal(120), aquatic(120));
        addMapping(Items.PUFFERFISH, faunal(45), aquatic(135), esoteric(90));
        addMapping(Items.PUMPKIN, floral(120));
        addMapping(Items.QUARTZ, regal(120));
        addMapping(Items.RABBIT, faunal(225));
        addMapping(Items.RABBIT_FOOT, faunal(90), esoteric(180));
        addMapping(Items.RABBIT_HIDE, faunal(45));
        addMapping(Items.RED_MUSHROOM, fungal(135));
        addMapping(Items.RED_MUSHROOM_BLOCK, fungal(30));
        addMapping(Items.REDSTONE, esoteric(90));
        addMapping(Items.ROTTEN_FLESH, faunal(180));
        addMapping(Items.SALMON, faunal(135), aquatic(135));
        addMapping(Items.SCUTE, faunal(90), aquatic(135), esoteric(45));
        addMapping(Items.SEA_PICKLE, faunal(75), aquatic(45));
        addMapping(Items.SEAGRASS, floral(15), aquatic(15));
        addMapping(Items.SHULKER_SHELL, faunal(270), esoteric(225));
        addMapping(Items.SLIME_BALL, faunal(120));
        addMapping(Items.SNOWBALL, aquatic(15));
        addMapping(Items.SOUL_SAND, earthen(90), esoteric(30));
        addMapping(Items.SPIDER_EYE, faunal(90), esoteric(45));
        addMapping(Items.STRING, faunal(60));
        addMapping(Items.SUGAR_CANE, floral(90));
        addMapping(Items.SWEET_BERRIES, floral(90));
        addMapping(Items.TROPICAL_FISH, faunal(45), aquatic(225));
        addMapping(Items.TURTLE_EGG, faunal(135), aquatic(2250));
        addMapping(Items.VINE, floral(30));
        addMapping(Items.WET_SPONGE, aquatic(15));
        addMapping(Items.WHEAT, floral(60));
        addMapping(Items.WHEAT_SEEDS, (floral(30)));
    }
}
