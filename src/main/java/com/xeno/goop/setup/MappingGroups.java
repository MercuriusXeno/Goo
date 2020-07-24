package com.xeno.goop.setup;

import net.minecraft.item.Items;

public class MappingGroups {

    public static MappingGroup leaves = new MappingGroup(
            Items.ACACIA_LEAVES,
            Items.BIRCH_LEAVES,
            Items.DARK_OAK_LEAVES,
            Items.JUNGLE_LEAVES,
            Items.OAK_LEAVES,
            Items.SPRUCE_LEAVES
            );

    public static MappingGroup logs = new MappingGroup (
            Items.ACACIA_LOG,
            Items.BIRCH_LOG,
            Items.DARK_OAK_LOG,
            Items.JUNGLE_LOG,
            Items.OAK_LOG,
            Items.SPRUCE_LOG
            );

    public static MappingGroup strippedLogs = new MappingGroup (
            Items.STRIPPED_ACACIA_LOG,
            Items.STRIPPED_BIRCH_LOG,
            Items.STRIPPED_DARK_OAK_LOG,
            Items.STRIPPED_JUNGLE_LOG,
            Items.STRIPPED_OAK_LOG,
            Items.STRIPPED_SPRUCE_LOG
    );

    public static MappingGroup saplings = new MappingGroup (
            Items.ACACIA_SAPLING,
            Items.BIRCH_SAPLING,
            Items.DARK_OAK_SAPLING,
            Items.JUNGLE_SAPLING,
            Items.OAK_SAPLING,
            Items.SPRUCE_SAPLING
            );

    public static MappingGroup oneBlockFoliage = new MappingGroup (
            Items.DEAD_BUSH,
            Items.FERN,
            Items.GRASS
            );

    public static MappingGroup twoBlockFoliage = new MappingGroup (
            Items.LARGE_FERN,
            Items.TALL_GRASS
    );

    public static MappingGroup singleDyeFlowers = new MappingGroup (
            Items.ALLIUM,
            Items.AZURE_BLUET,
            Items.BLUE_ORCHID,
            Items.COCOA_BEANS,
            Items.CORNFLOWER,
            Items.DANDELION,
            Items.LILY_OF_THE_VALLEY,
            Items.ORANGE_TULIP,
            Items.OXEYE_DAISY,
            Items.PINK_TULIP,
            Items.RED_TULIP,
            Items.WHITE_TULIP
            );

    public static MappingGroup doubleDyeFlowers = new MappingGroup (
            Items.LILAC,
            Items.PEONY,
            Items.ROSE_BUSH,
            Items.SUNFLOWER
    );

    public static MappingGroup earthenBlocks = new MappingGroup (
            Items.COBBLESTONE,
            Items.DIRT,
            Items.FLINT,
            Items.GRAVEL,
            Items.PODZOL,
            Items.RED_SAND,
            Items.SAND
        );

    public static MappingGroup concrete = new MappingGroup (
            Items.BLACK_CONCRETE,
            Items.BLUE_CONCRETE,
            Items.BROWN_CONCRETE,
            Items.CYAN_CONCRETE,
            Items.GRAY_CONCRETE,
            Items.GREEN_CONCRETE,
            Items.LIGHT_BLUE_CONCRETE,
            Items.LIGHT_GRAY_CONCRETE,
            Items.LIME_CONCRETE,
            Items.MAGENTA_CONCRETE,
            Items.ORANGE_CONCRETE,
            Items.PINK_CONCRETE,
            Items.PURPLE_CONCRETE,
            Items.RED_CONCRETE,
            Items.WHITE_CONCRETE,
            Items.YELLOW_CONCRETE
    );

    public static MappingGroup concretePowder = new MappingGroup (
            Items.BLACK_CONCRETE_POWDER,
            Items.BLUE_CONCRETE_POWDER,
            Items.BROWN_CONCRETE_POWDER,
            Items.CYAN_CONCRETE_POWDER,
            Items.GRAY_CONCRETE_POWDER,
            Items.GREEN_CONCRETE_POWDER,
            Items.LIGHT_BLUE_CONCRETE_POWDER,
            Items.LIGHT_GRAY_CONCRETE_POWDER,
            Items.LIME_CONCRETE_POWDER,
            Items.MAGENTA_CONCRETE_POWDER,
            Items.ORANGE_CONCRETE_POWDER,
            Items.PINK_CONCRETE_POWDER,
            Items.PURPLE_CONCRETE_POWDER,
            Items.RED_CONCRETE_POWDER,
            Items.WHITE_CONCRETE_POWDER,
            Items.YELLOW_CONCRETE_POWDER
    );

    public static MappingGroup dyedShulkerBox = new MappingGroup (Items.BLACK_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX,
            Items.BROWN_SHULKER_BOX,
            Items.CYAN_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX,
            Items.GREEN_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX,
            Items.LIGHT_GRAY_SHULKER_BOX,
            Items.LIME_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX,
            Items.ORANGE_SHULKER_BOX,
            Items.PINK_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX,
            Items.RED_SHULKER_BOX,
            Items.WHITE_SHULKER_BOX,
            Items.YELLOW_SHULKER_BOX
    );

    public static MappingGroup coral = new MappingGroup (
            Items.BRAIN_CORAL,
            Items.BUBBLE_CORAL,
            Items.DEAD_BRAIN_CORAL,
            Items.DEAD_BUBBLE_CORAL,
            Items.DEAD_FIRE_CORAL,
            Items.DEAD_HORN_CORAL,
            Items.DEAD_TUBE_CORAL,
            Items.FIRE_CORAL,
            Items.HORN_CORAL,
            Items.TUBE_CORAL
    );

    public static MappingGroup coralBlocks = new MappingGroup(
            Items.BRAIN_CORAL_BLOCK,
            Items.BUBBLE_CORAL_BLOCK,
            Items.DEAD_BRAIN_CORAL_BLOCK,
            Items.DEAD_BUBBLE_CORAL_BLOCK,
            Items.DEAD_FIRE_CORAL_BLOCK,
            Items.DEAD_HORN_CORAL_BLOCK,
            Items.DEAD_TUBE_CORAL_BLOCK,
            Items.FIRE_CORAL_BLOCK,
            Items.HORN_CORAL_BLOCK,
            Items.TUBE_CORAL_BLOCK
    );

    public static MappingGroup coralFans = new MappingGroup(
            Items.BRAIN_CORAL_FAN,
            Items.BUBBLE_CORAL_FAN,
            Items.DEAD_BRAIN_CORAL_FAN,
            Items.DEAD_BUBBLE_CORAL_FAN,
            Items.DEAD_FIRE_CORAL_FAN,
            Items.DEAD_HORN_CORAL_FAN,
            Items.DEAD_TUBE_CORAL_FAN,
            Items.FIRE_CORAL_FAN,
            Items.HORN_CORAL_FAN,
            Items.TUBE_CORAL_FAN
    );

    public static MappingGroup spawnEggs = new MappingGroup (
            Items.BAT_SPAWN_EGG,
            Items.BEE_SPAWN_EGG,
            Items.BLAZE_SPAWN_EGG,
            Items.CAT_SPAWN_EGG,
            Items.CAVE_SPIDER_SPAWN_EGG,
            Items.CHICKEN_SPAWN_EGG,
            Items.COD_SPAWN_EGG,
            Items.COW_SPAWN_EGG,
            Items.CREEPER_SPAWN_EGG,
            Items.DOLPHIN_SPAWN_EGG,
            Items.DONKEY_SPAWN_EGG,
            Items.DROWNED_SPAWN_EGG,
            Items.ELDER_GUARDIAN_SPAWN_EGG,
            Items.ENDERMAN_SPAWN_EGG,
            Items.ENDERMITE_SPAWN_EGG,
            Items.EVOKER_SPAWN_EGG,
            Items.FOX_SPAWN_EGG,
            Items.GHAST_SPAWN_EGG,
            Items.GUARDIAN_SPAWN_EGG,
            Items.HORSE_SPAWN_EGG,
            Items.HUSK_SPAWN_EGG,
            Items.LLAMA_SPAWN_EGG,
            Items.MAGMA_CUBE_SPAWN_EGG,
            Items.MOOSHROOM_SPAWN_EGG,
            Items.MULE_SPAWN_EGG,
            Items.OCELOT_SPAWN_EGG,
            Items.PANDA_SPAWN_EGG,
            Items.PARROT_SPAWN_EGG,
            Items.PHANTOM_SPAWN_EGG,
            Items.PIG_SPAWN_EGG,
            Items.PILLAGER_SPAWN_EGG,
            Items.POLAR_BEAR_SPAWN_EGG,
            Items.PUFFERFISH_SPAWN_EGG,
            Items.RABBIT_SPAWN_EGG,
            Items.RAVAGER_SPAWN_EGG,
            Items.SALMON_SPAWN_EGG,
            Items.SHEEP_SPAWN_EGG,
            Items.SHULKER_SPAWN_EGG,
            Items.SILVERFISH_SPAWN_EGG,
            Items.SKELETON_HORSE_SPAWN_EGG,
            Items.SKELETON_SPAWN_EGG,
            Items.SLIME_SPAWN_EGG,
            Items.SPIDER_SPAWN_EGG,
            Items.SQUID_SPAWN_EGG,
            Items.STRAY_SPAWN_EGG,
            Items.TRADER_LLAMA_SPAWN_EGG,
            Items.TROPICAL_FISH_SPAWN_EGG,
            Items.TURTLE_SPAWN_EGG,
            Items.VEX_SPAWN_EGG,
            Items.VILLAGER_SPAWN_EGG,
            Items.VINDICATOR_SPAWN_EGG,
            Items.WANDERING_TRADER_SPAWN_EGG,
            Items.WITCH_SPAWN_EGG,
            Items.WITHER_SKELETON_SPAWN_EGG,
            Items.WOLF_SPAWN_EGG,
            Items.ZOMBIE_HORSE_SPAWN_EGG,
            Items.ZOMBIE_PIGMAN_SPAWN_EGG,
            Items.ZOMBIE_SPAWN_EGG,
            Items.ZOMBIE_VILLAGER_SPAWN_EGG
    );
}
