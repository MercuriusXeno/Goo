package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Render state snapshot for the canister BER. Holds one {@link SlotState}
 * per cell of the 3x3 grid plus the shared animation tick.
 */
public class CanisterRenderState extends BlockEntityRenderState {

    /** Per-slot snapshot, indexed 0..MAX_SLOTS-1. */
    public final SlotState[] slots = new SlotState[CanisterBlockEntity.MAX_SLOTS];

    /** Animation time (game ticks + partial tick) for sin-wave pulsing. */
    public float animationTime;

    public CanisterRenderState() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new SlotState();
        }
    }
}
