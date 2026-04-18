package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.fluid.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.CanisterSlotResolver;
import com.mercuriusxeno.goo.item.GooInteractionType;
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
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
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

    /**
     * Picks up the targeted canister, removing it from the grid and giving
     * it to the player. Removes the block if no canisters remain.
     *
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return SUCCESS if a canister was picked up, PASS otherwise
     */
    static InteractionResult handleCanisterPickup(
            Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        int slot = CanisterBlock.hitSlot(hitResult, pos);
        if (slot < 0) { return InteractionResult.PASS; }
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        if (!(level.getBlockEntity(pos) instanceof CanisterBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (be.getCanister(slot).isEmpty()) { return InteractionResult.PASS; }
        return pickupFromSlot(level, pos, player, be, slot);
    }

    /**
     * Extracts a canister from the slot and gives it to the player.
     * If this was the last canister, removes the block in one step
     * (no intermediate sync) to avoid client desync from out-of-order
     * block entity data and block state packets.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param be     the canister block entity
     * @param slot   the slot to pick up from
     * @return SUCCESS
     */
    private static InteractionResult pickupFromSlot(
            Level level, BlockPos pos, Player player, CanisterBlockEntity be, int slot) {
        boolean lastCanister = countOccupied(be) == 1;
        if (lastCanister) {
            // Take the stack directly and remove the block in one step.
            // Avoids markDirtyAndSync sending a stale block entity packet
            // that can arrive after the block state change to air, corrupting
            // the client's chunk data at this position.
            ItemStack taken = be.containerState().getCanister(slot).copy();
            PlayerUtils.addOrDrop(player, taken);
            level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.removeBlock(pos, false);
        } else {
            ItemStack removed = be.removeCanister(slot);
            PlayerUtils.addOrDrop(player, removed);
            level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Counts occupied slots in the canister block entity.
     *
     * @param be the canister block entity
     * @return the number of non-empty slots
     */
    private static int countOccupied(CanisterBlockEntity be) {
        int count = 0;
        for (int i = 0; i < CanisterBlock.SLOT_COUNT; i++) {
            if (!be.getCanister(i).isEmpty()) { count++; }
        }
        return count;
    }

    /**
     * Attempts fluid container interaction (bucket fill/drain) on the targeted slot.
     * Canisters accept any fluid. Returns SUCCESS if fluid was transferred, null
     * if the item is not a fluid container or the targeted slot has no handler.
     *
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hand      the hand holding the fluid container
     * @param hitResult the ray trace hit result
     * @return SUCCESS if fluid transferred, null if not applicable
     */
    static @Nullable InteractionResult tryFluidInteraction(
            Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CanisterBlockEntity canister)) { return null; }
        int slot = CanisterBlock.hitSlot(hitResult, pos);
        if (slot < 0) { return null; }
        CanisterSlotFluidHandler handler = canister.containerState().getSlotFluidHandler(slot);
        if (handler == null) { return null; }
        if (FluidUtil.interactWithFluidHandler(player, hand, pos, handler)) {
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * Dispatches a validated interaction to the appropriate handler method.
     *
     * @param interaction the classified interaction type
     * @param canister    the canister block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hand        the hand used (required by Dispatcher interface)
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
        return dispatchNonTuner(interaction, canister, stack, player, hitResult);
    }

    /**
     * Dispatches a non-tuner interaction to the matching canister handler.
     *
     * @param interaction the classified interaction type (must not be TUNER_PASS)
     * @param canister    the canister block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hitResult   the ray trace hit result
     * @return the interaction result
     */
    private static InteractionResult dispatchNonTuner(
            GooInteractionType interaction, CanisterBlockEntity canister, ItemStack stack,
            Player player, BlockHitResult hitResult) {
        return switch (interaction) {
            case CANISTER_INSERT  -> handleCanisterInsert(canister, hitResult, stack, player);
            case BLOB_INSERT      -> handleBlobInsert(canister, hitResult, stack, player);
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
        canister.getLevel().playSound(null, canister.getBlockPos(),
                SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0f, 1.0f);
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
        int slot = CanisterSlotResolver.resolveAndConstrain(
                hitResult.getLocation(), canister.getBlockPos(), hitResult.getDirection(), canister);
        return slot >= 0 && canister.insertCanister(slot, stack, stripGaskets);
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
        int accepted = tryInsertBlobGoo(canister, CanisterBlock.hitSlot(hitResult, pos), type, BlobStacks.volumeOf(stack));
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
    private static int tryInsertBlobGoo(
            CanisterBlockEntity canister, int hitSlot,
            GooType type, int volume) {
        int slot = GooBlockInteraction.findSlot(hitSlot, CanisterBlockEntity.MAX_SLOTS, canister::canAccept);
        if (slot < 0) { return 0; }
        return canister.insertGoo(slot, type, volume);
    }

}
