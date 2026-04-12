package com.mercuriusxeno.goo.client.ber;

/**
 * Shared gasket endcap math for containers that stack gasket boxes on the
 * top and bottom of vertical fluid columns (canister slots, hub rings).
 * Kept as a static helper because the callers differ in their slot-center
 * sources and Y ranges but agree on the underlying box-emission pattern.
 */
public final class GasketCapRenderer {

    private GasketCapRenderer() {}

    /**
     * Y coordinates of the top/bottom body edges and the gasket edges they
     * cap. Canister and hub disagree on the exact values because their
     * interior fluid columns have different heights.
     *
     * @param bodyBot    Y of the fluid body's bottom edge
     * @param bodyTop    Y of the fluid body's top edge
     * @param gasketBot  Y of the bottom gasket's outer edge (beneath {@code bodyBot})
     * @param gasketTop  Y of the top gasket's outer edge (above {@code bodyTop})
     */
    public record GasketYRanges(float bodyBot, float bodyTop, float gasketBot, float gasketTop) {}

    /**
     * UV region used by {@link RenderContext#gasketBox} for the gasket side strips.
     *
     * @param u0 left U
     * @param u1 right U
     * @param v1 bottom V
     */
    public record GasketUv(float u0, float u1, float v1) {}

    /**
     * Returns true if any slot has a gasket on either end. Parallel boolean
     * arrays are addressed by slot index, so both must have the same length
     * (checked via {@code top.length}).
     *
     * @param top    per-slot top gasket presence flags
     * @param bottom per-slot bottom gasket presence flags
     * @return true if any index has a gasket on either side
     */
    public static boolean hasAnyCap(boolean[] top, boolean[] bottom) {
        for (int i = 0; i < top.length; i++) {
            if (top[i] || bottom[i]) { return true; }
        }
        return false;
    }

    /**
     * Builds the XZ cuboid centered at the given coordinates with zero Y
     * extent. The caller passes the center in block-local [0,1] space.
     *
     * @param cx      slot center X
     * @param cz      slot center Z
     * @param halfW   half-width of the slot square
     * @return an XZ cuboid with Y zeroed, ready for {@link CuboidBounds#withY}
     */
    public static CuboidBounds slotBoundsXZ(float cx, float cz, float halfW) {
        return new CuboidBounds(cx - halfW, cx + halfW, cz - halfW, cz + halfW, 0, 0);
    }

    /**
     * Emits the top and/or bottom gasket cap boxes for a single slot. The
     * UVs are uniform across container types; only the Y ranges differ.
     *
     * @param ctx    the render context
     * @param base   the slot's XZ cuboid with Y zeroed (from {@link #slotBoundsXZ})
     * @param y      the container's four gasket Y edges
     * @param uv     the gasket side UV region
     * @param top    whether to render the top cap
     * @param bottom whether to render the bottom cap
     */
    public static void renderEndcaps(RenderContext ctx, CuboidBounds base,
                                     GasketYRanges y, GasketUv uv, boolean top, boolean bottom) {
        if (!top && !bottom) { return; }
        if (top) {
            ctx.gasketBox(base.withY(y.bodyTop(), y.gasketTop()), uv.u0(), uv.u1(), uv.v1());
        }
        if (bottom) {
            ctx.gasketBox(base.withY(y.gasketBot(), y.bodyBot()), uv.u0(), uv.u1(), uv.v1());
        }
    }
}
