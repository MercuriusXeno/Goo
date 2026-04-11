package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the chain marker BER. Captures goo type,
 * stack count, and fuse progress for the slime-like orb visual, plus
 * the phase-machine state used by the nether black-hole shader.
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
    public int fuseRemaining;

    /** Partial tick for smooth interpolation. */
    public float partialTick;

    /** True when the player's crosshair is on this block. */
    public boolean targeted;

    /** The face this marker was placed on (for directional rendering). */
    public Direction placedFace = Direction.UP;

    /** Lifecycle phase: FUSE for the orb visual, EXPAND / HOLD / CONTRACT for the shader sphere. */
    public ChainMarkerBlockEntity.Phase phase = ChainMarkerBlockEntity.Phase.FUSE;

    /** Visible scale of the sphere in [0, 1]: grows through EXPAND, 1 during HOLD, shrinks through CONTRACT. */
    public float visibleScale;

    /** Effect radius of the nether blast in blocks. */
    public float implodeRadius;

    /** Cycling animation phase in [0, 1] used by the shader's swirl. Advanced by the BE per tick. */
    public float animationTime;
}
