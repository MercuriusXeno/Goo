package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.fluid.BucketOfGooItem;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Static interaction handlers extracted from HubBlock to keep the framework
 * override surface under the TooManyMethods threshold.
 */
final class HubBlockHandlers {

    /** Error message for TUNER_PASS reaching dispatch. */
    private static final String ERR_TUNER_PASS = "TUNER_PASS handled in validate";

    /** Error message prefix for unexpected interaction types reaching dispatch. */
    private static final String ERR_UNHANDLED = "Unhandled interaction: ";

    private HubBlockHandlers() {}

    /**
     * Resolves the canister stack under the player's crosshair, if any.
     *
     * @param hub the hub block entity
     * @param pos the block position
     * @return a copy of the targeted canister, or empty if none targeted
     */
    static ItemStack resolveTargetedCanister(HubBlockEntity hub, BlockPos pos) {
        var hit = ISidedProxy.get().getCrosshairHit();
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
            return ItemStack.EMPTY;
        }
        int slot = HubBlock.hitSlot(blockHit, pos);
        if (slot < 0) { return ItemStack.EMPTY; }
        ItemStack canister = hub.getCanister(slot);
        return canister.isEmpty() ? ItemStack.EMPTY : canister.copy();
    }

    /**
     * Dispatches a validated interaction to the appropriate handler method.
     *
     * @param interaction the classified interaction type
     * @param hub         the hub block entity
     * @param stack       the item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    static InteractionResult dispatchHub(
            GooInteractionType interaction, HubBlockEntity hub, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        if (interaction == GooInteractionType.TUNER_PASS) {
            throw new IllegalStateException(ERR_TUNER_PASS);
        }
        return dispatchNonTuner(interaction, hub, stack, player, hand, hitResult);
    }

    /**
     * Dispatches a non-tuner interaction to the matching hub handler.
     *
     * @param interaction the classified interaction type (must not be TUNER_PASS)
     * @param hub         the hub block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @return the interaction result
     */
    private static InteractionResult dispatchNonTuner(
            GooInteractionType interaction, HubBlockEntity hub, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        return switch (interaction) {
            case CANISTER_INSERT  -> handleCanisterInsert(hub, hitResult, stack, player);
            case BLOB_INSERT      -> handleBlobInsert(hub, hitResult, stack, player);
            case BUCKET_INSERT    -> handleBucketInsert(hub, hitResult, stack, player, hand);
            case BUCKET_EXTRACT   -> handleBucketExtract(hub, hitResult, stack, player);
            default -> throw new IllegalStateException(ERR_UNHANDLED + interaction);
        };
    }

    /**
     * Removes the intake gasket from the hub and drops it.
     *
     * @param level the current level
     * @param pos   the block position
     * @param hub   the hub block entity
     * @return SUCCESS after removing the gasket
     */
    static InteractionResult removeGasket(Level level, BlockPos pos, HubBlockEntity hub) {
        GasketInstallation.popGasket(level, pos, hub.getGasketId(GasketRole.RECEIVER));
        hub.clearGasket(GasketRole.RECEIVER);
        return InteractionResult.SUCCESS;
    }

    /**
     * Removes the canister from the targeted hub slot and gives it to the player.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param pos       the block position
     * @param player    the interacting player
     * @param level     the current level
     * @return SUCCESS if a canister was removed, PASS otherwise
     */
    static InteractionResult removeCanister(
            HubBlockEntity hub, BlockHitResult hitResult, BlockPos pos, Player player, Level level) {
        int slot = HubBlock.hitSlot(hitResult, pos);
        if (slot < 0) { return InteractionResult.PASS; }

        ItemStack removed = HubSlotLifecycle.removeCanister(hub, slot);
        if (removed.isEmpty()) { return InteractionResult.PASS; }

        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        InteractionCooldown.markInteraction(player.getUUID(), level.getGameTime());
        return InteractionResult.SUCCESS;
    }

    // --- Item insertion handlers ---

    /**
     * Inserts a canister item into the best slot resolved from the hit result.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleCanisterInsert(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        if (!tryInsertCanister(hub, hitResult, stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        stack.consume(1, player);
        InteractionCooldown.markInteraction(player.getUUID(), hub.getLevel().getGameTime());
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to insert a canister, preferring the hit slot then falling back to first empty.
     *
     * @param hub       the hub block entity
     * @param hitResult the block hit result for slot targeting
     * @param stack     the canister item stack
     * @return true if the canister was inserted
     */
    private static boolean tryInsertCanister(
            HubBlockEntity hub, BlockHitResult hitResult, ItemStack stack) {
        int slot = HubBlock.hitSlot(hitResult, hub.getBlockPos());
        return (slot >= 0 && HubSlotLifecycle.insertCanister(hub, slot, stack.copy()))
                || HubSlotLifecycle.insertCanister(hub, stack.copy());
    }

    /**
     * Inserts goo from a blob or omniblob item into the first accepting hub slot.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleBlobInsert(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) { return InteractionResult.PASS; }
        long volume = BlobStacks.volumeOf(stack);

        long accepted = insertBlobGoo(hub, hitResult, type, volume);
        if (accepted <= 0) { return InteractionResult.PASS; }

        BlobStacks.deplete(stack, accepted, player);
        hub.getLevel().playSound(null, hub.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Finds the best slot and inserts blob goo into it.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result for slot targeting
     * @param type      the goo type to insert
     * @param volume    the volume of goo in microblobs
     * @return the volume accepted, or 0 if no slot accepted
     */
    private static long insertBlobGoo(HubBlockEntity hub, BlockHitResult hitResult, GooType type, long volume) {
        var pos = hub.getBlockPos();
        int slot = GooBlockInteraction.findSlot(
                HubBlock.hitSlot(hitResult, pos), HubBlockEntity.MAX_CANISTERS, hub::canAccept);
        if (slot < 0) { return 0; }
        return hub.insertGoo(slot, type, volume);
    }

    // --- Bucket handlers ---

    /**
     * Pours goo from a bucket of goo into matching hub canister slots.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hand      the hand used
     * @return the interaction result
     */
    private static InteractionResult handleBucketInsert(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player, InteractionHand hand) {
        GooContents bucketGoo = BucketOfGooItem.getContents(stack);
        if (bucketGoo.isEmpty()) { return InteractionResult.PASS; }

        GooContents remainder = pourBucketIntoHub(hub, hitResult, bucketGoo);
        if (remainder == bucketGoo) { return InteractionResult.PASS; }

        BucketOfGooItem.setOrRevert(stack, remainder, player, hand);
        hub.getLevel().playSound(null, hub.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Pours each goo type from the bucket into matching hub slots.
     * Returns the updated contents after insertions, or the original if nothing was inserted.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result for slot targeting
     * @param bucketGoo the bucket's goo contents
     * @return updated contents after pour, or the original instance if nothing was inserted
     */
    private static GooContents pourBucketIntoHub(HubBlockEntity hub, BlockHitResult hitResult, GooContents bucketGoo) {
        int hitSlotIdx = HubBlock.hitSlot(hitResult, hub.getBlockPos());
        GooContents result = bucketGoo;
        for (var entry : bucketGoo.getAll().entrySet()) {
            result = pourSingleType(hub, hitSlotIdx, entry.getKey(), entry.getValue(), result);
        }
        return result;
    }

    /**
     * Attempts to pour a single goo type into the hub, updating the remaining contents.
     *
     * @param hub        the hub block entity
     * @param hitSlotIdx the preferred slot from the hit result
     * @param type       the goo type to pour
     * @param volume     the volume available
     * @param contents   the current remaining bucket contents
     * @return updated contents with any accepted volume removed
     */
    private static GooContents pourSingleType(
            HubBlockEntity hub, int hitSlotIdx, GooType type, long volume, GooContents contents) {
        int slot = GooBlockInteraction.findSlot(
                hitSlotIdx, HubBlockEntity.MAX_CANISTERS, hub::canAccept);
        if (slot < 0) { return contents; }
        long accepted = hub.insertGoo(slot, type, volume);
        if (accepted <= 0) { return contents; }
        return contents.withRemoved(type, accepted);
    }

    /**
     * Extracts goo from the first non-empty hub slot into an empty bucket.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleBucketExtract(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        int slot = findNonEmptySlot(hub, hitResult);
        if (slot < 0) { return InteractionResult.PASS; }

        ItemStack filledBucket = extractLargestGooType(hub, slot);
        if (filledBucket == null) { return InteractionResult.PASS; }

        stack.shrink(1);
        PlayerUtils.addOrDrop(player, filledBucket);
        hub.getLevel().playSound(null, hub.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Finds the first non-empty canister slot using hit-slot preference.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result for slot targeting
     * @return slot index, or -1 if no non-empty slot found
     */
    private static int findNonEmptySlot(HubBlockEntity hub, BlockHitResult hitResult) {
        var pos = hub.getBlockPos();
        return GooBlockInteraction.findSlot(HubBlock.hitSlot(hitResult, pos),
                HubBlockEntity.MAX_CANISTERS,
                i -> !hub.getSlotGooContents(i).isEmpty());
    }

    /**
     * Extracts the largest goo type from a hub slot into a filled bucket.
     *
     * @param hub  the hub block entity
     * @param slot the slot index to extract from
     * @return a filled bucket item stack, or null if extraction failed
     */
    @Nullable
    private static ItemStack extractLargestGooType(HubBlockEntity hub, int slot) {
        GooContents slotGoo = hub.getSlotGooContents(slot);
        GooType type = slotGoo.largestType();
        if (type == null) { return null; }
        long extracted = hub.extractGoo(slot, type, slotGoo.getVolume(type));
        if (extracted <= 0) { return null; }
        return BucketOfGooItem.createWithGoo(type, extracted);
    }
}
