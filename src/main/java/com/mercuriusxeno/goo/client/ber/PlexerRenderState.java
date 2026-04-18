package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Render state snapshot for the plexer BER. Captures the target item,
 * block facing, aim state, and held item for ghost preview rendering.
 */
public class PlexerRenderState extends BlockEntityRenderState {

    /** Item currently set as the reconstitution target. */
    public ItemStack targetItem = ItemStack.EMPTY;

    /** Block facing direction - determines cutaway orientation. */
    public Direction facing = Direction.SOUTH;

    /** Item the local player is holding in their main hand. */
    public ItemStack heldItem = ItemStack.EMPTY;

    /** True when the player's crosshair is on the cutaway region. */
    public boolean aimingAtCutaway;

    /** True when the held item is a valid reconstitution target. */
    public boolean heldItemValid;

    /** Game time in ticks plus partial tick, for oscillation animation. */
    public float gameTime;
}
