package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the reactor BER. Captures the output
 * canister presence, fluid type, fill ratio, and block facing.
 */
public class ReactorRenderState extends BlockEntityRenderState {

    /** Block facing direction - determines hollow orientation. */
    public Direction facing = Direction.SOUTH;

    /** Whether a canister is present in the output hollow. */
    public boolean hasCanister;

    /** Compression level of the output canister. */
    public int matrices;

    /** Goo type in the output canister, or null if empty/vanilla fluid. */
    public @Nullable GooType gooType;

    /** Fill ratio [0, 1] of the output canister. */
    public float fill;
}
