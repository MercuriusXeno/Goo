package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Resolves gasket region AABBs for each machine type based on role and slot.
 */
final class GasketBoundsResolver {
    /**
     * Vertical midpoint divisor for splitting slot bounds.
     */
    private static final double SLOT_MID_DIVISOR = 2.0;

    /**
     * Vat receiver upper half Y start.
     */
    private static final double VAT_UPPER_START = 0.5;

    /**
     * Vat transmitter lower half Y end.
     */
    private static final double VAT_LOWER_END = 0.5;

    /**
     * Crucible basin top boundary in block-local coords (9/16).
     */
    private static final double CRUCIBLE_BASIN_Y = 9.0 / 16.0;

    private GasketBoundsResolver() {
    }

    /**
     * Resolves the AABB for a gasket region based on machine type, role, and slot.
     *
     * @param be   the block entity instance
     * @param role the gasket role
     * @param slot the slot index
     * @return the gasket bounds in block-local coordinates (0-1), or null if invalid
     */
    static @Nullable AABB resolveGasketBounds(BlockEntity be, GasketRole role, int slot) {
        if (be instanceof CanisterBlockEntity cbe) {
            return resolveCanisterBounds(cbe, role, slot);
        }
        if (be instanceof HubBlockEntity hbe) {
            return resolveHubBounds(hbe, role, slot);
        }
        if (be instanceof VatBlockEntity) {
            return resolveVatBounds(be, role);
        }
        if (be instanceof CrucibleBlockEntity) {
            return resolveCrucibleBounds(be);
        }
        return null;
    }

    /**
     * Canister: split the full slot body at the vertical midpoint into upper/lower halves.
     *
     * @param cbe  the canister block entity
     * @param role the gasket role
     * @param slot the slot index
     * @return the upper or lower half bounds, or null if the slot is invalid/empty
     */
    private static @Nullable AABB resolveCanisterBounds(CanisterBlockEntity cbe,
                                                        GasketRole role, int slot) {
        if (slot < 0 || slot >= CanisterBlock.SLOT_COUNT) {
            return null;
        }
        if (cbe.getCanister(slot).isEmpty()) {
            return null;
        }
        return splitBoundsAtMidY(CanisterBlock.slotShape(slot).bounds(), role);
    }

    /**
     * Hub: split the full slot body at the vertical midpoint into upper/lower halves.
     *
     * @param hbe  the hub block entity
     * @param role the gasket role
     * @param slot the slot index
     * @return the upper or lower half bounds, or null if the slot is invalid/empty
     */
    private static @Nullable AABB resolveHubBounds(HubBlockEntity hbe,
                                                   GasketRole role, int slot) {
        if (slot < 0 || slot >= HubBlock.SLOT_COUNT) {
            return null;
        }
        if (hbe.getCanister(slot).isEmpty()) {
            return null;
        }
        return splitBoundsAtMidY(HubBlock.slotShape(slot).bounds(), role);
    }

    /**
     * Splits an AABB at its vertical midpoint. RECEIVER gets the upper half,
     * TRANSMITTER gets the lower half.
     *
     * @param bounds the full slot bounds
     * @param role   the gasket role determining which half to return
     * @return the upper or lower half of the bounds
     */
    private static AABB splitBoundsAtMidY(AABB bounds, GasketRole role) {
        double midY = (bounds.minY + bounds.maxY) / SLOT_MID_DIVISOR;
        if (role == GasketRole.RECEIVER) {
            return new AABB(bounds.minX, midY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ);
        }
        return new AABB(bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, midY, bounds.maxZ);
    }

    /**
     * Vat: upper or lower half of the full block, depending on role.
     *
     * @param be   the block entity instance
     * @param role the gasket role
     * @return the resolved bounds, or null if the role's gasket is absent
     */
    private static @Nullable AABB resolveVatBounds(BlockEntity be, GasketRole role) {
        var state = be.getBlockState();
        if (role == GasketRole.RECEIVER && state.getValue(VatBlock.GASKET_CAP)) {
            return new AABB(0, VAT_UPPER_START, 0, 1, 1, 1);
        } else if (role == GasketRole.TRANSMITTER && state.getValue(VatBlock.GASKET_BASE)) {
            return new AABB(0, 0, 0, 1, VAT_LOWER_END, 1);
        }
        return null;
    }

    /**
     * Crucible: basin region (upper portion, Y 9/16 to 16/16). Always transmitter.
     *
     * @param be the block entity instance
     * @return the basin bounds, or null if no gasket is installed
     */
    private static @Nullable AABB resolveCrucibleBounds(BlockEntity be) {
        if (!be.getBlockState().getValue(CrucibleBlock.HAS_GASKET)) {
            return null;
        }
        return new AABB(0, CRUCIBLE_BASIN_Y, 0, 1, 1, 1);
    }
}
