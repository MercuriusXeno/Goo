package com.mercuriusxeno.goo.item.gasket;

import net.minecraft.core.Direction;

/**
 * Pure utility: maps a block hit location to a {@link GasketRole} for each
 * machine type. All methods are static and take only primitive/enum arguments,
 * making them testable without Minecraft.
 */
public final class GasketRegionResolver {

    /** Midpoint of the block's vertical range, used to split top/bottom halves. */
    private static final double VERTICAL_MIDPOINT = 0.5;
    /** Divisor for computing midpoint of a voxel range. */
    private static final double HALF_DIVISOR = 2.0;

    private GasketRegionResolver() {}

    /**
     * Resolves the gasket role for a vat hit based on face direction and
     * local Y coordinate within the block.
     *
     * @param hitFace the face of the block that was hit
     * @param localY the Y coordinate within the block (0.0 to 1.0)
     * @return RECEIVER for top/cap hits, TRANSMITTER for bottom/base hits
     */
    public static GasketRole resolveVatRole(Direction hitFace, double localY) {
        if (hitFace == Direction.UP) { return GasketRole.RECEIVER; }
        if (hitFace == Direction.DOWN) { return GasketRole.TRANSMITTER; }
        return localY >= VERTICAL_MIDPOINT ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
    }

    /**
     * Resolves the gasket role for a canister slot hit based on where within
     * the slot's vertical range the click landed.
     *
     * @param localY the Y coordinate within the block (0.0 to 1.0)
     * @param slotMinY the bottom Y of the slot voxel (0.0 to 1.0)
     * @param slotMaxY the top Y of the slot voxel (0.0 to 1.0)
     * @return RECEIVER for top half, TRANSMITTER for bottom half
     */
    public static GasketRole resolveCanisterSlotRole(
            double localY, double slotMinY, double slotMaxY) {
        double midY = (slotMinY + slotMaxY) / HALF_DIVISOR;
        return localY >= midY ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
    }

    /**
     * Resolves the gasket role for a crucible. The crucible basin is always
     * a transmitter (output), regardless of where on the block the click lands.
     *
     * @return always TRANSMITTER
     */
    public static GasketRole resolveCrucibleRole() {
        return GasketRole.TRANSMITTER;
    }
}
