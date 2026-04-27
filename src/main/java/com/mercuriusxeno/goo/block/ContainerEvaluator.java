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

    /**
     * Checks whether the stack has container or bundle data components.
     *
     * @param stack the item stack to test
     * @return true if the stack is a recognized container type
     */
    @Override
    public boolean isContainer(ItemStack stack) {
        return stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    /**
     * Recursively evaluates a container's contents, summing goo values for
     * items with known mappings and collecting valueless items for ejection.
     *
     * @param containerId the registry ID of the container item (for shell value lookup)
     * @param container   the container item stack to evaluate
     * @param lookup      goo value lookup for resolving item values
     * @return evaluation result containing aggregated goo and ejected items
     */
    @Override
    public ContainerEvaluation evaluate(Identifier containerId, ItemStack container, IGooValueLookup lookup) {
        List<ItemStack> contents = collectContainerContents(container);
        GooContents goo = GooContents.EMPTY;
        List<ItemStack> ejects = new ArrayList<>();
        for (ItemStack item : contents) {
            goo = accumulateItem(item, lookup, goo, ejects);
        }
        goo = addShellValue(containerId, lookup, goo);
        return new ContainerEvaluation(goo, ejects);
    }

    /**
     * Processes one item: recurse into nested containers or evaluate directly.
     *
     * @param item   the item stack
     * @param lookup goo value lookup
     * @param goo    running goo total
     * @param ejects accumulator for ejected items
     * @return the updated goo total
     */
    private GooContents accumulateItem(ItemStack item, IGooValueLookup lookup,
                                       GooContents goo, List<ItemStack> ejects) {
        if (isContainer(item)) {
            Identifier nestedId = BuiltInRegistries.ITEM.getKey(item.getItem());
            ContainerEvaluation nested = evaluate(nestedId, item, lookup);
            ejects.addAll(nested.ejects());
            return goo.mergeWith(nested.goo());
        }
        return evaluateItemOrEject(item, lookup, goo, ejects);
    }

    /**
     * Collects all non-empty item stacks from a container's data components.
     *
     * @param container the container item stack
     * @return the list
     */
    private List<ItemStack> collectContainerContents(ItemStack container) {
        List<ItemStack> items = new ArrayList<>();
        collectFromComponent(container, DataComponents.CONTAINER, items);
        collectFromBundle(container, items);
        return items;
    }

    /**
     * Adds non-empty items from a CONTAINER data component.
     *
     * @param container the container item stack
     * @param component the data component key
     * @param items     accumulator for collected items
     */
    private void collectFromComponent(ItemStack container, net.minecraft.core.component.DataComponentType<ItemContainerContents> component, List<ItemStack> items) {
        ItemContainerContents data = container.get(component);
        if (data != null) {
            data.nonEmptyItemCopyStream().forEach(items::add);
        }
    }

    /**
     * Adds items from a BUNDLE_CONTENTS data component.
     *
     * @param container the container item stack
     * @param items     accumulator for collected items
     */
    private void collectFromBundle(ItemStack container, List<ItemStack> items) {
        BundleContents data = container.get(DataComponents.BUNDLE_CONTENTS);
        if (data != null) {
            data.itemCopyStream().forEach(items::add);
        }
    }

    /**
     * Resolves ID from the item stack, then delegates to the lookup.
     *
     * @param item   the item stack to evaluate
     * @param lookup the goo value lookup
     * @param goo    the aggregated goo contents
     * @param ejects the list of items to eject
     * @return the goo contents
     */
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

    /**
     * Adds the container shell's own goo value using the pre-resolved ID.
     *
     * @param containerId the container registry ID
     * @param lookup      the goo value lookup
     * @param goo         the aggregated goo contents
     * @return the goo contents
     */
    private GooContents addShellValue(Identifier containerId, IGooValueLookup lookup,
                                      GooContents goo) {
        GooValue shellValue = lookup.lookup(containerId);
        if (shellValue != null && !shellValue.isEmpty()) {
            return goo.mergeWith(shellValue.toGooContents(1));
        }
        return goo;
    }
}
