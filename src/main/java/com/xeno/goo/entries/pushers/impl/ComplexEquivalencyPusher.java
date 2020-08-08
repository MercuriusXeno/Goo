package com.xeno.goo.entries.pushers.impl;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.*;
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
 * Composites are the polar extreme of forced equivalencies, where inputs are unknown
 * and you want to work backwards from an output that has a known value, or some
 * other composite value is proving difficult due to special recipe handling (like shulker boxes, et al)
 * Composites, and complex equivalencies, let you manually invoke that a "recipe" exists, that is
 * essentially invisible to the recipe pusher, or not in the format that it knows how to work with.
 */
public class ComplexEquivalencyPusher extends EntryPusher
{
    public Map<String, ComplexEntry> equivalencies = new TreeMap<>(stringLexicographicalComparator);
    public ComplexEquivalencyPusher(ServerWorld world) {
        super(EntryPhase.DEFERRED, "goo-mappings-complex-equivalency.json", world);
    }

    @Override
    protected boolean load()
    {
        equivalencies = FileHelper.readCompositeFile(this.file());
        return equivalencies.size() > 0;
    }

    @Override
    protected void save()
    {
        FileHelper.writeCompositeFile(this.file(), equivalencies);
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

    private void processEntry(String target, ComplexEntry source)
    {
        GooEntry result = GooEntry.EMPTY;
        for(StackComplex composite : source.composites()) {
            if (composite.operator == CompositeOperator.ADD) {
                result = result.add(GooMod.mappingHandler.get(composite.stack.resource).multiply(composite.stack.count));
            }
            if (composite.operator == CompositeOperator.SUBTRACT) {
                result = result.subtract(GooMod.mappingHandler.get(composite.stack.resource).multiply(composite.stack.count));
            }
        }
        if (result.isUnusable()) {
            return;
        }
        values.put(target, result);
    }


    @Override
    protected void seedDefaults()
    {
        addEquivalency(Items.HONEY_BOTTLE, new ComplexEntry(
            new StackComplex(Items.GLASS_BOTTLE),
            new StackComplex(Items.SUGAR, 3)
        ));
    }

    private void addEquivalency(Item target, ComplexEntry source)
    {
        equivalencies.put(Objects.requireNonNull(target.getRegistryName()).toString(), source);
    }
}
