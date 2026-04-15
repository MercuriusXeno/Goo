package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooInteractionType;
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

}
