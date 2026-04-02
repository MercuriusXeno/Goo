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

    /** Maximum distance (in blocks) from which a player can punch a canister. */
    private static final double MAX_RANGE_SQUARED = 8.0 * 8.0;

    private CanisterPunchHandler() {}

    /** Handles the punch payload on the server thread. */
    public static void handle(CanisterPunchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            applyPunch(player, payload.pos(), payload.slot());
        });
    }

    /** Validates and pops the canister from the specified slot. */
    private static void applyPunch(ServerPlayer player, BlockPos pos, int slot) {
        if (!isInRange(player, pos)) return;
        if (InteractionCooldown.isOnCooldown(player.getUUID(), player.level().getGameTime())) return;

        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof CanisterBlockEntity canister)) return;

        ItemStack removed = canister.removeCanister(slot);
        if (removed.isEmpty()) return;

        dropCanister(player, removed, pos);
        player.level().playSound(null, pos,
                SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        InteractionCooldown.markInteraction(player.getUUID(), player.level().getGameTime());

        if (!canister.hasAnyCanister()) {
            player.level().removeBlock(pos, false);
        }
    }

    /** Drops the punched canister as an item entity at the block position. */
    private static void dropCanister(ServerPlayer player, ItemStack stack, BlockPos pos) {
        net.minecraft.world.level.block.Block.popResource(player.level(), pos, stack);
    }

    /** Returns true if the player is within interaction range of the position. */
    private static boolean isInRange(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
            <= MAX_RANGE_SQUARED;
    }
}
