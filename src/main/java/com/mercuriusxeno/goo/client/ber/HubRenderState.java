package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the hub BER. Captures which pipe slots
 * hold canisters, their fluid fill, and active stream data.
 */
public class HubRenderState extends BlockEntityRenderState {

    /** Per-slot canister presence flags (indexed 0-7). */
    public final boolean[] canisterPresent = new boolean[HubBlockEntity.MAX_CANISTERS];

    /** Per-slot top gasket presence (true if a choral gasket is installed on top). */
    public final boolean[] topGasketPresent = new boolean[HubBlockEntity.MAX_CANISTERS];

    /** Per-slot bottom gasket presence (true if a choral gasket is installed on bottom). */
    public final boolean[] bottomGasketPresent = new boolean[HubBlockEntity.MAX_CANISTERS];

    /** Per-slot dominant goo type (null if slot is empty or canister has no goo). */
    public final @Nullable GooType[] slotType = new GooType[HubBlockEntity.MAX_CANISTERS];

    /** Per-slot fill fraction in [0, 1]. */
    public final float[] slotFill = new float[HubBlockEntity.MAX_CANISTERS];


    /** Per-slot stream type (non-null when goo is actively flowing in via top gasket). */
    public final @Nullable GooType[] streamType = new GooType[HubBlockEntity.MAX_CANISTERS];

    /** Per-slot stream rate in mB/tick. */
    public final float[] streamRate = new float[HubBlockEntity.MAX_CANISTERS];

    /** Animation time for sin-wave pulsing. */
    public float animationTime;
}
