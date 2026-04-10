package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.CanisterSlotResolver;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.fluid.BucketOfGooItem;
import com.mercuriusxeno.goo.item.gasket.GasketRegionResolver;
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
import java.util.UUID;

/**
 * Static interaction handlers for CanisterBlock. Extracted to keep the
 * block class focused on framework overrides while handler logic remains
 * independently readable and testable.
 */
final class CanisterBlockHandlers {

    /** Error message prefix for unexpected interaction types reaching dispatch. */
    private static final String ERR_UNHANDLED = "Unhandled interaction: ";

    private CanisterBlockHandlers() {}

    /** Result of a successful goo extraction from a canister slot.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     */
    record ExtractedGoo(GooType type, long volume) {}

    /**
     * Dispatches a validated interaction to the appropriate handler method.
     *
     * @param interaction the classified interaction type
     * @param canister    the canister block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @param errTunerPass error message for TUNER_PASS reaching dispatch
     * @return the interaction result
     */
    static InteractionResult dispatch(
            GooInteractionType interaction, CanisterBlockEntity canister, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos,
            Level level, String errTunerPass) {
        if (interaction == GooInteractionType.TUNER_PASS) {
            throw new IllegalStateException(errTunerPass);
        }
        return dispatchNonTuner(interaction, canister, stack, player, hand, hitResult);
    }

    /**
     * Dispatches a non-tuner interaction to the matching canister handler.
     *
     * @param interaction the classified interaction type (must not be TUNER_PASS)
     * @param canister    the canister block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @return the interaction result
     */
    private static InteractionResult dispatchNonTuner(
            GooInteractionType interaction, CanisterBlockEntity canister, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        return switch (interaction) {
            case CANISTER_INSERT  -> handleCanisterInsert(canister, hitResult, stack, player);
            case BLOB_INSERT      -> handleBlobInsert(canister, hitResult, stack, player);
            case BUCKET_INSERT    -> handleBucketInsert(canister, hitResult, stack, player, hand);
            case BUCKET_EXTRACT   -> handleBucketExtract(canister, hitResult, stack, player);
            default -> throw new IllegalStateException(ERR_UNHANDLED + interaction);
        };
    }

    /**
     * Removes the gasket on the targeted face of a canister slot, if installed.
     *
     * @param canister  the canister block entity
     * @param slot      the targeted slot index
     * @param hitResult the ray trace hit result
     * @return SUCCESS if a gasket was removed, PASS otherwise
     */
    static InteractionResult handleSlotGasketRemove(
            CanisterBlockEntity canister, int slot, BlockHitResult hitResult) {
        var pos = canister.getBlockPos();
        GasketRole role = resolveSlotGasketRole(hitResult, pos);
        CanisterMetadata meta = canister.getSlotMetadata(slot);
        UUID gasketId = gasketIdForRole(meta, role);
        if (gasketId == null) { return InteractionResult.PASS; }

        GasketInstallation.popGasket(canister.getLevel(), pos, gasketId);
        canister.setSlotMetadata(slot, clearGasketForRole(meta, role));
        return InteractionResult.SUCCESS;
    }

    /**
     * Resolves which gasket role (top/bottom) was targeted by the hit.
     * @param hitResult the block hit result containing the exact hit location
     * @param pos       the block position used to compute local Y offset
     * @return the gasket role (receiver or sender) for the hit location
     */
    private static GasketRole resolveSlotGasketRole(BlockHitResult hitResult, BlockPos pos) {
        double localY = hitResult.getLocation().y - pos.getY();
        return GasketRegionResolver.resolveCanisterSlotRole(localY, 0.0, 1.0);
    }

    /**
     * Returns the gasket UUID for the given role on this metadata.
     * @param meta the canister slot metadata
     * @param role which gasket role to look up
     * @return the gasket UUID, or null if no gasket is installed for that role
     */
    private static @Nullable UUID gasketIdForRole(CanisterMetadata meta, GasketRole role) {
        return role == GasketRole.RECEIVER ? meta.topGasketId() : meta.bottomGasketId();
    }

    /**
     * Clears the gasket for the given role, returning updated metadata.
     * @param meta the canister slot metadata to modify
     * @param role which gasket role to clear
     * @return a new metadata instance with the specified gasket removed
     */
    private static CanisterMetadata clearGasketForRole(CanisterMetadata meta, GasketRole role) {
        return role == GasketRole.RECEIVER ? meta.withoutTopGasket() : meta.withoutBottomGasket();
    }

    /**
     * Inserts a canister item into the best empty slot resolved from the hit result.
     *
     * @param canister  the canister block entity
     * @param hitResult the ray trace hit result
     * @param stack     the canister item stack
     * @param player    the interacting player
     * @return SUCCESS if inserted, PASS otherwise
     */
    static InteractionResult handleCanisterInsert(
            CanisterBlockEntity canister, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        if (!tryInsertCanister(canister, hitResult, stack, player.isCreative())) {
            return InteractionResult.PASS;
        }
        stack.consume(1, player);
        InteractionCooldown.markInteraction(player.getUUID(), canister.getLevel().getGameTime());
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to insert a canister item into the resolved slot.
     *
     * @param canister      the canister block entity
     * @param hitResult     the block hit result for slot targeting
     * @param stack         the canister item stack
     * @param stripGaskets  true to clear gasket UUIDs (creative-mode duplication)
     * @return true if the canister was inserted
     */
    private static boolean tryInsertCanister(
            CanisterBlockEntity canister, BlockHitResult hitResult,
            ItemStack stack, boolean stripGaskets) {
        int slot = CanisterSlotResolver.resolveInsertionSlot(
                hitResult.getLocation(), canister.getBlockPos(), hitResult.getDirection(), canister);
        return slot >= 0 && canister.insertCanister(slot, stack, stripGaskets);
    }

    /**
     * Removes a canister from the targeted slot, dropping the block if it was the last.
     *
     * @param canister the canister block entity
     * @param slot     the targeted slot index
     * @param player   the interacting player
     * @return SUCCESS if removed, PASS otherwise
     */
    static InteractionResult handleCanisterRemove(
            CanisterBlockEntity canister, int slot, Player player) {
        ItemStack removed = canister.removeCanister(slot);
        if (removed.isEmpty()) { return InteractionResult.PASS; }
        PlayerUtils.addOrDrop(player, removed);
        onCanisterRemoved(canister, player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Plays feedback, marks cooldown, and removes the block if all slots are empty.
     * @param canister the canister block entity that lost a slot
     * @param player the player who removed the canister
     */
    private static void onCanisterRemoved(CanisterBlockEntity canister, Player player) {
        var level = canister.getLevel();
        var pos = canister.getBlockPos();
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        InteractionCooldown.markInteraction(player.getUUID(), level.getGameTime());
        removeBlockIfEmpty(level, canister, pos);
    }

    /**
     * Removes the canister block from the world if no canisters remain in any slot.
     *
     * @param level    the current level
     * @param canister the canister block entity
     * @param pos      the block position
     */
    private static void removeBlockIfEmpty(Level level, CanisterBlockEntity canister, BlockPos pos) {
        if (!canister.containerState().hasAnyCanister()) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Inserts goo from a blob or omniblob item into the first accepting slot.
     *
     * @param canister  the canister block entity
     * @param hitResult the ray trace hit result
     * @param stack     the blob item stack
     * @param player    the interacting player
     * @return SUCCESS if goo was inserted, PASS otherwise
     */
    static InteractionResult handleBlobInsert(
            CanisterBlockEntity canister, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        var pos = canister.getBlockPos();
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) { return InteractionResult.PASS; }
        long accepted = tryInsertBlobGoo(canister, CanisterBlock.hitSlot(hitResult, pos), type, BlobStacks.volumeOf(stack));
        if (accepted <= 0) { return InteractionResult.PASS; }

        BlobStacks.deplete(stack, accepted, player);
        canister.getLevel().playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to pour goo from a blob/omniblob into an accepting slot.
     *
     * @param canister the canister block entity
     * @param hitSlot  the slot the player targeted, or -1
     * @param type     the goo type to insert
     * @param volume   the volume in microblobs to insert
     * @return accepted volume in microblobs, or 0 if nothing was inserted
     */
    private static long tryInsertBlobGoo(
            CanisterBlockEntity canister, int hitSlot,
            GooType type, long volume) {
        int slot = GooBlockInteraction.findSlot(hitSlot, CanisterBlockEntity.MAX_SLOTS, canister::canAccept);
        if (slot < 0) { return 0; }
        return canister.insertGoo(slot, type, volume);
    }

    /**
     * Pours goo from a bucket of goo into matching canister slots.
     *
     * @param canister  the canister block entity
     * @param hitResult the ray trace hit result
     * @param stack     the bucket item stack
     * @param player    the interacting player
     * @param hand      the hand used
     * @return SUCCESS if goo was inserted, PASS otherwise
     */
    static InteractionResult handleBucketInsert(
            CanisterBlockEntity canister, BlockHitResult hitResult,
            ItemStack stack, Player player, InteractionHand hand) {
        var pos = canister.getBlockPos();
        GooContents updatedContents = tryPourBucket(
                canister, CanisterBlock.hitSlot(hitResult, pos), BucketOfGooItem.getContents(stack));
        if (updatedContents == null) { return InteractionResult.PASS; }

        BucketOfGooItem.setOrRevert(stack, updatedContents, player, hand);
        canister.getLevel().playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to pour a bucket's goo contents into accepting canister slots.
     *
     * @param canister  the canister block entity
     * @param hitSlot   the slot the player targeted, or -1
     * @param bucketGoo the bucket's goo contents
     * @return updated goo contents after pouring, or null if nothing was inserted
     */
    private static @Nullable GooContents tryPourBucket(
            CanisterBlockEntity canister, int hitSlot, GooContents bucketGoo) {
        if (bucketGoo.isEmpty()) { return null; }
        GooContents remaining = pourAllEntries(canister, hitSlot, bucketGoo);
        return remaining.equals(bucketGoo) ? null : remaining;
    }

    /**
     * Attempts to pour each goo entry into the canister, returning whatever remains.
     * @param canister the canister block entity to pour into
     * @param hitSlot the slot the player targeted, or -1
     * @param bucketGoo the bucket's goo contents to pour
     * @return goo contents remaining after pouring
     */
    private static GooContents pourAllEntries(
            CanisterBlockEntity canister, int hitSlot, GooContents bucketGoo) {
        GooContents remaining = bucketGoo;
        for (var entry : bucketGoo.getAll().entrySet()) {
            long accepted = pourSingleEntry(canister, hitSlot, entry.getKey(), entry.getValue());
            if (accepted > 0) {
                remaining = remaining.withRemoved(entry.getKey(), accepted);
            }
        }
        return remaining;
    }

    /**
     * Pours a single goo type from the bucket into the best accepting slot.
     * @param canister the canister block entity
     * @param hitSlot  the slot targeted by the hit
     * @param type     the goo type being poured
     * @param volume   the amount of goo to pour (mB)
     * @return the amount of goo actually inserted (mB)
     */
    private static long pourSingleEntry(
            CanisterBlockEntity canister, int hitSlot, GooType type, long volume) {
        int slot = GooBlockInteraction.findSlot(hitSlot, CanisterBlockEntity.MAX_SLOTS, canister::canAccept);
        if (slot < 0) { return 0; }
        return canister.insertGoo(slot, type, volume);
    }

    /**
     * Extracts goo from the first non-empty slot into an empty bucket.
     *
     * @param canister  the canister block entity
     * @param hitResult the ray trace hit result
     * @param stack     the empty bucket stack
     * @param player    the interacting player
     * @return SUCCESS if goo was extracted, PASS otherwise
     */
    static InteractionResult handleBucketExtract(
            CanisterBlockEntity canister, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        var pos = canister.getBlockPos();
        ExtractedGoo extracted = tryExtractGoo(canister, CanisterBlock.hitSlot(hitResult, pos));
        if (extracted == null) { return InteractionResult.PASS; }

        ItemStack filledBucket = BucketOfGooItem.createWithGoo(extracted.type(), extracted.volume());
        stack.shrink(1);
        PlayerUtils.addOrDrop(player, filledBucket);
        canister.getLevel().playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to extract goo from the targeted or first filled canister slot.
     *
     * @param canister the canister block entity
     * @param hitSlot  the slot the player targeted, or -1
     * @return extracted goo type and volume, or null if nothing could be extracted
     */
    private static @Nullable ExtractedGoo tryExtractGoo(
            CanisterBlockEntity canister, int hitSlot) {
        int slot = GooBlockInteraction.findSlot(hitSlot, CanisterBlockEntity.MAX_SLOTS, i -> !canister.getSlotGooContents(i).isEmpty());
        if (slot < 0) { return null; }

        GooContents slotGoo = canister.getSlotGooContents(slot);
        GooType type = slotGoo.largestType();
        if (type == null) { return null; }
        long extracted = canister.extractGoo(slot, type, slotGoo.getVolume(type));
        if (extracted <= 0) { return null; }
        return new ExtractedGoo(type, extracted);
    }
}
