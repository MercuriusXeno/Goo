package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GasketPartner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Display formatting for gasket partner references. Lives in client/
 * because it requires Level access to resolve labels from loaded chunks.
 * Keeps GasketPartner (a data component) free of Level dependencies.
 */
public final class GasketPartnerDisplay {

    private GasketPartnerDisplay() {}

    /**
     * Formats a display string for a gasket partner: the partner's label if
     * available, otherwise "X: x, Y: y, Z: z". For entity targets, returns
     * the player name if available.
     *
     * @param partner the partner to format
     * @param level   the level for label lookups
     * @return human-readable display string
     */
    public static String formatDisplay(GasketPartner partner, Level level) {
        if (partner.isEntityTarget()) {
            return formatEntityDisplay(partner, level);
        }
        String label = lookupLabel(partner, level);
        return label != null ? label : partner.formatCoords();
    }

    /** Formats display for entity targets: player name or UUID prefix. */
    private static String formatEntityDisplay(GasketPartner partner, Level level) {
        var entityId = partner.entityId();
        if (entityId == null) return "?";
        var player = level.getPlayerByUUID(entityId);
        if (player != null) return player.getName().getString();
        return entityId.toString().substring(0, 8) + "...";
    }

    /** Returns the partner's label if the chunk is loaded, or null. */
    private static @Nullable String lookupLabel(GasketPartner partner, Level level) {
        var pos = partner.pos();
        if (!level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CanisterBlockEntity canister) {
            return canisterSlotLabel(canister, partner.slot());
        }
        if (be instanceof VatBlockEntity vat) {
            return nonEmptyOrNull(vat.getLabel());
        }
        return null;
    }

    /** Returns the label of a specific canister slot, or null. */
    private static @Nullable String canisterSlotLabel(CanisterBlockEntity canister, int slot) {
        if (slot < 0) return null;
        CanisterMetadata meta = canister.getSlotMetadata(slot);
        return nonEmptyOrNull(meta.label());
    }

    /** Returns the string if non-null and non-empty, or null. */
    private static @Nullable String nonEmptyOrNull(@Nullable String s) {
        return (s != null && !s.isEmpty()) ? s : null;
    }
}
