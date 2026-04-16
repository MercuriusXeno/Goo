package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.IGooSource;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Single-tank block-level fluid handler for canister slots. Accepts any
 * registered fluid (goo or vanilla). Only one fluid type at a time.
 *
 * <p>Used by canister and hub block entities for per-slot fluid storage.
 * Replaces the multi-tank ordinal-indexed GooFluidHandler for canister slots.</p>
 */
public class CanisterSlotFluidHandler extends FluidStacksResourceHandler implements IGooSource {

    private final Runnable onChange;
    private final LongSupplier tickSupplier;

    // --- Stream tracking (transient, for rendering incoming fluid) ---

    /** Fluid last inserted via transfer, or null if idle. */
    private @Nullable Fluid streamFluid;

    /** Total mB inserted this tick. */
    private int streamRate;

    /** Game tick of the last insertion event. */
    private long streamTick = -1;

    /** Suppresses callbacks during bulk loads. */
    private boolean suppressCallbacks;

    /**
     * Creates a single-tank handler with the given capacity and change callback.
     *
     * @param capacity total capacity in microblobs (mB)
     * @param onChange  called when contents change
     */
    public CanisterSlotFluidHandler(int capacity, Runnable onChange) {
        this(capacity, onChange, () -> 0);
    }

    /**
     * Creates a single-tank handler with capacity, change callback, and tick supplier.
     *
     * @param capacity     total capacity in microblobs (mB)
     * @param onChange      called when contents change
     * @param tickSupplier  supplies current game tick for stream timing
     */
    public CanisterSlotFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier) {
        super(1, capacity);
        this.onChange = onChange;
        this.tickSupplier = tickSupplier;
    }

    /**
     * Accepts any non-empty fluid if the slot is empty or already holds the same fluid.
     *
     * @param index    always 0
     * @param resource the fluid resource to validate
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) { return false; }
        FluidResource current = getResource(0);
        return current.isEmpty() || current.getFluid() == resource.getFluid();
    }

    /**
     * Full capacity for the single tank.
     *
     * @param index    always 0
     * @param resource the fluid resource
     * @return capacity in mB
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return capacity;
    }

    /** Tracks insertions and notifies owner. Suppressed during bulk loads. */
    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        if (suppressCallbacks) { return; }
        int delta = (int) getAmountAsLong(0) - previousContents.getAmount();
        if (delta > 0) {
            trackInsertion(delta);
        }
        onChange.run();
    }

    /**
     * Records an insertion event for stream rendering.
     * @param delta the amount of fluid inserted this tick in mB
     */
    private void trackInsertion(int delta) {
        long now = tickSupplier.getAsLong();
        if (now != streamTick) {
            streamRate = 0;
            streamTick = now;
        }
        FluidResource res = getResource(0);
        streamFluid = res.isEmpty() ? null : res.getFluid();
        streamRate += (int) delta;
    }

    // --- Stream getters ---

    /**
     * Returns the fluid currently streaming in, or null if no active stream.
     *
     * @param currentTick the current game tick
     * @return the streaming fluid, or null
     */
    public @Nullable Fluid getStreamFluid(long currentTick) {
        return (currentTick - streamTick <= 1) ? streamFluid : null;
    }

    /**
     * Returns the goo type of the active stream, or null if non-goo or inactive.
     *
     * @param currentTick the current game tick
     * @return the streaming goo type, or null
     */
    public @Nullable GooType getStreamGooType(long currentTick) {
        Fluid f = getStreamFluid(currentTick);
        return f != null ? GooFluids.getTypeFromFluid(f) : null;
    }

    /**
     * Returns the transfer rate of the active stream in mB/tick.
     *
     * @param currentTick the current game tick
     * @return mB/tick if stream is active, 0 otherwise
     */
    public int getStreamRate(long currentTick) {
        return (currentTick - streamTick <= 1) ? streamRate : 0;
    }

    // --- Bridge methods ---

    // --- IGooSource bridge (for gasket push compatibility) ---

    /**
     * Returns a GooContents snapshot. Single goo entry if holding goo, empty otherwise.
     *
     * @return goo contents for push operations
     */
    @Override
    public GooContents toGooContents() {
        GooType type = getGooType();
        if (type == null) { return GooContents.EMPTY; }
        return new GooContents(Map.of(type, getAmount()));
    }

    /**
     * Loads from a GooContents snapshot. Reads the first (only) entry.
     *
     * @param contents the goo contents to load
     */
    @Override
    public void loadFrom(GooContents contents) {
        suppressCallbacks = true;
        try {
            if (contents.isEmpty()) {
                set(0, FluidResource.EMPTY, 0);
            } else {
                var entry = contents.getAll().entrySet().iterator().next();
                Fluid fluid = GooFluids.SOURCES.get(entry.getKey()).get();
                set(0, FluidResource.of(fluid),
                    (int) Math.min(entry.getValue(), Integer.MAX_VALUE));
            }
        } finally {
            suppressCallbacks = false;
        }
    }

    // --- CanisterFluidContent bridge ---

    /**
     * Creates a CanisterFluidContent snapshot from the current tank state.
     *
     * @return the fluid content
     */
    public CanisterFluidContent toFluidContent() {
        FluidResource res = getResource(0);
        if (res.isEmpty()) { return CanisterFluidContent.EMPTY; }
        return new CanisterFluidContent(res.getFluid(), (int) getAmountAsLong(0));
    }

    /**
     * Loads fluid from a CanisterFluidContent into the tank. Suppresses callbacks.
     *
     * @param content the content to load
     */
    public void loadFrom(CanisterFluidContent content) {
        suppressCallbacks = true;
        try {
            if (content.isEmpty()) {
                set(0, FluidResource.EMPTY, 0);
            } else {
                set(0, FluidResource.of(content.fluid()),
                    (int) Math.min(content.amount(), Integer.MAX_VALUE));
            }
        } finally {
            suppressCallbacks = false;
        }
    }

    /**
     * Returns the fluid stored in this slot, or Fluids.EMPTY.
     *
     * @return the stored fluid
     */
    public Fluid getFluid() {
        FluidResource res = getResource(0);
        return res.isEmpty() ? Fluids.EMPTY : res.getFluid();
    }

    /**
     * Returns the goo type stored in this slot, or null for non-goo/empty.
     *
     * @return the goo type, or null
     */
    @Nullable
    public GooType getGooType() {
        Fluid f = getFluid();
        return f == Fluids.EMPTY ? null : GooFluids.getTypeFromFluid(f);
    }

    /**
     * Returns the stored amount.
     *
     * @return volume in mB
     */
    public int getAmount() {
        return (int) getAmountAsLong(0);
    }

    /**
     * Returns true if the tank is empty.
     * @return true if the tank holds no fluid
     */
    public boolean isEmpty() {
        return (int) getAmountAsLong(0) == 0;
    }

    /**
     * Returns the total volume (same as getAmount for single-tank).
     *
     * @return volume in mB
     */
    public int totalVolume() {
        return (int) getAmountAsLong(0);
    }

    /**
     * Inserts fluid, handling transaction lifecycle internally.
     *
     * @param fluid    the fluid to insert
     * @param amount   volume in mB
     * @param simulate if true, returns how much would be accepted without mutating
     * @return the amount actually inserted
     */
    public int insertFluid(Fluid fluid, int amount, boolean simulate) {
        if (amount <= 0) { return 0; }
        FluidResource resource = FluidResource.of(fluid);
        try (var tx = Transaction.openRoot()) {
            int inserted = insert(0, resource, amount, tx);
            if (!simulate) { tx.commit(); }
            return inserted;
        }
    }

    /**
     * Convenience: insert goo by type.
     *
     * @param type     the goo type
     * @param amount   volume in mB
     * @param simulate if true, dry run
     * @return the amount actually inserted
     */
    public int insertGoo(GooType type, int amount, boolean simulate) {
        return insertFluid(GooFluids.SOURCES.get(type).get(), amount, simulate);
    }

    /**
     * Extracts fluid, handling transaction lifecycle internally.
     *
     * @param fluid    the fluid to extract
     * @param amount   volume in mB
     * @param simulate if true, dry run
     * @return the amount actually extracted
     */
    public int extractFluid(Fluid fluid, int amount, boolean simulate) {
        if (amount <= 0) { return 0; }
        FluidResource resource = FluidResource.of(fluid);
        try (var tx = Transaction.openRoot()) {
            int extracted = extract(0, resource, amount, tx);
            if (!simulate) { tx.commit(); }
            return extracted;
        }
    }

    /**
     * Convenience: extract goo by type.
     *
     * @param type     the goo type
     * @param amount   volume in mB
     * @param simulate if true, dry run
     * @return the amount actually extracted
     */
    public int extractGoo(GooType type, int amount, boolean simulate) {
        return extractFluid(GooFluids.SOURCES.get(type).get(), amount, simulate);
    }

    /**
     * Updates the capacity. Used when matrix upgrades change.
     *
     * @param newCapacity new total capacity in mB
     */
    public void setCapacity(int newCapacity) {
        this.capacity = newCapacity;
    }
}
