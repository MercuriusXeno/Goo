package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the reactor BER. Wraps a single
 * {@link SlotState} for the output canister slot plus reactor-specific
 * block-level fields (facing, crafting, wheel animation).
 */
public class ReactorRenderState extends BlockEntityRenderState {

    /** Block facing direction; determines hollow orientation. */
    public Direction facing = Direction.SOUTH;

    /** State of the output canister slot. */
    public final SlotState slot = new SlotState();

    /** True when the reactor is actively crafting (wheels at max speed). */
    public boolean crafting;

    /** Current wheel rotation angle in degrees. */
    public float wheelAngle;
}
