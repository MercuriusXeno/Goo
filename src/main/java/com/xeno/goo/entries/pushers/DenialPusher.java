package com.xeno.goo.entries.pushers;

import com.xeno.goo.library.*;
import com.xeno.goo.entries.EntryGroups;
import com.xeno.goo.entries.EntryPhase;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

public class DenialPusher extends EntryPusher
{
    public DenialPusher(ServerWorld world) {
        super(EntryPhase.DENIED, "goop-mappings-denials.json", world);
    }

    @Override
    protected boolean load()
    {
        values = FileHelper.readEntryFile(this.file());
        return values.size() > 0;
    }

    @Override
    protected void save()
    {
        FileHelper.writeEntryFile(this.file(), values);
    }

    public ProgressState pushTo(Map<String, GooEntry> target) { return EntryHelper.trackedPush(values, target); }

    @Override
    public void process()
    {
        this.resolve();
    }

    @Override
    protected void clearProcessing()
    {
        // NO OP
    }

    @Override
    protected void seedDefaults()
    {
        denyMapping(EntryGroups.spawnEggs);
        denyMapping(EntryGroups.oreBlocks);
        denyMapping(EntryGroups.musicDiscs);
        denyMapping(EntryGroups.infestedBlocks);
        denyMapping(EntryGroups.skulls);
        denyMapping(EntryGroups.potions);
        denyMapping(EntryGroups.structureBlocks);
        denyMapping(EntryGroups.dyedShulkerBoxes);
        denyMapping(EntryGroups.netheriteStuff);

        denyMapping(Items.AIR);
        denyMapping(Items.ANCIENT_DEBRIS);
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
        denyMapping(Items.FIREWORK_STAR);
        denyMapping(Items.FIREWORK_ROCKET);
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
        denyMapping(Items.PIGLIN_BANNER_PATTERN);
        denyMapping(Items.PUFFERFISH_BUCKET);
        denyMapping(Items.SADDLE);
        denyMapping(Items.SALMON_BUCKET);
        denyMapping(Items.SHULKER_BOX);
        denyMapping(Items.SPAWNER);
        denyMapping(Items.SUSPICIOUS_STEW);
        denyMapping(Items.TIPPED_ARROW);
        denyMapping(Items.TOTEM_OF_UNDYING);
        denyMapping(Items.TRIDENT);
        denyMapping(Items.TROPICAL_FISH_BUCKET);
        denyMapping(Items.WITHER_ROSE);
        denyMapping(Items.WRITTEN_BOOK);
    }

    private void denyMapping(EntryGroup g) { g.items.forEach(this::denyMapping); }

    private void denyMapping(Item item) { denyMapping(Objects.requireNonNull(item.getRegistryName()).toString()); }

    private void denyMapping(String resourceLocation) { values.put(resourceLocation, GooEntry.DENIED); }
}
