package com.xeno.goop.setup;

import com.xeno.goop.library.MappingGroup;
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
            Items.POPPY,
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

    public static MappingGroup oreBlocks = new MappingGroup (
            Items.COAL_ORE,
            Items.DIAMOND_ORE,
            Items.EMERALD_ORE,
            Items.GOLD_ORE,
            Items.IRON_ORE,
            Items.LAPIS_ORE,
            Items.NETHER_QUARTZ_ORE,
            Items.REDSTONE_ORE
    );

    public static MappingGroup musicDiscs = new MappingGroup (
            Items.MUSIC_DISC_11,
            Items.MUSIC_DISC_13,
            Items.MUSIC_DISC_BLOCKS,
            Items.MUSIC_DISC_CAT,
            Items.MUSIC_DISC_CHIRP,
            Items.MUSIC_DISC_FAR,
            Items.MUSIC_DISC_MALL,
            Items.MUSIC_DISC_MELLOHI,
            Items.MUSIC_DISC_STAL,
            Items.MUSIC_DISC_STRAD,
            Items.MUSIC_DISC_WAIT,
            Items.MUSIC_DISC_WARD
    );

    public static MappingGroup infestedBlocks = new MappingGroup (
            Items.INFESTED_CHISELED_STONE_BRICKS,
            Items.INFESTED_COBBLESTONE,
            Items.INFESTED_CRACKED_STONE_BRICKS,
            Items.INFESTED_MOSSY_STONE_BRICKS,
            Items.INFESTED_STONE,
            Items.INFESTED_STONE_BRICKS
    );

    public static MappingGroup skulls = new MappingGroup (
            Items.CREEPER_HEAD,
            Items.DRAGON_HEAD,
            Items.PLAYER_HEAD,
            Items.SKELETON_SKULL,
            Items.WITHER_SKELETON_SKULL,
            Items.ZOMBIE_HEAD
    );

    public static MappingGroup potions = new MappingGroup (
            Items.LINGERING_POTION,
            Items.POTION,
            Items.SPLASH_POTION
    );

    public static MappingGroup structureBlocks = new MappingGroup (
            Items.BARRIER,
            Items.CHAIN_COMMAND_BLOCK,
            Items.COMMAND_BLOCK,
            Items.COMMAND_BLOCK_MINECART,
            Items.REPEATING_COMMAND_BLOCK,
            Items.STRUCTURE_BLOCK,
            Items.STRUCTURE_VOID
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
    public static MappingGroup dyes = new MappingGroup (
        Items.BLACK_DYE,
        Items.BLUE_DYE,
        Items.BROWN_DYE,
        Items.CYAN_DYE,
        Items.GRAY_DYE,
        Items.GREEN_DYE,
        Items.LIGHT_BLUE_DYE,
        Items.LIGHT_GRAY_DYE,
        Items.LIME_DYE,
        Items.MAGENTA_DYE,
        Items.ORANGE_DYE,
        Items.PINK_DYE,
        Items.PURPLE_DYE,
        Items.RED_DYE,
        Items.WHITE_DYE,
        Items.YELLOW_DYE
    );
}
