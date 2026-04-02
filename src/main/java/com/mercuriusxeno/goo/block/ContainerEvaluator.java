package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates container items by recursively summing goo values
 * and collecting valueless items for ejection.
 */
public class ContainerEvaluator implements IContainerEvaluator {

    @Override
    public boolean isContainer(ItemStack stack) {
        return stack.has(DataComponents.CONTAINER)
            || stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    @Override
    public ContainerEvaluation evaluate(Identifier containerId, ItemStack container, IGooValueLookup lookup) {
        List<ItemStack> contents = collectContainerContents(container);
        GooContents goo = GooContents.EMPTY;
        List<ItemStack> ejects = new ArrayList<>();

        for (ItemStack item : contents) {
            if (isContainer(item)) {
                // Resolve ID at the recursion edge - adapter-boundary concern for nested containers.
                Identifier nestedId = BuiltInRegistries.ITEM.getKey(item.getItem());
                ContainerEvaluation nested = evaluate(nestedId, item, lookup);
                goo = goo.mergeWith(nested.goo());
                ejects.addAll(nested.ejects());
            } else {
                goo = evaluateItemOrEject(item, lookup, goo, ejects);
            }
        }

        goo = addShellValue(containerId, lookup, goo);
        return new ContainerEvaluation(goo, ejects);
    }

    /** Collects all non-empty item stacks from a container's data components. */
    private List<ItemStack> collectContainerContents(ItemStack container) {
        List<ItemStack> items = new ArrayList<>();
        ItemContainerContents containerData = container.get(DataComponents.CONTAINER);
        if (containerData != null) {
            containerData.nonEmptyItemCopyStream().forEach(items::add);
        }
        BundleContents bundleData = container.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleData != null) {
            bundleData.itemCopyStream().forEach(items::add);
        }
        return items;
    }

    /** Resolves ID from the item stack, then delegates to the lookup. */
    private GooContents evaluateItemOrEject(ItemStack item, IGooValueLookup lookup,
            GooContents goo, List<ItemStack> ejects) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        GooValue value = lookup.lookup(itemId);
        if (value != null && !value.isEmpty()) {
            return goo.mergeWith(value.toGooContents(item.getCount()));
        }
        ejects.add(item.copy());
        return goo;
    }

    /** Adds the container shell's own goo value using the pre-resolved ID. */
    private GooContents addShellValue(Identifier containerId, IGooValueLookup lookup,
            GooContents goo) {
        GooValue shellValue = lookup.lookup(containerId);
        if (shellValue != null && !shellValue.isEmpty()) {
            return goo.mergeWith(shellValue.toGooContents(1));
        }
        return goo;
    }
}
