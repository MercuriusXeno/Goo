package com.mercuriusxeno.goo.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Render state snapshot for the plexer BER. Captures the target item
 * and block facing for the render thread.
 */
public class PlexerRenderState extends BlockEntityRenderState {

    /** Item currently set as the reconstitution target. */
    public ItemStack targetItem = ItemStack.EMPTY;

    /** Block facing direction - determines cutaway orientation. */
    public Direction facing = Direction.SOUTH;
}
