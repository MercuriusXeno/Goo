package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.IGooSource;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Multi-tank fluid handler for goo containers. One tank per {@link GooType}
 * ordinal (15 total), with a single shared capacity across all tanks.
 *
 * <p>Pipe mods see 15 typed slots and can insert/extract the correct goo type
 * at the matching index. The shared capacity ensures total volume never exceeds
 * the container's limit regardless of how many types are stored.</p>
 *
 * @see GooType
 * @see GooContents
 */
public class GooFluidHandler extends FluidStacksResourceHandler implements IGooSource {

    private static final int TANK_COUNT = GooType.values().length;

    private final Runnable onChange;
    private final LongSupplier tickSupplier;

    // --- Stream tracking (transient, for rendering incoming goo) ---

    /** Goo type last inserted via gasket transfer, or null if idle. */
    private @Nullable GooType streamType;

    /** Total mB inserted this tick (accumulates across multiple types). */
    private int streamRate;

    /** Game tick of the last insertion event. */
    private long streamTick = -1;

    /** Suppresses all side-effect callbacks (stream tracking + onChange) during bulk loads. */
    private boolean suppressCallbacks;

    /**
     * Creates a handler with shared capacity and a change callback.
     *
     * @param capacity total shared capacity in microblobs (mB)
     * @param onChange  called when contents change (e.g. markDirtyAndSync)
     */
    public GooFluidHandler(int capacity, Runnable onChange) {
        this(capacity, onChange, () -> 0L);
    }

    /**
     * Creates a handler with shared capacity, change callback, and tick supplier
     * for stream tracking.
     *
     * @param capacity     total shared capacity in microblobs (mB)
     * @param onChange      called when contents change (e.g. markDirtyAndSync)
     * @param tickSupplier  supplies the current game tick for stream timing
     */
    public GooFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier) {
        super(TANK_COUNT, capacity);
        this.onChange = onChange;
        this.tickSupplier = tickSupplier;
    }

    /**
     * Only the goo fluid matching this tank index is valid.
     * Index maps to {@link GooType#ordinal()}.
     *
     * @param index    tank index (0-14)
     * @param resource the fluid resource to validate
     * @return true if the resource's fluid matches the tank's goo type
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) { return false; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index;
    }

    /**
     * Returns the effective capacity for a slot, accounting for shared capacity.
     * Each slot's effective capacity is the total capacity minus volume in all
     * other slots. The default insert() clamps at {@code getCapacity - currentAmount},
     * yielding the correct shared remaining space.
     *
     * @param index    tank index
     * @param resource the fluid resource
     * @return effective capacity for this slot in mB
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        int otherVolume = 0;
        for (int i = 0; i < size(); i++) {
            if (i != index) { otherVolume += getAmountAsInt(i); }
        }
        return capacity - otherVolume;
    }

    /** Detects insertions for stream tracking, then notifies the owner. Suppressed during bulk loads.
     *
     * @param index            the tank index
     * @param previousContents the previous fluid stack contents
     */
    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        if (suppressCallbacks) { return; }
        long delta = getAmountAsLong(index) - previousContents.getAmount();
        if (delta > 0) {
            trackInsertion(index, delta);
        }
        onChange.run();
    }

    /** Records an insertion event for stream rendering.
     *
     * @param index the tank index that received goo
     * @param delta the volume inserted in mB
     */
    private void trackInsertion(int index, long delta) {
        long now = tickSupplier.getAsLong();
        if (now != streamTick) {
            streamRate = 0;
            streamTick = now;
        }
        streamType = GooType.values()[index];
        streamRate += (int) delta;
    }

    // --- Stream getters (queried by BER extractRenderState) ---

    /**
     * Returns the goo type currently streaming in, or null if no active stream.
     * A stream is considered active if goo was inserted within the last tick.
     *
     * @param currentTick the current game tick
     * @return the streaming goo type, or null
     */
    public @Nullable GooType getStreamType(long currentTick) {
        return (currentTick - streamTick <= 1) ? streamType : null;
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

    /**
     * Creates a {@link GooContents} snapshot from the current tank state.
     * Used for rendering, serialization, and backward-compatible APIs.
     *
     * @return immutable GooContents reflecting current volumes
     */
    @Override
    public GooContents toGooContents() {
        Map<GooType, Long> map = collectNonEmptyTanks();
        return map.isEmpty() ? GooContents.EMPTY : new GooContents(map);
    }

    /** Builds a map of all goo types with non-zero volume.
     *
     * @return the non-empty tank volumes keyed by goo type
     */
    private Map<GooType, Long> collectNonEmptyTanks() {
        GooType[] types = GooType.values();
        Map<GooType, Long> map = new EnumMap<>(GooType.class);
        for (int i = 0; i < types.length; i++) {
            long amount = getAmountAsLong(i);
            if (amount > 0) { map.put(types[i], amount); }
        }
        return map;
    }

    /**
     * Loads volumes from a {@link GooContents} snapshot into the tanks.
     * Clears all tanks first, then sets each type's volume. Suppresses
     * all callbacks (stream tracking and onChange) - caller should sync after loading.
     *
     * @param contents the contents to load from
     */
    @Override
    public void loadFrom(GooContents contents) {
        suppressCallbacks = true;
        try {
            applyAllTanks(contents);
        } finally {
            suppressCallbacks = false;
        }
    }

    /** Sets each tank's contents from the snapshot, clearing tanks with zero volume.
     *
     * @param contents the goo contents to load
     */
    private void applyAllTanks(GooContents contents) {
        GooType[] types = GooType.values();
        for (int i = 0; i < types.length; i++) {
            applyTank(i, types[i], contents.getVolume(types[i]));
        }
    }

    /** Sets a single tank from a volume, clearing it if zero.
     *
     * @param index  the tank index
     * @param type   the goo type
     * @param volume the volume to set
     */
    private void applyTank(int index, GooType type, long volume) {
        if (volume > 0) {
            Fluid fluid = GooFluids.SOURCES.get(type).get();
            set(index, FluidResource.of(fluid), (int) Math.min(volume, Integer.MAX_VALUE));
        } else {
            set(index, FluidResource.EMPTY, 0);
        }
    }

    /** Returns the total volume across all 15 tanks.
     *
     * @return the long value
     */
    public long totalVolume() {
        long total = 0;
        for (int i = 0; i < size(); i++) {
            total += getAmountAsLong(i);
        }
        return total;
    }

    /** Returns true if all tanks are empty.
     *
     * @return true if empty
     */
    @Override
    public boolean isEmpty() {
        return totalVolume() == 0;
    }

    /**
     * Updates the shared capacity. Used when matrix upgrades change.
     *
     * @param newCapacity new total capacity in mB
     */
    public void setCapacity(int newCapacity) {
        this.capacity = newCapacity;
    }

    // --- Convenience methods for internal use ---

    /**
     * Inserts goo by type, handling transaction lifecycle internally.
     *
     * @param type     the goo type to insert
     * @param amount   volume in mB to insert
     * @param simulate if true, returns how much would be accepted without mutating
     * @return the amount actually inserted (or that would be)
     */
    public int insertGoo(GooType type, int amount, boolean simulate) {
        if (amount <= 0) { return 0; }
        int index = type.ordinal();
        FluidResource resource = FluidResource.of(GooFluids.SOURCES.get(type).get());
        try (var tx = Transaction.openRoot()) {
            int inserted = insert(index, resource, amount, tx);
            if (!simulate) { tx.commit(); }
            return inserted;
        }
    }

    /**
     * Extracts goo by type, handling transaction lifecycle internally.
     *
     * @param type     the goo type to extract
     * @param amount   volume in mB to extract
     * @param simulate if true, returns how much would be extracted without mutating
     * @return the amount actually extracted (or that would be)
     */
    public int extractGoo(GooType type, int amount, boolean simulate) {
        if (amount <= 0) { return 0; }
        int index = type.ordinal();
        FluidResource resource = FluidResource.of(GooFluids.SOURCES.get(type).get());
        try (var tx = Transaction.openRoot()) {
            int extracted = extract(index, resource, amount, tx);
            if (!simulate) { tx.commit(); }
            return extracted;
        }
    }

    /**
     * Returns the volume of a specific goo type.
     *
     * @param type the goo type to query
     * @return volume in mB, or 0 if absent
     */
    public long getVolume(GooType type) {
        return getAmountAsLong(type.ordinal());
    }

    /**
     * Returns the largest goo type by volume, or null if empty.
     *
     * @return the dominant goo type, or null
     */
    @Nullable
    public GooType largestType() {
        return toGooContents().largestType();
    }
}
