package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Canister fluid-transfer handlers for vat interactions.
 * Extracted from VatInteractionHandler to keep method counts under threshold.
 */
final class VatFluidInteraction {

    private VatFluidInteraction() {
    }

    /**
     * Routes canister interactions with the vat.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result, or null if no match
     */
    @Nullable
    static InteractionResult dispatchFluidContainers(
            VatBlockEntity vat, ItemStack stack,
            Player player, InteractionHand hand) {
        if (stack.getItem() instanceof CanisterItem) {
            return handleCanisterInteraction(vat, stack);
        }
        return null;
    }

    // --- Canister handlers ---

    /**
     * Routes canister-vat interaction: dump if canister has fluid, drain if empty.
     *
     * @param vat   the vat block entity
     * @param stack the item stack
     * @return the interaction result
     */
    private static InteractionResult handleCanisterInteraction(VatBlockEntity vat, ItemStack stack) {
        CanisterFluidContent canisterContent = CanisterItem.getFluidContent(stack);
        if (!canisterContent.isEmpty()) {
            return handleCanisterDump(vat, stack, canisterContent);
        }
        return handleCanisterDrain(vat, stack);
    }

    /**
     * Dumps the canister's fluid into the vat, removing the accepted amount.
     *
     * @param vat     the vat block entity
     * @param stack   the item stack
     * @param content the canister fluid content
     * @return the interaction result
     */
    private static InteractionResult handleCanisterDump(
            VatBlockEntity vat, ItemStack stack, CanisterFluidContent content) {
        if (!vat.canAccept()) {
            return InteractionResult.PASS;
        }
        GooType type = content.getGooType();
        if (type == null) {
            return InteractionResult.PASS;
        }
        int accepted = vat.insertGoo(type, content.amount());
        if (accepted <= 0) {
            return InteractionResult.PASS;
        }
        CanisterItem.removeGoo(stack, type, accepted);
        return InteractionResult.SUCCESS;
    }

    /**
     * Drains the vat's dominant type into an empty canister, up to canister capacity.
     *
     * @param vat   the vat block entity
     * @param stack the item stack
     * @return the result
     */
    private static InteractionResult handleCanisterDrain(VatBlockEntity vat, ItemStack stack) {
        GooType dominant = extractableDominant(vat);
        if (dominant == null) {
            return InteractionResult.PASS;
        }
        int extracted = drainDominantForCanister(vat, stack, dominant);
        if (extracted <= 0) {
            return InteractionResult.PASS;
        }
        CanisterItem.addGoo(stack, dominant, extracted);
        return InteractionResult.SUCCESS;
    }

    /**
     * Extracts as much of the dominant type as the canister can hold.
     *
     * @param vat      the vat block entity
     * @param stack    the canister item stack
     * @param dominant the goo type to extract
     * @return the volume actually extracted in microblobs
     */
    private static int drainDominantForCanister(
            VatBlockEntity vat, ItemStack stack, GooType dominant) {
        int space = canisterRemainingSpace(stack);
        if (space <= 0) {
            return 0;
        }
        int available = vat.getContents().getVolume(dominant);
        return vat.extractGoo(dominant, Math.min(available, space));
    }

    // --- Helpers ---

    /**
     * Returns the dominant goo type if the vat is non-empty, or null otherwise.
     *
     * @param vat the vat block entity
     * @return the dominant goo type, or null if the vat is empty
     */
    @Nullable
    static GooType extractableDominant(VatBlockEntity vat) {
        if (vat.isEmpty()) {
            return null;
        }
        return vat.getDominantType();
    }

    /**
     * Returns the remaining fluid capacity of the canister stack.
     *
     * @param stack the canister item stack
     * @return remaining capacity in microblobs
     */
    private static int canisterRemainingSpace(ItemStack stack) {
        int capacity = ContainerCapacity.canisterCapacity(
                GooEnchantments.getCompressionLevel(stack));
        return capacity - CanisterItem.getFluidContent(stack).amount();
    }
}
