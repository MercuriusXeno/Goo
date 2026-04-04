package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the chain marker BER. Captures goo type,
 * stack count, and fuse progress for the slime-like orb visual.
 */
public class ChainMarkerRenderState extends BlockEntityRenderState {

    /** The goo type determining color and fluid texture. */
    public GooType gooType = GooType.ROCK;

    /** Current stack count (1-based). */
    public int stackCount = 1;

    /** Maximum stack count from the profile. */
    public int maxStacks = 1;

    /** Total fuse duration from the profile. */
    public int fuseTicks = 1;

    /** Fuse remaining in ticks (for pulsing/implosion animation). */
    public int fuseRemaining = 0;

    /** Partial tick for smooth interpolation. */
    public float partialTick = 0f;

    /** True when the player's crosshair is on this block. */
    public boolean targeted = false;

    /** The face this marker was placed on (for directional rendering). */
    public Direction placedFace = Direction.UP;
}
