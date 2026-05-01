package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Per-slot render snapshot for slot-arrayed machines (Hub, Canister) and
 * single-slot machines (Tap, Reactor). Mutable; populated by the BER's
 * extractRenderState path each frame and consumed by submit. Each
 * RenderState instance owns its slot states for the life of the BE.
 *
 * <p>Some fields apply only to certain machines:
 * <ul>
 *   <li>{@code fluid} - vanilla fluids in canister slots; Hub leaves
 *       this {@link Fluids#EMPTY}.</li>
 *   <li>{@code matrices} - canister compression level; consumed by
 *       Tap/Reactor renderers, ignored by Hub/Canister.</li>
 *   <li>{@code topGasketPresent} / {@code bottomGasketPresent} - choral
 *       gasket caps; populated by Hub and Canister.</li>
 *   <li>Stream fields - active gasket flow visualization; populated by
 *       Hub and Canister when goo or vanilla fluid is flowing in.</li>
 * </ul>
 */
public final class SlotState {
    /** True when a canister occupies this slot. */
    public boolean present;
    /** Goo type in the slot, or null if empty / vanilla fluid. */
    public @Nullable GooType type;
    /** Vanilla fluid for non-goo contents; {@link Fluids#EMPTY} when empty or has goo. */
    public Fluid fluid = Fluids.EMPTY;
    /** Fill fraction in [0, 1]. */
    public float fill;
    /** Compression level of the inserted canister (Tap / Reactor only). */
    public int matrices;
    /** True if a top gasket cap is installed. */
    public boolean topGasketPresent;
    /** True if a bottom gasket cap is installed. */
    public boolean bottomGasketPresent;
    /** Active stream goo type, or null if no stream. */
    public @Nullable GooType streamType;
    /** Active stream vanilla fluid; {@link Fluids#EMPTY} if none or stream is goo. */
    public Fluid streamFluid = Fluids.EMPTY;
    /** Stream rate in mB/tick. */
    public float streamRate;
}
