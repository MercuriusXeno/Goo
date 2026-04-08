package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.InteractionCooldown;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for canister punch (left-click pop) requests.
 * Validates proximity, cooldown, and slot occupancy, then pops the
 * targeted canister into the player's inventory (or drops it).
 */
public final class CanisterPunchHandler {

    /** Maximum interaction range in blocks. */
    private static final double MAX_RANGE = 8.0;
    /** Maximum distance (in blocks) from which a player can punch a canister. */
    private static final double MAX_RANGE_SQUARED = MAX_RANGE * MAX_RANGE;
    /** Block center offset (half-block). */
    private static final double BLOCK_CENTER = 0.5;
    /** Sound volume for the pot-hit feedback. */
    private static final float SOUND_VOLUME = 1.0F;
    /** Sound pitch for the pot-hit feedback. */
    private static final float SOUND_PITCH = 1.0F;

    private CanisterPunchHandler() {}

    /**
     * Handles the punch payload on the server thread.
     *
     * @param payload the punch payload data
     * @param context the network context
     */
    public static void handle(CanisterPunchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            applyPunch(player, payload.pos(), payload.slot());
        });
    }

    /**
     * Validates and pops the canister from the specified slot.
     *
     * @param player the interacting player
     * @param pos    the block position
     * @param slot   the canister slot index
     */
    private static void applyPunch(ServerPlayer player, BlockPos pos, int slot) {
        if (!isInRange(player, pos)) { return; }
        if (InteractionCooldown.isOnCooldown(player.getUUID(), player.level().getGameTime())) { return; }
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof CanisterBlockEntity canister)) { return; }
        ItemStack removed = canister.removeCanister(slot);
        if (!removed.isEmpty()) {
            executePunch(player, pos, removed, canister);
        }
    }

    /** Drops the canister, plays the hit sound, marks cooldown, and removes empty canister blocks.
     *
     * @param player   the interacting player
     * @param pos      the block position
     * @param removed  the removed canister stack
     * @param canister the canister block entity
     */
    private static void executePunch(ServerPlayer player, BlockPos pos,
                                      ItemStack removed, CanisterBlockEntity canister) {
        dropCanister(player, removed, pos);
        player.level().playSound(null, pos,
                SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
        InteractionCooldown.markInteraction(player.getUUID(), player.level().getGameTime());
        if (!canister.hasAnyCanister()) {
            player.level().removeBlock(pos, false);
        }
    }

    /**
     * Drops the punched canister as an item entity at the block position.
     *
     * @param player the interacting player
     * @param stack  the canister item stack
     * @param pos    the block position to drop at
     */
    private static void dropCanister(ServerPlayer player, ItemStack stack, BlockPos pos) {
        net.minecraft.world.level.block.Block.popResource(player.level(), pos, stack);
    }

    /**
     * Returns true if the player is within interaction range of the position.
     *
     * @param player the interacting player
     * @param pos    the block position
     * @return true if within range
     */
    private static boolean isInRange(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER)
            <= MAX_RANGE_SQUARED;
    }
}
