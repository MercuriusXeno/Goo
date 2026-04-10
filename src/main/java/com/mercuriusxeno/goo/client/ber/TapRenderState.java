package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the tap BER. Captures the facing direction,
 * canister presence, and fluid fill for the single canister slot.
 */
public class TapRenderState extends BlockEntityRenderState {

    /** The tap's facing direction (spigot direction). */
    public Direction facing = Direction.SOUTH;

    /** Whether a canister is inserted in the tap's body slot. */
    public boolean hasCanister;

    /** Dominant goo type in the canister (null if empty). */
    public @Nullable GooType gooType;

    /** Fill fraction in [0, 1]. */
    public float fill;

    /** Compression level of the inserted canister. */
    public int matrices;
}
