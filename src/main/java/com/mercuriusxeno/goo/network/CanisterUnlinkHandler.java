package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Server-side handler for gasket unlink requests. Validates proximity,
 * then unlinks gaskets on the targeted machine and clears partner info
 * on both sides. Supports canister, hub, vat, and crucible blocks.
 */
public final class CanisterUnlinkHandler {

    /** Maximum interaction range in blocks. */
    private static final double MAX_RANGE = 8.0;
    /** Maximum distance (in blocks) from which a player can unlink. */
    private static final double MAX_RANGE_SQUARED = MAX_RANGE * MAX_RANGE;
    /** Block center offset (half-block). */
    private static final double BLOCK_CENTER = 0.5;

    private CanisterUnlinkHandler() {}

    /**
     * Handles the unlink payload on the server thread.
     *
     * @param payload the unlink payload data
     * @param context the network context
     */
    public static void handle(CanisterUnlinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            applyUnlink(player, payload.pos(), payload.slot(), payload.role());
        });
    }

    /**
     * Validates and applies the unlink via the unified IGasketHolder interface.
     *
     * @param player the interacting player
     * @param pos    the block position
     * @param slot   the canister slot index
     * @param role   the gasket role filter, or null for both
     */
    private static void applyUnlink(ServerPlayer player, BlockPos pos, int slot,
            @Nullable GasketRole role) {
        if (!isInRange(player, pos)) { return; }
        if (!(player.level() instanceof ServerLevel serverLevel)) { return; }
        BlockEntity be = player.level().getBlockEntity(pos);
        if (isUnauthorized(player, be)) { return; }
        if (be instanceof IGasketHolder holder) {
            unlinkHolder(serverLevel, holder, slot, role);
        }
    }

    /** Returns true if the player lacks ownership rights for this canister block entity.
     *
     * @param player the interacting player
     * @param be     the block entity
     * @return true if access is denied
     */
    private static boolean isUnauthorized(ServerPlayer player, BlockEntity be) {
        return be instanceof CanisterBlockEntity canister && !isOwnerOrUnowned(player, canister);
    }

    /**
     * Unlinks gaskets on an IGasketHolder. For slotted machines (slot >= 0),
     * both roles are always unlinked. For non-slotted, the role filter applies.
     *
     * @param level  the server level
     * @param holder the gasket holder to unlink
     * @param slot   the canister slot index
     * @param role   the gasket role filter, or null for both
     */
    private static void unlinkHolder(ServerLevel level, IGasketHolder holder,
            int slot, @Nullable GasketRole role) {
        GasketRegistry registry = GasketRegistry.get(level);
        for (GasketRole r : GasketRole.values()) {
            if (slot < 0 && role != null && role != r) { continue; }
            clearRemotePartner(level, holder.getPartner(r, slot));
            unlinkGasket(registry, holder.getGasketId(r, slot));
            holder.setPartner(r, slot, null);
        }
    }

    /**
     * Clears the partner reference on the remote side of a link.
     *
     * @param level   the current level
     * @param partner the remote partner to clear, or null if none
     */
    private static void clearRemotePartner(Level level, @Nullable GasketPartner partner) {
        if (partner == null) { return; }
        if (!level.isLoaded(partner.pos())) { return; }

        BlockEntity be = level.getBlockEntity(partner.pos());
        if (be instanceof IGasketHolder holder) {
            for (GasketRole r : GasketRole.values()) {
                holder.setPartner(r, partner.slot(), null);
            }
        }
    }

    /**
     * Unlinks a single gasket if non-null.
     *
     * @param registry the gasket registry
     * @param gasketId the gasket UUID, or null if none
     */
    private static void unlinkGasket(GasketRegistry registry, @Nullable UUID gasketId) {
        if (gasketId != null) {
            registry.unlink(gasketId);
        }
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

    /**
     * Returns true if the player owns the canister or the canister has no owner.
     *
     * @param player   the interacting player
     * @param canister the canister block entity
     * @return true if the player is the owner or the canister is unowned
     */
    private static boolean isOwnerOrUnowned(
            ServerPlayer player, CanisterBlockEntity canister) {
        return canister.getOwner() == null
            || canister.getOwner().equals(player.getUUID());
    }
}
