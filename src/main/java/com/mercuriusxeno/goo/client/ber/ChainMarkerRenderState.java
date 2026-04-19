package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the chain marker BER. Captures goo type,
 * stack count, and fuse progress for the slime-like orb visual, plus
 * a flag + sphere fields populated when a nether {@code ChainBehavior}
 * is active so the BER can submit the black-hole shader sphere.
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

    /** True when flat mining mode is active. */
    public boolean flatMode;

    /** Game tick when the last stack was added (for pulse animation). */
    public long lastStackTick;

    /** Current game time including partial tick, for pulse calculation. */
    public float gameTime;

    /** The face this marker was placed on (for directional rendering). */
    public Direction placedFace = Direction.UP;

    /** True when a non-nether chain behavior (rock/blaze) is actively
     * mining. The ghost outline persists through the mining phase. */
    public boolean behaviorActive;

    /** Number of depth layers already mined by the active behavior. */
    public int minedLayers;

    /** True when a metal spike trap behavior is active. */
    public boolean metalActive;

    /** Per-entity spike animation snapshots: [entityId, animTick] pairs. */
    public java.util.List<int[]> spikeAnims = java.util.List.of();

    /** Remaining spike charges for the metal trap. */
    public int metalCharges;

    /** True when a crystal shard cloud behavior is active. */
    public boolean crystalActive;

    /** Charge density [0-1] for crystal cloud visual scaling. */
    public float crystalDensity;

    /** Slow cycling phase [0-1] for crystal crack drift animation. */
    public float crystalAnimationTime;

    /** Cloud radius fraction [0-1] for expand/contract animation. */
    public float crystalRadiusFraction;

    /** True when a nether black-hole behavior is active on this marker.
     * The BER uses this flag to branch between the orb visual (false) and
     * the shader sphere (true). */
    public boolean netherActive;

    /** Visible scale of the sphere in [0, 1]: grows through EXPAND, 1
     * during HOLD, shrinks through CONTRACT. Only meaningful when
     * {@link #netherActive} is true. */
    public float visibleScale;

    /** Accretion disc expansion scale in [0, 1]. Runs on a separate
     * curve from {@link #visibleScale} so the disc sweeps outward past
     * the sphere instead of inflating in lockstep with it. Only
     * meaningful when {@link #netherActive} is true. */
    public float diskExpansionScale;

    /** Effect radius of the nether blast in blocks. Only meaningful
     * when {@link #netherActive} is true. */
    public float implodeRadius;

    /** Cycling animation phase in [0, 1] used by the shader's swirl. */
    public float animationTime;
}
