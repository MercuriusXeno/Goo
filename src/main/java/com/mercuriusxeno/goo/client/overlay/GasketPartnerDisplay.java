package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Display formatting for gasket partner references. Lives in client/
 * because it requires Level access to resolve labels from loaded chunks.
 * Keeps GasketPartner (a data component) free of Level dependencies.
 */
public final class GasketPartnerDisplay {

    /** Fallback label when entity UUID cannot be resolved. */
    private static final String UNKNOWN_ENTITY = "?";
    /** Length of UUID prefix shown for unresolved entity partners. */
    private static final int UUID_PREFIX_LENGTH = 8;
    /** Suffix appended to truncated UUID strings. */
    private static final String UUID_ELLIPSIS = "...";

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

    /**
     * Formats display for entity targets: player name or UUID prefix.
     *
     * @param partner the gasket partner reference
     * @param level the current level
     * @return the formatted string
     */
    private static String formatEntityDisplay(GasketPartner partner, Level level) {
        var entityId = partner.entityId();
        if (entityId == null) { return UNKNOWN_ENTITY; }
        var player = level.getPlayerByUUID(entityId);
        if (player != null) { return player.getName().getString(); }
        return entityId.toString().substring(0, UUID_PREFIX_LENGTH) + UUID_ELLIPSIS;
    }

    /**
     * Returns the partner's label if the chunk is loaded, or null.
     *
     * @param partner the gasket partner reference
     * @param level the current level
     * @return the label, or null if not found
     */
    private static @Nullable String lookupLabel(GasketPartner partner, Level level) {
        var pos = partner.pos();
        if (!level.isLoaded(pos)) { return null; }
        return extractLabel(level.getBlockEntity(pos), partner.slot());
    }

    /** Extracts a human-readable label from a canister slot or vat block entity.
     *
     * @param be   the block entity (may be null or unrecognized)
     * @param slot the canister slot index (for canister BEs)
     * @return the label, or null if not a labeled holder
     */
    private static @Nullable String extractLabel(@Nullable BlockEntity be, int slot) {
        if (be instanceof CanisterBlockEntity canister) {
            return canisterSlotLabel(canister, slot);
        }
        if (be instanceof VatBlockEntity vat) {
            return nonEmptyOrNull(vat.getLabel());
        }
        return null;
    }

    /**
     * Returns the label of a specific canister slot, or null.
     *
     * @param canister the canister block entity
     * @param slot the slot index
     * @return the result
     */
    private static @Nullable String canisterSlotLabel(CanisterBlockEntity canister, int slot) {
        if (slot < 0) { return null; }
        CanisterMetadata meta = canister.getSlotMetadata(slot);
        return nonEmptyOrNull(meta.label());
    }

    /**
     * Returns the string if non-null and non-empty, or null.
     *
     * @param s the string to check
     * @return the result
     */
    private static @Nullable String nonEmptyOrNull(@Nullable String s) {
        return (s != null && !s.isEmpty()) ? s : null;
    }
}
