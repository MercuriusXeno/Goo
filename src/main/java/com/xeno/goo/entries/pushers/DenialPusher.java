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
        super(EntryPhase.DENIED, "goo-mappings-denials.json", world);
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
        denyEntry(EntryGroups.spawnEggs);
        denyEntry(EntryGroups.oreBlocks);
        denyEntry(EntryGroups.musicDiscs);
        denyEntry(EntryGroups.infestedBlocks);
        denyEntry(EntryGroups.skulls);
        denyEntry(EntryGroups.potions);
        denyEntry(EntryGroups.structureBlocks);
        denyEntry(EntryGroups.dyedShulkerBoxes);
        denyEntry(EntryGroups.netheriteStuff);

        denyEntry(Items.AIR);
        denyEntry(Items.ANCIENT_DEBRIS);
        denyEntry(Items.BEDROCK);
        denyEntry(Items.BELL);
        denyEntry(Items.CHAINMAIL_BOOTS);
        denyEntry(Items.CHAINMAIL_CHESTPLATE);
        denyEntry(Items.CHAINMAIL_HELMET);
        denyEntry(Items.CHAINMAIL_LEGGINGS);
        denyEntry(Items.CHIPPED_ANVIL);
        denyEntry(Items.CHORUS_PLANT);
        denyEntry(Items.COD_BUCKET);
        denyEntry(Items.DAMAGED_ANVIL);
        denyEntry(Items.DEBUG_STICK);
        denyEntry(Items.DIAMOND_HORSE_ARMOR);
        denyEntry(Items.DRAGON_BREATH);
        denyEntry(Items.DRAGON_EGG);
        denyEntry(Items.ELYTRA);
        denyEntry(Items.ENCHANTED_BOOK);
        denyEntry(Items.ENCHANTED_GOLDEN_APPLE);
        denyEntry(Items.END_PORTAL_FRAME);
        denyEntry(Items.EXPERIENCE_BOTTLE);
        denyEntry(Items.FARMLAND);
        denyEntry(Items.FILLED_MAP);
        denyEntry(Items.FIREWORK_STAR);
        denyEntry(Items.FIREWORK_ROCKET);
        denyEntry(Items.GLOBE_BANNER_PATTERN);
        denyEntry(Items.GOLDEN_HORSE_ARMOR);
        denyEntry(Items.GRASS_PATH);
        denyEntry(Items.HEART_OF_THE_SEA);
        denyEntry(Items.IRON_HORSE_ARMOR);
        denyEntry(Items.JIGSAW);
        denyEntry(Items.KNOWLEDGE_BOOK);
        denyEntry(Items.MAP);
        denyEntry(Items.NAME_TAG);
        denyEntry(Items.NAUTILUS_SHELL);
        denyEntry(Items.NETHER_STAR);
        denyEntry(Items.PETRIFIED_OAK_SLAB);
        denyEntry(Items.PIGLIN_BANNER_PATTERN);
        denyEntry(Items.PUFFERFISH_BUCKET);
        denyEntry(Items.SADDLE);
        denyEntry(Items.SALMON_BUCKET);
        denyEntry(Items.SHULKER_BOX);
        denyEntry(Items.SPAWNER);
        denyEntry(Items.SUSPICIOUS_STEW);
        denyEntry(Items.TIPPED_ARROW);
        denyEntry(Items.TOTEM_OF_UNDYING);
        denyEntry(Items.TRIDENT);
        denyEntry(Items.TROPICAL_FISH_BUCKET);
        denyEntry(Items.WITHER_ROSE);
        denyEntry(Items.WRITTEN_BOOK);
    }

    private void denyEntry(EntryGroup g) { g.items.forEach(this::denyEntry); }

    private void denyEntry(Item item) { denyEntry(Objects.requireNonNull(item.getRegistryName()).toString()); }

    private void denyEntry(String resourceLocation) { values.put(resourceLocation, GooEntry.DENIED); }
}
