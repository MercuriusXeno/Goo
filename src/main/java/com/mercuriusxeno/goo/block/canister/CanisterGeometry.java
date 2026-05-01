package com.mercuriusxeno.goo.block.canister;

/**
 * Pixel-space geometry constants for canister body and gasket caps.
 * Shared between the in-world block-entity renderer (multi-slot grid)
 * and the special item-form renderer (single canister at block center).
 *
 * <p>Y-axis ranges (BODY_BOT, BODY_TOP, GASKET_BOT, GASKET_TOP) and the
 * gasket-side UV layout are universal across all canister forms. The
 * XZ slot center varies with form (3x3 grid in the BER vs. block center
 * in the special renderer) and lives at the call site.
 */
public final class CanisterGeometry {

    /** Canister half-width: 2 px. */
    public static final float HW = 2f / 16f;

    /** Bottom of lower gasket (y=0). */
    public static final float GASKET_BOT = 0f;

    /** Top of lower gasket / bottom of body (y=1px). */
    public static final float BODY_BOT = 1f / 16f;

    /** Top of body / bottom of upper gasket (y=11px). */
    public static final float BODY_TOP = 11f / 16f;

    /** Top of upper gasket (y=12px). */
    public static final float GASKET_TOP = 12f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    public static final float FLUID_INSET = 0.5f / 16f;

    /** Gasket side U start: column 4/16. */
    public static final float GS_U0 = 0.25f;

    /** Gasket side U end: column 8/16. */
    public static final float GS_U1 = 0.5f;

    /** Gasket side V end: row 1/16. */
    public static final float GS_V1 = 0.0625f;

    private CanisterGeometry() {
    }
}
