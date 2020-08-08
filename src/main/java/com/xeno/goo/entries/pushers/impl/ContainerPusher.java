package com.xeno.goo.entries.pushers.impl;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.EntryHelper;
import com.xeno.goo.library.FileHelper;
import com.xeno.goo.library.GooEntry;
import com.xeno.goo.library.GooValue;
import com.xeno.goo.entries.EntryPhase;
import com.xeno.goo.entries.pushers.EntryPusher;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.xeno.goo.library.EntryHelper.*;

/**
 * Container items are itemstacks behind an itemstack that return when crafted.
 * Thus, containe*d* items are perceived to be the result of a mapping combined
 * with their containe*r*. Their mappings are stored as a loose equivalency because
 * we do not yet know what the value of their container is. Thus, they fall into
 * derived states, but their processing is special, because they need a baseline
 * that combines with a yet-unknown derived value, while setting the value of the contents.
 */
public class ContainerPusher extends EntryPusher
{
    private Map<String, GooEntry> unprocessedValues = new HashMap<>();
    public ContainerPusher(ServerWorld world)
    {
        super(EntryPhase.DERIVED, "goop-mappings-contained-items.json", world);
    }

    @Override
    protected boolean load()
    {
        unprocessedValues = FileHelper.readEntryFile(this.file());
        return unprocessedValues.size() > 0;
    }

    @Override
    protected void save()
    {
        FileHelper.writeEntryFile(this.file(), unprocessedValues);
    }

    @Override
    public void process()
    {
        unprocessedValues.forEach(this::processMapping);
    }

    @Override
    protected void clearProcessing()
    {
        unprocessedValues.clear();
    }

    @Override
    protected void seedDefaults()
    {
        addMapping(Items.LAVA_BUCKET, molten(Items.LAVA_BUCKET));
        addMapping(Items.MILK_BUCKET, faunal(1));
        addMapping(Items.WATER_BUCKET, aquatic(1));
    }

    private void addMapping(Item item, GooValue... values)
    {
        unprocessedValues.put(EntryHelper.name(item), new GooEntry(Arrays.asList(values)));
    }

    private void processMapping(String registryName, GooEntry unadjustedMapping)
    {
        ResourceLocation resource = ResourceLocation.tryCreate(registryName);
        if (resource == null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(resource);
        if (item == null) {
            return;
        }
        ItemStack container = item.getContainerItem(EntryHelper.getSingleton(item));
        if (container.isEmpty()) {
            return;
        }

        GooEntry mapping = GooMod.mappingHandler.get(container);
        if (mapping.isUnknown()) {
            return;
        }
        for(GooValue v : unadjustedMapping.values()) {
            // early abort if we don't know the value.
            mapping = mapping.add(v);
            if (mapping.isUnknown()) {
                return;
            }
        }

        values.put(registryName, mapping);
    }
}
