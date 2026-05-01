package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Render state snapshot for the hub BER. Holds one {@link SlotState}
 * per pipe slot plus the shared animation tick.
 */
public class HubRenderState extends BlockEntityRenderState {

    /** Per-slot snapshot, indexed 0..MAX_CANISTERS-1. */
    public final SlotState[] slots = new SlotState[HubBlockEntity.MAX_CANISTERS];

    /** Animation time for sin-wave pulsing. */
    public float animationTime;

    public HubRenderState() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new SlotState();
        }
    }
}
