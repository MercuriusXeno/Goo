package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Render state snapshot for the reactor BER. Captures the output
 * canister item and block facing for the render thread.
 */
public class ReactorRenderState extends BlockEntityRenderState {

    /** Canister item in the output hollow. */
    public ItemStack outputCanister = ItemStack.EMPTY;

    /** Block facing direction - determines hollow orientation. */
    public Direction facing = Direction.SOUTH;
}
