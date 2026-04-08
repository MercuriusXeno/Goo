package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the crucible BER. Extracted on the main thread,
 * consumed on the render thread. Only liquid surface rendering remains.
 */
public class CrucibleRenderState extends BlockEntityRenderState {

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
}
