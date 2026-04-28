package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.IContainerEvaluator;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Item and goo insertion logic for CrucibleBlockEntity. Handles meltable items,
 * containers, direct goo contents, and PMI pool merging.
 */
final class CrucibleInsertion {

    private CrucibleInsertion() {
    }

    /**
     * Returns true if the item has a non-empty goo value.
     *
     * @param stack the item stack to check
     * @return true if the item can be inserted
     */
    static boolean canInsertItem(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return canInsertItem(id, Goo.GOO_VALUES);
    }

    /**
     * Testable seam: checks goo value via Identifier without registry coupling.
     *
     * @param itemId the item registry ID
     * @param lookup the goo value lookup
     * @return true if the item has goo value
     */
    static boolean canInsertItem(Identifier itemId, IGooValueLookup lookup) {
        GooValue value = lookup.lookup(itemId);
        return value != null && !value.isEmpty();
    }

    /**
     * Inserts a stack of items for melting into the shared PMI pool.
     *
     * @param be    the crucible block entity
     * @param stack the item stack to insert
     * @param count the number of items to insert
     * @return true if the item was inserted
     */
    static boolean insertItem(CrucibleBlockEntity be, ItemStack stack, int count) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return insertItem(be, id, count, Goo.GOO_VALUES);
    }

    /**
     * Testable seam: inserts goo by Identifier without registry coupling.
     *
     * @param be     the crucible block entity
     * @param itemId the item registry ID
     * @param count  the item count
     * @param lookup the goo value lookup
     * @return true if inserted
     */
    static boolean insertItem(CrucibleBlockEntity be, Identifier itemId, int count, IGooValueLookup lookup) {
        GooValue value = lookup.lookup(itemId);
        if (value == null || value.isEmpty()) {
            return false;
        }
        GooContents itemContents = value.toGooContents(count);
        mergeIntoPool(be, itemContents);
        be.syncToClients();
        return true;
    }

    /**
     * Merges goo contents into the PMI pool, creating a new PMI if needed.
     *
     * @param be       the crucible block entity
     * @param contents the goo contents
     */
    static void mergeIntoPool(CrucibleBlockEntity be, GooContents contents) {
        if (be.meltingItem.isEmpty()) {
            be.meltingItem = PartiallyMeltedItem.createWith(contents);
        } else {
            PartiallyMeltedItem.mergeContents(be.meltingItem, contents);
        }
    }

    /**
     * Inserts goo directly into the reservoir. Bypasses melting pipeline.
     *
     * @param be     the crucible block entity
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    static int insertGoo(CrucibleBlockEntity be, GooType type, int volume) {
        if (volume <= 0) {
            return 0;
        }
        int clamped = Math.min(volume, Integer.MAX_VALUE);
        int inserted = be.reservoir.insertGoo(type, clamped, false);
        be.syncToClients();
        return inserted;
    }

    /**
     * Inserts a container item's evaluated contents into the melt pool.
     *
     * @param be        the crucible block entity
     * @param container the container item stack
     * @return list of items to eject, or null if the container had no content
     */
    static @Nullable List<ItemStack> insertContainer(CrucibleBlockEntity be, ItemStack container) {
        Identifier containerId = BuiltInRegistries.ITEM.getKey(container.getItem());
        return insertContainer(be, containerId, container, Goo.GOO_VALUES);
    }

    /**
     * Testable seam: evaluates a container via Identifier without registry coupling.
     *
     * @param be          the crucible block entity
     * @param containerId the container registry ID
     * @param container   the container item stack
     * @param lookup      the goo value lookup
     * @return the eject list, or null
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull") // null = nothing happened; empty = eject nothing
    static @Nullable List<ItemStack> insertContainer(
            CrucibleBlockEntity be, Identifier containerId, ItemStack container, IGooValueLookup lookup) {
        IContainerEvaluator.ContainerEvaluation eval = be.containerEvaluator.evaluate(containerId, container, lookup);
        if (eval.goo().isEmpty() && eval.ejects().isEmpty()) {
            return null;
        }
        if (!eval.goo().isEmpty()) {
            mergeIntoPool(be, eval.goo());
        }
        be.syncToClients();
        return eval.ejects();
    }
}
