package com.xeno.goop.setup;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DefaultMappings {
    public static String volatileGoop = Objects.requireNonNull(Registry.VOLATILE_GOOP.get().getRegistryName()).toString();
    public static String earthenGoop = Objects.requireNonNull(Registry.EARTHEN_GOOP.get().getRegistryName()).toString();
    public static String aquaticGoop = Objects.requireNonNull(Registry.AQUATIC_GOOP.get().getRegistryName()).toString();
    public static String esotericGoop = Objects.requireNonNull(Registry.ESOTERIC_GOOP.get().getRegistryName()).toString();
    public static String floralGoop = Objects.requireNonNull(Registry.FLORAL_GOOP.get().getRegistryName()).toString();
    public static String faunalGoop = Objects.requireNonNull(Registry.FAUNAL_GOOP.get().getRegistryName()).toString();
    public static String fungalGoop = Objects.requireNonNull(Registry.FUNGAL_GOOP.get().getRegistryName()).toString();
    public static String regalGoop = Objects.requireNonNull(Registry.REGAL_GOOP.get().getRegistryName()).toString();

    public static List<GoopValueMapping> values = new ArrayList<>();

    static {
        addBaseMappings();
    }

    private static void addBaseMappings() {
        addMapping(MappingGroups.leaves, floral(15));
        addMapping(MappingGroups.logs, floral(480));
        addMapping(MappingGroups.strippedLogs, floral(480));
        addMapping(MappingGroups.saplings, floral(45));
        addMapping(MappingGroups.singleDyeFlowers, floral(120));
        addMapping(MappingGroups.coral, faunal(15), aquatic(15));
        addMapping(MappingGroups.coralBlocks, faunal(60), aquatic(60));
        addMapping(MappingGroups.coralFans, faunal(15), aquatic(15));
        addMapping(MappingGroups.oneBlockFoliage, floral(15));
        addMapping(MappingGroups.twoBlockFoliage, floral(30));
        addMapping(Items.APPLE, floral(180));
        addMapping(Items.BAMBOO, floral(60));
        addMapping(Items.BEE_NEST, floral(1125));
        addMapping(Items.BEEF, faunal(360));
        addMapping(Items.BEETROOT, floral(45));
        addMapping(Items.BEETROOT_SEEDS, floral(45));
        addMapping(Items.BLAZE_ROD, burning(360));
        addMapping(Items.BONE, faunal(360) );
        addMapping(Items.BROWN_MUSHROOM, fungal(135));
        addMapping(Items.BROWN_MUSHROOM_BLOCK, fungal(30));
        addMapping(Items.CACTUS, floral(120));
        addMapping(Items.CARROT, floral(135));
        addMapping(Items.CHICKEN, );
        addMapping(Items.CHORUS_FLOWER, );
        addMapping(Items.CHORUS_FRUIT, );
        addMapping(Items.CLAY_BALL, );
        addMapping(Items.COBWEB, );
        addMapping(Items.COD, );
        addMapping(Items.EGG, );
        addMapping(Items.END_STONE, );
        addMapping(Items.ENDER_PEARL, );
        addMapping(Items.FEATHER, );
        addMapping(Items.GHAST_TEAR, );
        addMapping(Items.GLOBE_BANNER_PATTERN, );
        addMapping(Items.GLOWSTONE_DUST, );
        addMapping(Items.GRASS_BLOCK, );
        addMapping(Items.GUNPOWDER, );
        addMapping(Items.HONEYCOMB, );
        addMapping(Items.ICE, );
        addMapping(Items.INK_SAC, faunal(120))
        addMapping(Items.KELP, );
        addMapping(Items.LAVA_BUCKET, );
        addMapping(Items.LILY_PAD, );
        addMapping(Items.MELON_SLICE, );
        addMapping(Items.MILK_BUCKET, );
        addMapping(Items.MUSHROOM_STEM, );
        addMapping(Items.MUTTON, );
        addMapping(Items.MYCELIUM, );
        addMapping(Items.NETHER_WART, );
        addMapping(Items.NETHERRACK, );
        addMapping(Items.OBSIDIAN, );
        addMapping(Items.PHANTOM_MEMBRANE, );
        addMapping(Items.POISONOUS_POTATO, );
        addMapping(Items.POPPY, );
        addMapping(Items.PORKCHOP, );
        addMapping(Items.POTATO, );
        addMapping(Items.PRISMARINE_CRYSTALS, );
        addMapping(Items.PRISMARINE_SHARD, );
        addMapping(Items.PUFFERFISH, );
        addMapping(Items.PUMPKIN, );
        addMapping(Items.RABBIT, );
        addMapping(Items.RABBIT_FOOT, );
        addMapping(Items.RABBIT_HIDE, );
        addMapping(Items.RED_MUSHROOM, );
        addMapping(Items.RED_MUSHROOM_BLOCK, );
        addMapping(Items.ROTTEN_FLESH, );
        addMapping(Items.SALMON, );
        addMapping(Items.SCUTE, );
        addMapping(Items.SEA_PICKLE, );
        addMapping(Items.SEAGRASS, );
        addMapping(Items.SHULKER_SHELL, );
        addMapping(Items.SNOWBALL, );
        addMapping(Items.SOUL_SAND, );
        addMapping(Items.SPIDER_EYE, );
        addMapping(Items.STRING, );
        addMapping(Items.SUGAR_CANE, );
        addMapping(Items.SWEET_BERRIES, );
        addMapping(Items.TROPICAL_FISH, );
        addMapping(Items.TURTLE_EGG, );
        addMapping(Items.VINE, );
        addMapping(Items.WATER_BUCKET, );
        addMapping(Items.WET_SPONGE, );
        addMapping(Items.WHEAT_SEEDS, );
    }

    private static GoopValue earthen(int i) {
        return goopValue(earthenGoop, i);
    }

    private static GoopValue burning(int i) {
        return goopValue(volatileGoop, i);
    }

    private static GoopValue aquatic(int i) {
        return goopValue(aquaticGoop, i);
    }

    private static GoopValue esoteric(int i) {
        return goopValue(esotericGoop, i);
    }

    private static GoopValue floral(int i) {
        return goopValue(floralGoop, i);
    }

    private static GoopValue faunal(int i) {
        return goopValue(faunalGoop, i);
    }

    private static GoopValue fungal(int i) {
        return goopValue(fungalGoop, i);
    }

    private static GoopValue regal(int i) {
        return goopValue(regalGoop, i);
    }

    private static GoopValue goopValue(String s, int i) {
        return new GoopValue(s, i);
    }

    private static void addMapping(MappingGroup g, GoopValue... args) {
        for(Item item : g.items) {
            addMapping(item.getRegistryName().toString(), args);
        }
    }

    private static void addMapping(Item item, GoopValue... args) {
        addMapping(item.getRegistryName().toString(), args);
    }

    private static void addMapping(String resourceLocation, GoopValue... args) {
        values.add(new GoopValueMapping(resourceLocation, new ArrayList<>(Arrays.asList(args))));
    }

}
