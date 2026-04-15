package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Static helpers for canister slot lifecycle: building canister stacks
 * from pending data or source items, gasket metadata stripping, shape
 * computation, and item component export. Extracted from CanisterBlockEntity
 * to keep the entity focused on framework overrides and slot coordination.
 */
final class CanisterSlotLifecycle {

    /** Sentinel value indicating no occupied slot was found. */
    

    private CanisterSlotLifecycle() {}

    /**
     * Replaces gasket UUIDs with fresh ones and clears partner refs.
     * Preserves the physical gasket presence (non-null UUID = gasket installed)
     * while avoiding duplicate UUIDs across creative-mode copies.
     *
     * @param stack the canister item stack to strip
     */
    static void stripGasketMetadata(ItemStack stack) {
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        if (meta.topGasketId() != null || meta.bottomGasketId() != null) {
            CanisterItem.setMetadata(stack, meta.withFreshGasketIds());
        }
    }

    /**
     * Writes fluid content and metadata onto a canister stack if present and non-empty.
     *
     * @param stack   the canister item stack to populate
     * @param content the fluid content, or null to skip
     * @param meta    the canister metadata, or null to skip
     */
    static void applyFluidAndMetadata(ItemStack stack, @Nullable CanisterFluidContent content, @Nullable CanisterMetadata meta) {
        if (content != null && !content.isEmpty()) {
            CanisterItem.setFluidContent(stack, content);
        }
        if (meta != null && meta.hasData()) {
            CanisterItem.setMetadata(stack, meta);
        }
    }

    /**
     * Builds a canister stack from pending placement data.
     *
     * @param pendingContent the pending fluid content, or null
     * @param pendingMeta    the pending canister metadata, or null
     * @return a new canister item stack with the pending data applied
     */
    static ItemStack buildCanisterFromPending(@Nullable CanisterFluidContent pendingContent, @Nullable CanisterMetadata pendingMeta) {
        ItemStack canisterStack = new ItemStack(GooItems.CANISTER.get());
        applyFluidAndMetadata(canisterStack, pendingContent, pendingMeta);
        return canisterStack;
    }

    /**
     * Builds a canister stack by reading fluid data directly from a source ItemStack.
     * Bypasses pending fields so placement works before applyImplicitComponents runs.
     *
     * @param source the held canister ItemStack being placed
     * @return a new canister stack with copied fluid data
     */
    static ItemStack buildCanisterFromStack(ItemStack source) {
        ItemStack canisterStack = new ItemStack(GooItems.CANISTER.get());
        CanisterFluidContent content = CanisterItem.getFluidContent(source);
        CanisterMetadata meta = CanisterItem.getMetadata(source);
        applyFluidAndMetadata(canisterStack, content, meta);
        return canisterStack;
    }

    /**
     * Computes the union of occupied slot VoxelShapes.
     *
     * @param canisters  the canister stacks in each slot
     * @param maxSlots   the number of slots
     * @param centerSlot the center slot index (fallback for empty grids)
     * @return the composite shape of all occupied slots
     */
    static VoxelShape computeShape(List<ItemStack> canisters, int maxSlots, int centerSlot) {
        VoxelShape result = Shapes.empty();
        for (int i = 0; i < maxSlots; i++) {
            if (!canisters.get(i).isEmpty()) {
                result = Shapes.or(result, CanisterBlock.slotShape(i));
            }
        }
        return result.isEmpty() ? CanisterBlock.slotShape(centerSlot) : result;
    }

    /**
     * Returns the index of the sole occupied slot, or -1 if zero or multiple are occupied.
     *
     * @param canisters the canister stacks in each slot
     * @param maxSlots  the number of slots
     * @return the single occupied slot index, or -1
     */
    static int findSingleOccupiedSlot(List<ItemStack> canisters, int maxSlots) {
        int found = GooConstants.NO_SLOT;
        for (int i = 0; i < maxSlots; i++) {
            if (!canisters.get(i).isEmpty()) {
                if (found != GooConstants.NO_SLOT) { return GooConstants.NO_SLOT; }
                found = i;
            }
        }
        return found;
    }

    /**
     * Exports a single slot's goo contents and metadata to the item component builder.
     *
     * @param builder the component map builder
     * @param slot    the canister item stack in the slot
     */
    static void exportSlotComponents(DataComponentMap.Builder builder, ItemStack slot) {
        CanisterFluidContent content = CanisterItem.getFluidContent(slot);
        if (!content.isEmpty()) {
            builder.set(GooDataComponents.CANISTER_FLUID_CONTENT.get(), content);
        }
        CanisterMetadata meta = CanisterItem.getMetadata(slot);
        if (meta.hasData()) {
            builder.set(GooDataComponents.CANISTER_METADATA.get(), meta);
        }
    }
}
