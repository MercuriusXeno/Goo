package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the tap BER. Wraps a single {@link SlotState}
 * for the body slot plus the spigot facing direction.
 */
public class TapRenderState extends BlockEntityRenderState {

    /** The tap's facing direction (spigot direction). */
    public Direction facing = Direction.SOUTH;

    /** State of the single body slot. */
    public final SlotState slot = new SlotState();
}
