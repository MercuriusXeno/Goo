package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ChoralTunerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for rename requests.
 * Validates proximity, ownership, and label length before applying
 * the rename to a canister slot or a vat (slot = -1).
 */
public final class CanisterRenameHandler {

    /** Maximum distance (in blocks) from which a player can rename. */
    private static final double MAX_RANGE_SQUARED = 8.0 * 8.0;

    private CanisterRenameHandler() {}

    /** Handles the rename payload on the server thread. */
    public static void handle(CanisterRenamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            applyRename(player, payload.pos(), payload.slot(), payload.newLabel());
        });
    }

    /** Dispatches rename to the appropriate block entity type. */
    private static void applyRename(
            ServerPlayer player, BlockPos pos, int slot, String newLabel) {
        if (!isInRange(player, pos)) return;
        String sanitized = sanitizeLabel(newLabel);

        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof CanisterBlockEntity canister) {
            applyCanisterRename(player, canister, slot, sanitized);
        } else if (be instanceof VatBlockEntity vat) {
            applyVatRename(vat, sanitized);
        }
    }

    /** Applies a rename to a canister slot. */
    private static void applyCanisterRename(
            ServerPlayer player, CanisterBlockEntity canister, int slot, String label) {
        if (!isOwnerOrUnowned(player, canister)) return;
        CanisterMetadata meta = canister.getSlotMetadata(slot);
        canister.setSlotMetadata(slot, meta.withLabel(
            label.isEmpty() ? null : label));
    }

    /** Applies a rename to a vat. */
    private static void applyVatRename(VatBlockEntity vat, String label) {
        vat.setLabel(label.isEmpty() ? null : label);
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

    /** Trims and truncates the label to the maximum length. */
    private static String sanitizeLabel(String label) {
        String trimmed = label.trim();
        if (trimmed.length() > ChoralTunerItem.MAX_LABEL_LENGTH) {
            return trimmed.substring(0, ChoralTunerItem.MAX_LABEL_LENGTH);
        }
        return trimmed;
    }
}
