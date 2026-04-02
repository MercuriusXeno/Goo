package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
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

    /** Maximum distance (in blocks) from which a player can unlink. */
    private static final double MAX_RANGE_SQUARED = 8.0 * 8.0;

    private CanisterUnlinkHandler() {}

    /** Handles the unlink payload on the server thread. */
    public static void handle(CanisterUnlinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            applyUnlink(player, payload.pos(), payload.slot(), payload.role());
        });
    }

    /** Validates and applies the unlink via the unified IGasketHolder interface. */
    private static void applyUnlink(ServerPlayer player, BlockPos pos, int slot,
            @Nullable GasketRole role) {
        if (!isInRange(player, pos)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof CanisterBlockEntity canister) {
            if (!isOwnerOrUnowned(player, canister)) return;
        }
        if (be instanceof IGasketHolder holder) {
            unlinkHolder(serverLevel, holder, slot, role);
        }
    }

    /**
     * Unlinks gaskets on an IGasketHolder. For slotted machines (slot >= 0),
     * both roles are always unlinked. For non-slotted, the role filter applies.
     */
    private static void unlinkHolder(ServerLevel level, IGasketHolder holder,
            int slot, @Nullable GasketRole role) {
        GasketRegistry registry = GasketRegistry.get(level);
        for (GasketRole r : GasketRole.values()) {
            if (slot < 0 && role != null && role != r) continue;
            clearRemotePartner(level, holder.getPartner(r, slot));
            unlinkGasket(registry, holder.getGasketId(r, slot));
            holder.setPartner(r, slot, null);
        }
    }

    /** Clears the partner reference on the remote side of a link. */
    private static void clearRemotePartner(Level level, @Nullable GasketPartner partner) {
        if (partner == null) return;
        if (!level.isLoaded(partner.pos())) return;

        BlockEntity be = level.getBlockEntity(partner.pos());
        if (be instanceof IGasketHolder holder) {
            for (GasketRole r : GasketRole.values()) {
                holder.setPartner(r, partner.slot(), null);
            }
        }
    }

    /** Unlinks a single gasket if non-null. */
    private static void unlinkGasket(GasketRegistry registry, @Nullable UUID gasketId) {
        if (gasketId != null) {
            registry.unlink(gasketId);
        }
    }

    /** Returns true if the player is within interaction range of the position. */
    private static boolean isInRange(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
            <= MAX_RANGE_SQUARED;
    }

    /** Returns true if the player owns the canister or the canister has no owner. */
    private static boolean isOwnerOrUnowned(
            ServerPlayer player, CanisterBlockEntity canister) {
        return canister.getOwner() == null
            || canister.getOwner().equals(player.getUUID());
    }
}
