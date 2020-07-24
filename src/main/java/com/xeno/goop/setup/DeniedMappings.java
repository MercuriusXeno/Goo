package com.xeno.goop.setup;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class DeniedMappings {
    public static List<GoopValueMapping> values = new ArrayList<>();

    static {
        denyUndesirableMappings();
    }

    private static void denyUndesirableMappings() {
        denyMapping(MappingGroups.spawnEggs);
        denyMapping(Items.BARRIER);
        denyMapping(Items.BEDROCK);
        denyMapping(Items.BELL);
        denyMapping(Items.CHAIN_COMMAND_BLOCK);
        denyMapping(Items.CHAINMAIL_BOOTS);
        denyMapping(Items.CHAINMAIL_CHESTPLATE);
        denyMapping(Items.CHAINMAIL_HELMET);
        denyMapping(Items.CHAINMAIL_LEGGINGS);
        denyMapping(Items.CHIPPED_ANVIL);
        denyMapping(Items.CHORUS_PLANT);
        denyMapping(Items.COAL_ORE);
        denyMapping(Items.COD_BUCKET);
        denyMapping(Items.COMMAND_BLOCK);
        denyMapping(Items.COMMAND_BLOCK_MINECART);
        denyMapping(Items.CREEPER_HEAD);
        denyMapping(Items.DAMAGED_ANVIL);
        denyMapping(Items.DEBUG_STICK);
        denyMapping(Items.DIAMOND_HORSE_ARMOR);
        denyMapping(Items.DIAMOND_ORE);
        denyMapping(Items.DRAGON_BREATH);
        denyMapping(Items.DRAGON_EGG);
        denyMapping(Items.DRAGON_HEAD);
        denyMapping(Items.ELYTRA);
        denyMapping(Items.EMERALD_ORE);
        denyMapping(Items.ENCHANTED_BOOK);
        denyMapping(Items.ENCHANTED_GOLDEN_APPLE);
        denyMapping(Items.END_PORTAL_FRAME);
        denyMapping(Items.EXPERIENCE_BOTTLE);
        denyMapping(Items.FARMLAND);
        denyMapping(Items.FILLED_MAP);
        denyMapping(Items.GOLD_ORE);
        denyMapping(Items.GOLDEN_HORSE_ARMOR);
        denyMapping(Items.GRASS_PATH);
        denyMapping(Items.HEART_OF_THE_SEA);
        denyMapping(Items.INFESTED_CHISELED_STONE_BRICKS);
        denyMapping(Items.INFESTED_COBBLESTONE);
        denyMapping(Items.INFESTED_CRACKED_STONE_BRICKS);
        denyMapping(Items.INFESTED_MOSSY_STONE_BRICKS);
        denyMapping(Items.INFESTED_STONE);
        denyMapping(Items.INFESTED_STONE_BRICKS);
        denyMapping(Items.IRON_HORSE_ARMOR);
        denyMapping(Items.IRON_ORE);
        denyMapping(Items.JIGSAW);
        denyMapping(Items.KNOWLEDGE_BOOK);
        denyMapping(Items.LAPIS_ORE);
        denyMapping(Items.LINGERING_POTION);
        denyMapping(Items.MUSIC_DISC_11);
        denyMapping(Items.MUSIC_DISC_13);
        denyMapping(Items.MUSIC_DISC_BLOCKS);
        denyMapping(Items.MUSIC_DISC_CAT);
        denyMapping(Items.MUSIC_DISC_CHIRP);
        denyMapping(Items.MUSIC_DISC_FAR);
        denyMapping(Items.MUSIC_DISC_MALL);
        denyMapping(Items.MUSIC_DISC_MELLOHI);
        denyMapping(Items.MUSIC_DISC_STAL);
        denyMapping(Items.MUSIC_DISC_STRAD);
        denyMapping(Items.MUSIC_DISC_WAIT);
        denyMapping(Items.MUSIC_DISC_WARD);
        denyMapping(Items.NAME_TAG);
        denyMapping(Items.NAUTILUS_SHELL);
        denyMapping(Items.NETHER_QUARTZ_ORE);
        denyMapping(Items.NETHER_STAR);
        denyMapping(Items.PETRIFIED_OAK_SLAB);
        denyMapping(Items.PLAYER_HEAD);
        denyMapping(Items.POTION);
        denyMapping(Items.PUFFERFISH_BUCKET);
        denyMapping(Items.REDSTONE_ORE);
        denyMapping(Items.REPEATING_COMMAND_BLOCK);
        denyMapping(Items.SADDLE);
        denyMapping(Items.SALMON_BUCKET);
        denyMapping(Items.SKELETON_SKULL);
        denyMapping(Items.SPAWNER);
        denyMapping(Items.SPLASH_POTION);
        denyMapping(Items.STRUCTURE_BLOCK);
        denyMapping(Items.STRUCTURE_VOID);
        denyMapping(Items.SUSPICIOUS_STEW);
        denyMapping(Items.TIPPED_ARROW);
        denyMapping(Items.TOTEM_OF_UNDYING);
        denyMapping(Items.TRIDENT);
        denyMapping(Items.TROPICAL_FISH_BUCKET);
        denyMapping(Items.WITHER_ROSE);
        denyMapping(Items.WITHER_SKELETON_SKULL);
        denyMapping(Items.WRITTEN_BOOK);
        denyMapping(Items.ZOMBIE_HEAD);
    }

    private static void denyMapping(MappingGroup g) {
        for (Item item : g.items) {
            denyMapping(item);
        }
    }

    private static void denyMapping(Item item) {
        denyMapping(item.getRegistryName().toString());
    }

    private static void denyMapping(String resourceLocation) {
        values.add(new GoopValueMapping(resourceLocation));
    }
}
