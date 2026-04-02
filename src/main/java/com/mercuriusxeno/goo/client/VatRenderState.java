package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the vat BER. Captures the dominant goo type,
 * fill fraction, and stack geometry for the render thread. When vats are
 * vertically stacked, fill and dominant type are computed from the merged
 * stack contents so all vats in the column render one contiguous fluid body.
 */
public class VatRenderState extends BlockEntityRenderState {

    /** Dominant goo type across the stack, or null if empty. */
    public @Nullable GooType dominantType;

    /** Stack-wide fill fraction in [0, 1] (total volume / total capacity). */
    public float fillFraction;

    /** True if another vat is directly above (interior ceiling extends to 1.0). */
    public boolean vatAbove;

    /** True if another vat is directly below (interior floor extends to 0.0). */
    public boolean vatBelow;

    /** Total number of vats in the vertical stack (1 = solo). */
    public int stackSize;

    /** This vat's zero-based index from the bottom of the stack. */
    public int indexFromBottom;

    /** Stream type (non-null when goo is actively flowing in via cap gasket). */
    public @Nullable GooType streamType;

    /** Stream rate in mB/tick (used for stream width calculation). */
    public float streamRate;

    /** Animation time for sin-wave pulsing. */
    public float animationTime;
}
