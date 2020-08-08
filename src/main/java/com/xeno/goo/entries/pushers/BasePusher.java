package com.xeno.goo.entries.pushers;

import com.xeno.goo.library.*;
import com.xeno.goo.entries.EntryGroups;
import com.xeno.goo.entries.EntryPhase;
import net.minecraft.item.Foods;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

import static com.xeno.goo.library.EntryHelper.*;

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
            addEntry(Objects.requireNonNull(item.getRegistryName()).toString(), args);
        }
    }

    private void addEntry(Item item, GooValue... args) {
        addEntry(Objects.requireNonNull(item.getRegistryName()).toString(), args);
    }

    private void addEntry(String resourceLocation, GooValue... args) {
        // mappings in defaults are "fixed", meaning they can't be overwritten by improvements during a tracked push
        values.put(resourceLocation, new GooEntry(Arrays.asList(args), true));
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
        addEntry(EntryGroups.leaves, floral(1));
        addEntry(EntryGroups.logs, floral(96));
        addEntry(EntryGroups.stems, fungal(72), chromatic(72));
        addEntry(EntryGroups.strippedLogs, floral(96));
        addEntry(EntryGroups.strippedStems, fungal(72), chromatic(72));
        addEntry(EntryGroups.saplings, floral(8));
        addEntry(EntryGroups.singleDyeFlowers, floral(12));
        addEntry(EntryGroups.doubleDyeFlowers, floral(24));
        addEntry(EntryGroups.dyes, chromatic(8));
        addEntry(EntryGroups.coral, faunal(1), aquatic(1));
        addEntry(EntryGroups.coralBlocks, faunal(4), aquatic(4));
        addEntry(EntryGroups.coralFans, faunal(1), aquatic(1));
        addEntry(EntryGroups.oneBlockFoliage, floral(1));
        addEntry(EntryGroups.oneBlockNetherFoliage, fungal(1));
        addEntry(EntryGroups.twoBlockFoliage, floral(2));
        addEntry(EntryGroups.earthenBlocks, earthen(3));

        addEntry(Items.APPLE, floral(1), vital(Foods.APPLE));
        addEntry(Items.BAMBOO, floral(6));
        addEntry(Items.BEEF, faunal(1), vital(Foods.COOKED_BEEF));
        addEntry(Items.BEETROOT, floral(1), vital(Foods.BEETROOT), chromatic(8));
        addEntry(Items.BEETROOT_SEEDS, floral(1));
        addEntry(Items.BLAZE_ROD, molten(Items.BLAZE_ROD));
        addEntry(Items.BONE, faunal(3), chromatic(24));
        addEntry(Items.BROWN_MUSHROOM, fungal(1));
        addEntry(Items.BROWN_MUSHROOM_BLOCK, fungal(0.2d));
        addEntry(Items.CACTUS, floral(1), chromatic(8));
        addEntry(Items.CARROT, floral(1), vital(Foods.CARROT));
        addEntry(Items.CARVED_PUMPKIN, floral(1), esoteric(1));
        addEntry(Items.CHICKEN, faunal(1), vital(Foods.COOKED_CHICKEN));
        addEntry(Items.CHORUS_FLOWER, floral(4), esoteric(4));
        addEntry(Items.CHORUS_FRUIT, floral(6), esoteric(1));
        addEntry(Items.CLAY_BALL, earthen(0.25d));
        addEntry(Items.COAL, molten(Items.COAL));
        addEntry(Items.COBWEB, faunal(4));
        addEntry(Items.COD, faunal(1), aquatic(1), vital(Foods.COOKED_COD));
        addEntry(Items.CRIMSON_NYLIUM, earthen(0.5d), molten(0.5d), fungal(0.5d));
        addEntry(Items.CRIMSON_FUNGUS, fungal(4));
        addEntry(Items.CRYING_OBSIDIAN, earthen(4), regal(1), esoteric(1));
        addEntry(Items.DIAMOND, earthen(72), regal(96), molten(96), esoteric(96));
        addEntry(Items.EGG, faunal(1), vital(5));
        addEntry(Items.EMERALD, earthen(96), regal(96));
        addEntry(Items.END_STONE, earthen(1.5d), esoteric(0.5d));
        addEntry(Items.ENDER_PEARL, faunal(1), esoteric(3));
        addEntry(Items.FEATHER, faunal(1));
        addEntry(Items.GHAST_TEAR, faunal(4), esoteric(8), regal(2));
        addEntry(Items.GILDED_BLACKSTONE, metal(20), regal(20), esoteric(10), earthen(3));
        addEntry(Items.GLASS, earthen(1), regal(1)); // glass is special
        addEntry(Items.GLOWSTONE_DUST, esoteric(3));
        addEntry(Items.GOLD_INGOT, metal(72), regal(72), esoteric(36));
        addEntry(Items.GRASS_BLOCK, earthen(1.5d), floral(0.5d));
        addEntry(Items.GUNPOWDER, molten(9d), faunal(3), esoteric(3));
        addEntry(Items.HONEYCOMB, floral(4), faunal(4), regal(4));
        addEntry(Items.ICE, aquatic(1));
        addEntry(Items.INK_SAC, faunal(1), chromatic(8));
        addEntry(Items.IRON_INGOT, earthen(36d), metal(72d));
        addEntry(Items.KELP, floral(1), aquatic(1), vital(Foods.DRIED_KELP));
        addEntry(Items.LAPIS_LAZULI, regal(2), esoteric(2), chromatic(8));
        addEntry(Items.LILY_PAD, floral(1), aquatic(1));
        addEntry(Items.MELON_SLICE, floral(1), vital(Foods.MELON_SLICE));
        addEntry(Items.MUSHROOM_STEM, fungal(1));
        addEntry(Items.MUTTON, faunal(1), vital(Foods.COOKED_MUTTON));
        addEntry(Items.MYCELIUM, earthen(0.5d), fungal(1));
        addEntry(Items.NETHER_WART, esoteric(1), fungal(1));
        addEntry(Items.NETHERITE_SCRAP, metal(96), regal(96), esoteric(96), molten(96));
        addEntry(Items.NETHERRACK, earthen(0.5d), molten(0.5d));
        addEntry(Items.OBSIDIAN, earthen(4), regal(1));
        addEntry(Items.PHANTOM_MEMBRANE, faunal(6), esoteric(6));
        addEntry(Items.POISONOUS_POTATO, floral(1), esoteric(1), vital(Foods.POISONOUS_POTATO));
        addEntry(Items.PORKCHOP, faunal(1), vital(Foods.COOKED_PORKCHOP));
        addEntry(Items.POTATO, floral(1), vital(Foods.BAKED_POTATO));
        addEntry(Items.PRISMARINE_CRYSTALS, faunal(1), regal(2), aquatic(1));
        addEntry(Items.PRISMARINE_SHARD, faunal(1), regal(2), aquatic(2));
        addEntry(Items.PUFFERFISH, faunal(1), aquatic(2), esoteric(1));
        addEntry(Items.PUMPKIN, floral(1));
        addEntry(Items.QUARTZ, earthen(1), regal(2), esoteric(1));
        addEntry(Items.RABBIT, faunal(1), vital(Foods.COOKED_RABBIT));
        addEntry(Items.RABBIT_FOOT, faunal(1), esoteric(3));
        addEntry(Items.RABBIT_HIDE, faunal(0.25d));
        addEntry(Items.RED_MUSHROOM, fungal(1));
        addEntry(Items.RED_MUSHROOM_BLOCK, fungal(0.2d));
        addEntry(Items.REDSTONE, esoteric(3d));
        addEntry(Items.ROTTEN_FLESH, faunal(1), vital(Foods.ROTTEN_FLESH), esoteric(1));
        addEntry(Items.SALMON, faunal(1), aquatic(1), vital(Foods.COOKED_SALMON));
        addEntry(Items.SCUTE, faunal(1), aquatic(1), esoteric(6));
        addEntry(Items.SEA_PICKLE, faunal(3), aquatic(3), chromatic(8));
        addEntry(Items.SEAGRASS, floral(1), aquatic(1));
        addEntry(Items.SHULKER_SHELL, faunal(3), esoteric(7));
        addEntry(Items.SHROOMLIGHT, esoteric(12), fungal(8));
        addEntry(Items.SLIME_BALL, faunal(4), esoteric(2));
        addEntry(Items.SNOWBALL, aquatic(1));
        addEntry(Items.SOUL_SAND, earthen(1), esoteric(1));
        addEntry(Items.SOUL_SOIL, earthen(1), esoteric(1));
        addEntry(Items.SPIDER_EYE, faunal(1), esoteric(1), vital(Foods.SPIDER_EYE));
        addEntry(Items.STRING, faunal(1.5d));
        addEntry(Items.SUGAR_CANE, floral(1));
        addEntry(Items.SWEET_BERRIES, floral(1), vital(Foods.SWEET_BERRIES));
        addEntry(Items.TROPICAL_FISH, faunal(1), aquatic(3), regal(3));
        addEntry(Items.TURTLE_EGG, faunal(3), aquatic(15), vital(20));
        addEntry(Items.TWISTING_VINES, fungal(0.5d));
        addEntry(Items.VINE, floral(1));
        addEntry(Items.WARPED_NYLIUM, earthen(0.5d), molten(0.5d), fungal(0.5d));
        addEntry(Items.WARPED_FUNGUS, fungal(4));
        addEntry(Items.WEEPING_VINES, fungal(0.5d));
        addEntry(Items.WET_SPONGE, aquatic(1), esoteric(1), faunal(2), vital(1));
        addEntry(Items.WHEAT, floral(0.5d));
        addEntry(Items.WHEAT_SEEDS, (floral(0.2d)));
    }
}
