package com.xeno.goop.setup;

import com.xeno.goop.library.*;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.*;

import static com.xeno.goop.library.GoopMapping.DENIED;

public class DeniedMappings {
    private Map<String, GoopMapping> values = new TreeMap<>(Compare.stringLexicographicalComparator);

    public DeniedMappings() {
        this.init();
    }

    public ProgressState pushTo(Map<String, GoopMapping> target) { return Helper.trackedPush(values, target); }

    private void denyMapping(MappingGroup g) { g.items.forEach(this::denyMapping); }

    private void denyMapping(Item item) { denyMapping(Objects.requireNonNull(item.getRegistryName()).toString()); }

    private void denyMapping(String resourceLocation) { values.put(resourceLocation, GoopMapping.DENIED); }

    // deny things unattainable for various reasons
    private void init() {
        denyMapping(MappingGroups.spawnEggs);
        denyMapping(MappingGroups.oreBlocks);
        denyMapping(MappingGroups.musicDiscs);
        denyMapping(MappingGroups.infestedBlocks);
        denyMapping(MappingGroups.skulls);
        denyMapping(MappingGroups.potions);
        denyMapping(MappingGroups.structureBlocks);

        denyMapping(Items.BEDROCK);
        denyMapping(Items.BELL);
        denyMapping(Items.CHAINMAIL_BOOTS);
        denyMapping(Items.CHAINMAIL_CHESTPLATE);
        denyMapping(Items.CHAINMAIL_HELMET);
        denyMapping(Items.CHAINMAIL_LEGGINGS);
        denyMapping(Items.CHIPPED_ANVIL);
        denyMapping(Items.CHORUS_PLANT);
        denyMapping(Items.COD_BUCKET);
        denyMapping(Items.DAMAGED_ANVIL);
        denyMapping(Items.DEBUG_STICK);
        denyMapping(Items.DIAMOND_HORSE_ARMOR);
        denyMapping(Items.DRAGON_BREATH);
        denyMapping(Items.DRAGON_EGG);
        denyMapping(Items.ELYTRA);
        denyMapping(Items.ENCHANTED_BOOK);
        denyMapping(Items.ENCHANTED_GOLDEN_APPLE);
        denyMapping(Items.END_PORTAL_FRAME);
        denyMapping(Items.EXPERIENCE_BOTTLE);
        denyMapping(Items.FARMLAND);
        denyMapping(Items.FILLED_MAP);
        denyMapping(Items.GLOBE_BANNER_PATTERN);
        denyMapping(Items.GOLDEN_HORSE_ARMOR);
        denyMapping(Items.GRASS_PATH);
        denyMapping(Items.HEART_OF_THE_SEA);
        denyMapping(Items.IRON_HORSE_ARMOR);
        denyMapping(Items.JIGSAW);
        denyMapping(Items.KNOWLEDGE_BOOK);
        denyMapping(Items.MAP);
        denyMapping(Items.NAME_TAG);
        denyMapping(Items.NAUTILUS_SHELL);
        denyMapping(Items.NETHER_STAR);
        denyMapping(Items.PETRIFIED_OAK_SLAB);
        denyMapping(Items.PUFFERFISH_BUCKET);
        denyMapping(Items.SADDLE);
        denyMapping(Items.SALMON_BUCKET);
        denyMapping(Items.SPAWNER);
        denyMapping(Items.SUSPICIOUS_STEW);
        denyMapping(Items.TIPPED_ARROW);
        denyMapping(Items.TOTEM_OF_UNDYING);
        denyMapping(Items.TRIDENT);
        denyMapping(Items.TROPICAL_FISH_BUCKET);
        denyMapping(Items.WITHER_ROSE);
        denyMapping(Items.WRITTEN_BOOK);
    }
}
