package com.xeno.goo.entries.pushers.impl;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.FileHelper;
import com.xeno.goo.entries.EntryPhase;
import com.xeno.goo.entries.pushers.EntryPusher;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.server.ServerWorld;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static com.xeno.goo.library.Compare.stringLexicographicalComparator;

/**
 * Forced equivalencies are when we just want thing A to be worth the same as thing B.
 * Usually we do this at the expense of (or after) recipe mappings. In some cases (concrete)
 * the recipe doesn't exist because it is the result of an in world event.
 * Other times, it's more like coal and charcoal. One is the result of mining, and the other
 * is of a recipe. But the smelting recipe resulting in charcoal is pure floral.
 * We don't want that; instead, we'd rather prefer charcoal simply equaled coal, for mechanical reasons.
 * Thus, it is a forced equivalency.
 * Bees nest to beehives, similar.
 */
public class SimpleExchangePusher extends EntryPusher
{
    public Map<String, String> equivalencies = new TreeMap<>(stringLexicographicalComparator);
    public SimpleExchangePusher(ServerWorld world) {
        super(EntryPhase.DEFERRED, "goo-mappings-simple-equivalencies.json", world);
    }

    @Override
    protected boolean load()
    {
        equivalencies = FileHelper.readEquivalencyFile(this.file());
        return equivalencies.size() > 0;
    }

    @Override
    protected void save()
    {
        FileHelper.writeEquivalencyFile(this.file(), equivalencies);
    }

    @Override
    public void process()
    {
        equivalencies.forEach(this::processEntry);
    }

    @Override
    protected void clearProcessing()
    {
        equivalencies.clear();
    }

    private void processEntry(String target, String source)
    {
        values.put(target, GooMod.mappingHandler.get(source));
    }


    @Override
    protected void seedDefaults()
    {
        addEquivalency(Items.BEE_NEST, Items.BEEHIVE);
        addEquivalency(Items.BLACK_CONCRETE, Items.BLACK_CONCRETE_POWDER);
        addEquivalency(Items.BLUE_CONCRETE, Items.BLUE_CONCRETE_POWDER);
        addEquivalency(Items.BROWN_CONCRETE, Items.BROWN_CONCRETE_POWDER);
        addEquivalency(Items.CHARCOAL, Items.COAL);
        addEquivalency(Items.CYAN_CONCRETE, Items.CYAN_CONCRETE_POWDER);
        addEquivalency(Items.GRAY_CONCRETE, Items.GRAY_CONCRETE_POWDER);
        addEquivalency(Items.GREEN_CONCRETE, Items.GREEN_CONCRETE_POWDER);
        addEquivalency(Items.LIGHT_BLUE_CONCRETE, Items.LIGHT_BLUE_CONCRETE_POWDER);
        addEquivalency(Items.LIGHT_GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE_POWDER);
        addEquivalency(Items.LIME_CONCRETE, Items.LIME_CONCRETE_POWDER);
        addEquivalency(Items.MAGENTA_CONCRETE, Items.MAGENTA_CONCRETE_POWDER);
        addEquivalency(Items.ORANGE_CONCRETE, Items.ORANGE_CONCRETE_POWDER);
        addEquivalency(Items.PINK_CONCRETE, Items.PINK_CONCRETE_POWDER);
        addEquivalency(Items.PURPLE_CONCRETE, Items.PURPLE_CONCRETE_POWDER);
        addEquivalency(Items.RED_CONCRETE, Items.RED_CONCRETE_POWDER);
        addEquivalency(Items.WARPED_WART_BLOCK, Items.NETHER_WART_BLOCK);
        addEquivalency(Items.WHITE_CONCRETE, Items.WHITE_CONCRETE_POWDER);
        addEquivalency(Items.YELLOW_CONCRETE, Items.YELLOW_CONCRETE_POWDER);
    }

    private void addEquivalency(Item target, Item source)
    {
        equivalencies.put(
                Objects.requireNonNull(target.getRegistryName()).toString(),
                Objects.requireNonNull(source.getRegistryName()).toString());
    }
}
