package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the crucible BER. Extracted on the main thread,
 * consumed on the render thread. The server-authoritative platformY drives
 * platform, chain, and rod rendering; the client lerps for sub-tick smoothness.
 */
public class CrucibleRenderState extends BlockEntityRenderState {

    /** Server-authoritative platform Y position (bottom edge, block coords). */
    public float platformY;

    /** Whether a fuel rod is installed (drives rod visibility). */
    public boolean hasFuelRod;

    /** Fuel rod remaining fraction: 0.0 = depleted, 1.0 = fresh. */
    public float fuelFraction;

    /** Total mB remaining in the PMI pool (drives liquid level). */
    public long poolVolume;

    /** Total mB in the reservoir (drives liquid level alongside pool). */
    public long reservoirVolume;

    /** Debounce-stabilized dominant goo type from the block entity. Drives liquid surface texture. */
    @Nullable
    public GooType dominantType;

    /** Outgoing type during a crossfade transition. Null when not crossfading. */
    @Nullable
    public GooType outgoingType;

    /** Crossfade alpha [0, 1]: 0 = fully outgoing, 1 = fully incoming. */
    public float crossfadeAlpha = 1f;

    /** Platform Y at the start of the current server tick (for partialTick interpolation). */
    public float prevPlatformY;

    /** Partial tick fraction [0, 1] for sub-tick interpolation. */
    public float partialTick;

    /** Whether the fuel rod should render: installed and not fully depleted. */
    public boolean hasFuelRodVisible() {
        return hasFuelRod && fuelFraction > 0f;
    }
}
