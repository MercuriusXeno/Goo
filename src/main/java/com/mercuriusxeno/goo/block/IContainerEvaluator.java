package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * Evaluates container items (shulker boxes, bundles) by recursively
 * summing goo values and collecting items without goo for ejection.
 */
public interface IContainerEvaluator {

    /**
     * Returns true if the given stack is a container that should be
     * recursively evaluated rather than flat-looked-up.
     *
     * @param stack the item stack
     * @return the result
     */
    boolean isContainer(ItemStack stack);

    /**
     * Recursively evaluates a container's contents, summing goo values
     * and collecting items without goo values for ejection.
     *
     * @param containerId the container's registry ID
     * @param container   the container item stack
     * @param lookup      the goo value lookup for resolving item values
     * @return evaluation result with summed goo and eject list
     */
    ContainerEvaluation evaluate(Identifier containerId, ItemStack container, IGooValueLookup lookup);

    /**
     * Result of recursively evaluating a container item's contents.
     * Goo: summed goo values of all items (including nested containers).
     * Ejects: items with no goo value that should be spawned as entities.
     *
     * @param goo    the aggregated goo contents
     * @param ejects the list of items to eject
     */
    record ContainerEvaluation(GooContents goo, List<ItemStack> ejects) {

        /**
         * Empty evaluation with no goo and no ejects.
         */
        public static final ContainerEvaluation EMPTY =
                new ContainerEvaluation(GooContents.EMPTY, List.of());
    }
}
