package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the canister BER. Captures which 3x3 grid
 * slots hold canisters and their fluid fill levels for the render thread.
 */
public class CanisterRenderState extends BlockEntityRenderState {

    /** Per-slot canister presence flags (indexed 0-8). */
    public final boolean[] canisterPresent = new boolean[CanisterBlockEntity.MAX_SLOTS];

    /** Per-slot goo type (null if slot is empty, has no goo, or holds a vanilla fluid). */
    public final @Nullable GooType[] slotType = new GooType[CanisterBlockEntity.MAX_SLOTS];

    /** Per-slot fluid for vanilla (non-goo) fluids. Fluids.EMPTY when slot has goo or is empty. */
    public final Fluid[] slotFluid = initFluids();

    /** Per-slot fill fraction in [0, 1] (0 if slot is empty or canister has no fluid). */
    public final float[] slotFill = new float[CanisterBlockEntity.MAX_SLOTS];


    /** Per-slot top gasket presence (true if a choral gasket is installed on top). */
    public final boolean[] topGasketPresent = new boolean[CanisterBlockEntity.MAX_SLOTS];

    /** Per-slot bottom gasket presence (true if a choral gasket is installed on bottom). */
    public final boolean[] bottomGasketPresent = new boolean[CanisterBlockEntity.MAX_SLOTS];

    /** Per-slot stream type (non-null when goo is actively flowing in via top gasket). */
    public final @Nullable GooType[] streamType = new GooType[CanisterBlockEntity.MAX_SLOTS];

    /** Per-slot stream fluid for vanilla (non-goo) streams. */
    public final Fluid[] streamFluid = initFluids();

    /** Per-slot stream rate in mB/tick (used for stream width calculation). */
    public final float[] streamRate = new float[CanisterBlockEntity.MAX_SLOTS];

    /** Animation time (game ticks + partial tick) for sin-wave pulsing. */
    public float animationTime;

    private static Fluid[] initFluids() {
        Fluid[] arr = new Fluid[CanisterBlockEntity.MAX_SLOTS];
        java.util.Arrays.fill(arr, Fluids.EMPTY);
        return arr;
    }
}
