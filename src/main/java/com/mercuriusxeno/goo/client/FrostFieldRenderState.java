package com.mercuriusxeno.goo.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Render state snapshot for the frost field BER. Captures stack count,
 * game time for breathing, and duration remaining for the fade-out.
 */
public class FrostFieldRenderState extends BlockEntityRenderState {

    /** Current stack count (1-based). */
    public int stacks = 1;

    /** Current field radius. */
    public int radius = 3;

    /** Level game time at extraction -- drives the breathing cycle. */
    public long gameTime;

    /** Duration remaining in ticks -- drives the fade-out near expiry. */
    public int durationRemaining;

    /** Partial tick for smooth interpolation. */
    public float partialTick;

    /** True when the player's crosshair is on this block. */
    public boolean targeted;
}
