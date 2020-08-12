package com.xeno.goo.evaluations.pushers;

import com.xeno.goo.evaluations.*;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

import static com.xeno.goo.evaluations.EntryHelper.*;

/**
 * Baseline mappings are mappings specified, by hand, for items which you want to cascade all derivative values from.
 * They should include pretty much everything that isn't the output of a recipe, with some exceptions.
 * For example, ore blocks in the mod are denied mappings deliberately, so their smelted products are mapped
 * (which defies the convention of not assigning baselines to recipe results, with good reason)
 * Baseline mappings have 3 sources: 1st is your world save file, highest priority. Second is your global config.
 * Third and final is this source, which will initiate the others if they're missing.
 */
public class BasePusher extends EntryPusher
{

    public BasePusher(ServerWorld world) {
        super(EntryPhase.BASELINE, "goo-mappings-baseline.json", world);
    }

    private void addEntry(EntryGroup g, GooValue... args) {
        for(Item item : g.items) {
            addEntry(true, Objects.requireNonNull(item.getRegistryName()).toString(), args);
        }
    }

    private void addEntry(Item item, GooValue... args) {
        addEntry(true, Objects.requireNonNull(item.getRegistryName()).toString(), args);
    }

    private void addEntryUnattainable(Item item, GooValue... args)
    {
        addEntry(false, Objects.requireNonNull(item.getRegistryName()).toString(), args);
    }

    private void addEntry(boolean isAttainable, String resourceLocation, GooValue... args) {
        // mappings in defaults are "fixed", meaning they can't be overwritten by improvements during a tracked push
        values.put(resourceLocation, new GooEntry(Arrays.asList(args)));
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
        addEntry(EntryGroups.leaves, floral(60), vital(60));
        addEntry(EntryGroups.logs, floral(960), vital(60));
        addEntry(EntryGroups.stems, fungal(960), chromatic(60), vital (60));
        addEntry(EntryGroups.strippedLogs, floral(960));
        addEntry(EntryGroups.strippedStems, fungal(960), chromatic(60));
        addEntry(EntryGroups.saplings, floral(960), vital(60));
        addEntry(EntryGroups.singleDyeFlowers, floral(60), chromatic(240), vital(60));
        addEntry(EntryGroups.doubleDyeFlowers, floral(120), chromatic(480), vital(120));
        addEntry(EntryGroups.dyes, chromatic(240));
        addEntry(EntryGroups.coral, faunal(60), vital(60));
        addEntry(EntryGroups.coralBlocks, faunal(480), vital(480));
        addEntry(EntryGroups.coralFans, faunal(60), vital(60));
        addEntry(EntryGroups.oneBlockFoliage, floral(120), vital(60));
        addEntry(EntryGroups.oneBlockNetherFoliage, fungal(120), vital(60));
        addEntry(EntryGroups.twoBlockFoliage, floral(240), vital(120));
        addEntry(EntryGroups.earthenBlocks, earthen(1080));

        addEntry(Items.APPLE, floral(60), vital(60));
        addEntry(Items.BAMBOO, floral(60));
        addEntry(Items.BASALT, earthen(960), obsidian(60));
        addEntry(Items.BEEF, faunal(60), vital(60));
        addEntry(Items.BEETROOT, floral(60), vital(60), chromatic(240));
        addEntry(Items.BEETROOT_SEEDS, floral(60));
        addEntry(Items.BLAZE_ROD, molten(120), energetic(60), vital(60));
        addEntry(Items.BONE, faunal(180), chromatic(360), vital(180), decay(60));
        addEntry(Items.BROWN_MUSHROOM, fungal(60), vital(60));
        addEntry(Items.BROWN_MUSHROOM_BLOCK, fungal(960));
        addEntry(Items.CACTUS, floral(960), chromatic(240), vital (60));
        addEntry(Items.CARROT, floral(60), vital(60));
        addEntry(Items.CARVED_PUMPKIN, floral(60), weird(60));
        addEntryUnattainable(Items.CHARCOAL, molten(96), floral(96));
        addEntry(Items.CHICKEN, faunal(60), vital(60));
        addEntry(Items.CHORUS_FLOWER, floral(240), weird(240), vital(60));
        addEntry(Items.CHORUS_FRUIT, floral(60), weird(60), vital(60));
        addEntry(Items.CLAY_BALL, earthen(240));
        addEntry(Items.COAL, molten(96), earthen(72));
        addEntry(Items.COBWEB, faunal(120));
        addEntry(Items.COD, faunal(60), vital(60));
        addEntry(Items.CRIMSON_NYLIUM, earthen(480d), fungal(480d), chromatic(60));
        addEntry(Items.CRIMSON_FUNGUS, fungal(120));
        addEntry(Items.CRYING_OBSIDIAN, weird(60), obsidian(960));
        addEntry(Items.DIAMOND, crystal(120));
        addEntry(Items.EGG, faunal(60), vital(60));
        addEntry(Items.EMERALD, regal(60), crystal(60));
        addEntry(Items.END_STONE, earthen(720), weird(240), vital(60));
        addEntry(Items.ENDER_PEARL, weird(120));
        addEntry(Items.FEATHER, faunal(60));
        addEntry(Items.GHAST_TEAR, weird(240), crystal(24));
        addEntry(Items.GILDED_BLACKSTONE, metal(240), regal(240), weird(60), earthen(240), obsidian(240));
        addEntry(Items.GLOWSTONE_DUST, weird(60), energetic(60));
        addEntry(Items.GOLD_INGOT, metal(36), regal(72));
        addEntry(Items.GRASS_BLOCK, earthen(960), floral(60));
        addEntry(Items.GUNPOWDER, molten(60), energetic(120));
        addEntry(Items.HONEYCOMB, honey(120), regal(24));
        addEntry(Items.HONEY_BLOCK, honey(960), regal(60));
        addEntry(Items.ICE, snow(960), aquatic(60));
        addEntry(Items.INK_SAC, faunal(60), chromatic(240));
        addEntry(Items.IRON_INGOT, metal(72));
        addEntry(Items.KELP, floral(60), vital(60));
        addEntry(Items.LAPIS_LAZULI, weird(48), chromatic(240), crystal(4));
        addEntry(Items.LILY_PAD, floral(60), aquatic(60), vital(60));
        addEntry(Items.MELON_SLICE, floral(60), vital(60));
        addEntry(Items.MUSHROOM_STEM, fungal(960));
        addEntry(Items.MUTTON, faunal(60), vital(60));
        addEntry(Items.MYCELIUM, earthen(960d), fungal(60));
        addEntry(Items.NETHER_WART, weird(60), fungal(60));
        addEntry(Items.NETHERITE_SCRAP, metal(960), obsidian(60));
        addEntry(Items.NETHERRACK, earthen(960), molten(60));
        addEntry(Items.OBSIDIAN, obsidian(960), molten(60));
        addEntry(Items.PHANTOM_MEMBRANE, decay(60), vital(60), weird(60));
        addEntry(Items.POISONOUS_POTATO, floral(60), weird(60), vital(60));
        addEntry(Items.PORKCHOP, faunal(60), vital(60));
        addEntry(Items.POTATO, floral(60), vital(60));
        addEntry(Items.PRISMARINE_CRYSTALS, aquatic(36), crystal(2), weird(36), energetic(72));
        addEntry(Items.PRISMARINE_SHARD, aquatic(36), crystal(1), weird(72));
        addEntry(Items.PUFFERFISH, faunal(60), weird(60));
        addEntry(Items.PUMPKIN, floral(960));
        addEntry(Items.QUARTZ, earthen(60), crystal (1));
        addEntry(Items.RABBIT, faunal(60), vital(60));
        addEntry(Items.RABBIT_FOOT, faunal(60), weird(180));
        addEntry(Items.RABBIT_HIDE, faunal(60));
        addEntry(Items.RED_MUSHROOM, fungal(60));
        addEntry(Items.RED_MUSHROOM_BLOCK, fungal(960));
        addEntry(Items.REDSTONE, logic(120));
        addEntry(Items.ROTTEN_FLESH, faunal(60), decay(60));
        addEntry(Items.SALMON, faunal(60), vital(60));
        addEntry(Items.SCUTE, faunal(60), weird(60), vital(60));
        addEntry(Items.SEA_PICKLE, faunal(60), aquatic(60), chromatic(240));
        addEntry(Items.SEAGRASS, floral(60), vital(60));
        addEntry(Items.SHULKER_SHELL, faunal(60), weird(180));
        addEntry(Items.SHROOMLIGHT, weird(120), fungal(360));
        addEntry(Items.SLIME_BALL, slime(240));
        addEntry(Items.SNOWBALL, snow(120), aquatic(60));
        addEntry(Items.SOUL_SAND, earthen(720), vital(240), decay(60));
        addEntry(Items.SOUL_SOIL, earthen(720), vital(240), decay(60));
        addEntry(Items.SPIDER_EYE, faunal(60), weird(60));
        addEntry(Items.STRING, faunal(60));
        addEntry(Items.SUGAR_CANE, floral(60));
        addEntry(Items.SWEET_BERRIES, floral(60), vital(60));
        addEntry(Items.TROPICAL_FISH, faunal(60), vital(60));
        addEntry(Items.TURTLE_EGG, faunal(60), vital(180), weird(60));
        addEntry(Items.TWISTING_VINES, fungal(60));
        addEntry(Items.VINE, floral(60));
        addEntry(Items.WARPED_NYLIUM, earthen(720), molten(60), fungal(240));
        addEntry(Items.WARPED_FUNGUS, fungal(120));
        addEntry(Items.WEEPING_VINES, fungal(60));
        addEntry(Items.WET_SPONGE, aquatic(60), weird(60), faunal(120), vital(720));
        addEntry(Items.WHEAT, floral(60));
        addEntry(Items.WHEAT_SEEDS, (floral(60)));
    }
}
