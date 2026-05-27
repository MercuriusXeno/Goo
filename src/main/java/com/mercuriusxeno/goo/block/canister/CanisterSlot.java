package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import java.util.function.LongSupplier;

/**
 * One slot in a multi-canister block (CanisterBlockEntity or HubBlockEntity).
 * Owns its canister ItemStack, live fluid handler, gasket pusher, voxel shape,
 * and the transient stream-visualization snapshot. Per-slot operations live
 * here as instance methods rather than as statics keyed on (BE, slot).
 *
 * <p>Two callbacks are injected by the parent container:</p>
 * <ul>
 *   <li>{@code onSync} — fires after transient state changes (stream snapshot
 *       updates) so the parent can mark dirty and sync to clients.</li>
 *   <li>{@code onStructureChanged} — fires when canister occupancy changes
 *       (insert/remove). Parent re-ORs its composite shape, invalidates
 *       capabilities, and syncs.</li>
 * </ul>
 */
public final class CanisterSlot {

    private static final String TAG_CANISTER = "canister";
    private static final String TAG_STREAM_TYPE = "streamType";
    private static final String TAG_STREAM_FLUID = "streamFluid";
    private static final String TAG_STREAM_RATE = "streamRate";
    private static final String TAG_STREAM_TICK = "streamTick";
    private static final int INVALID_ORDINAL = -1;
    /** Stream snapshot is considered fresh for this many ticks past write. */
    private static final long STREAM_FRESHNESS_TICKS = 1L;

    private final int index;
    private final VoxelShape filledShape;
    private final Runnable onSync;
    private final Runnable onStructureChanged;

    private ItemStack canister = ItemStack.EMPTY;
    private @Nullable CanisterSlotFluidHandler handler;
    private @Nullable IGasketPusher pusher;
    private @Nullable VoxelShape shape;
    private @Nullable GooType streamType;
    private @Nullable Fluid streamFluid;
    private int streamRate;
    private long streamTick;

    /**
     * Creates an empty slot bound to the given index and parent callbacks.
     *
     * @param index              the slot's position in the parent grid
     * @param filledShape        the voxel shape this slot occupies when a canister is present
     * @param onSync             called when transient state mutates (stream snapshot)
     * @param onStructureChanged called when canister occupancy mutates (insert/remove)
     */
    public CanisterSlot(int index, VoxelShape filledShape,
            Runnable onSync, Runnable onStructureChanged) {
        this.index = index;
        this.filledShape = filledShape;
        this.onSync = onSync;
        this.onStructureChanged = onStructureChanged;
    }

    // --- Accessors ---

    /** @return the slot's grid index */
    public int index() { return index; }

    /** @return the canister stack in this slot, or EMPTY if unoccupied */
    public ItemStack canister() { return canister; }

    /** @return the live fluid handler, or null if the slot is empty */
    public @Nullable CanisterSlotFluidHandler handler() { return handler; }

    /** @return the active gasket pusher, or null if none */
    public @Nullable IGasketPusher pusher() { return pusher; }

    /** @return this slot's voxel shape, or null if empty */
    public @Nullable VoxelShape shape() { return shape; }

    /** @return true if this slot has no canister */
    public boolean isEmpty() { return canister.isEmpty(); }

    // --- Structural mutation (canister insert / remove / replace) ---

    /**
     * Replaces this slot's canister stack and fires the structure-changed
     * callback so the parent container recomputes its composite shape and
     * marks dirty.
     *
     * @param stack the new canister stack (use ItemStack.EMPTY to vacate)
     */
    public void setCanister(ItemStack stack) {
        this.canister = stack;
        this.shape = stack.isEmpty() ? null : filledShape;
        onStructureChanged.run();
    }

    /**
     * Sets the live fluid handler for this slot. Called during slot wiring
     * after a canister is placed and during deserialization.
     *
     * @param newHandler the handler, or null to clear
     */
    public void setHandler(@Nullable CanisterSlotFluidHandler newHandler) {
        this.handler = newHandler;
    }

    /**
     * Builds a fresh fluid handler from this slot's canister and assigns it.
     * Loads the canister's fluid content into the new handler. The handler's
     * onChange callback flows through {@link #onHandlerChanged(long)} so any
     * fluid mutation persists back to the canister, refreshes the stream
     * snapshot, and triggers a client sync. No-op when the slot is empty.
     *
     * @param gameTime supplier of the level's current game tick
     */
    public void buildHandler(LongSupplier gameTime) {
        if (canister.isEmpty()) {
            handler = null;
            return;
        }
        int capacity = ContainerCapacity.canisterCapacity(
                GooEnchantments.getCompressionLevel(canister));
        CanisterSlotFluidHandler newHandler = new CanisterSlotFluidHandler(capacity,
                () -> onHandlerChanged(gameTime.getAsLong()),
                gameTime);
        newHandler.loadFrom(CanisterItem.getFluidContent(canister));
        this.handler = newHandler;
    }

    private void onHandlerChanged(long currentTick) {
        syncHandlerToStack();
        snapshotStream(currentTick);
        // Always notify the parent so client-tracked state reflects the
        // new fluid amount. Pure extracts (e.g. reactor consuming inputs)
        // produce no stream snapshot, and without this call clients would
        // never see the drained amount in the HUD or BER.
        onSync.run();
    }

    /**
     * Sets the active gasket pusher. Disposes any prior pusher first.
     *
     * @param newPusher the pusher, or null to clear
     */
    public void setPusher(@Nullable IGasketPusher newPusher) {
        disposePusher();
        this.pusher = newPusher;
    }

    /** Disposes the current pusher (if any) and clears the field. */
    public void disposePusher() {
        if (pusher != null) {
            pusher.dispose();
            pusher = null;
        }
    }

    /**
     * Clears all per-slot state: canister, handler, pusher, shape, stream.
     * Fires the structure-changed callback. Called on slot vacate.
     */
    public void clear() {
        disposePusher();
        handler = null;
        canister = ItemStack.EMPTY;
        shape = null;
        clearStreamSilently();
        onStructureChanged.run();
    }

    private void clearStreamSilently() {
        streamType = null;
        streamFluid = null;
        streamRate = 0;
        streamTick = 0;
    }

    // --- Fluid ops ---

    /** @return the slot's fluid content, or EMPTY if no handler */
    public CanisterFluidContent fluidContent() {
        return handler != null ? handler.toFluidContent() : CanisterFluidContent.EMPTY;
    }

    /**
     * Inserts fluid into this slot's handler.
     *
     * @param fluid  the fluid to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertFluid(Fluid fluid, int volume) {
        return handler != null ? handler.insertFluid(fluid, volume, false) : 0;
    }

    /**
     * Extracts fluid from this slot's handler.
     *
     * @param fluid     the fluid to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractFluid(Fluid fluid, int requested) {
        return handler != null ? handler.extractFluid(fluid, requested, false) : 0;
    }

    /**
     * Inserts goo by type.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertGoo(GooType type, int volume) {
        return insertFluid(GooFluids.SOURCES.get(type).get(), volume);
    }

    /**
     * Extracts goo by type.
     *
     * @param type      the goo type
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractGoo(GooType type, int requested) {
        return extractFluid(GooFluids.SOURCES.get(type).get(), requested);
    }

    /** @return true if the canister can accept more fluid (has remaining capacity) */
    public boolean canAccept() {
        if (canister.isEmpty() || handler == null) {
            return false;
        }
        int compression = GooEnchantments.getCompressionLevel(canister);
        return handler.totalVolume() < ContainerCapacity.canisterCapacity(compression);
    }

    /** @return the slot's canister capacity in mB, or 0 if no canister installed */
    public int capacity() {
        if (canister.isEmpty()) {
            return 0;
        }
        return ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(canister));
    }

    // --- Stream visualization snapshot ---

    /**
     * Pulls fresh stream state from the handler into the snapshot fields and
     * fires the sync callback so clients see the update on the next tick.
     * No-op if the handler is null or reports no active stream.
     *
     * @param currentTick the current game tick (must be the level's game time)
     */
    public void snapshotStream(long currentTick) {
        if (handler == null) {
            return;
        }
        GooType t = handler.getStreamGooType(currentTick);
        Fluid f = handler.getStreamFluid(currentTick);
        int r = handler.getStreamRate(currentTick);
        if (t == null && f == null && r <= 0) {
            return;
        }
        streamType = t;
        streamFluid = f;
        streamRate = r;
        streamTick = currentTick;
    }

    /**
     * @param currentTick the current game tick
     * @return the snapshot's goo type if fresh, otherwise null
     */
    public @Nullable GooType getStreamType(long currentTick) {
        return isStreamFresh(currentTick) ? streamType : null;
    }

    /**
     * @param currentTick the current game tick
     * @return the snapshot's vanilla fluid if fresh, otherwise null
     */
    public @Nullable Fluid getStreamFluid(long currentTick) {
        return isStreamFresh(currentTick) ? streamFluid : null;
    }

    /**
     * @param currentTick the current game tick
     * @return the snapshot's transfer rate in mB/tick if fresh, otherwise 0
     */
    public int getStreamRate(long currentTick) {
        return isStreamFresh(currentTick) ? streamRate : 0;
    }

    private boolean isStreamFresh(long currentTick) {
        return currentTick - streamTick <= STREAM_FRESHNESS_TICKS;
    }

    // --- Item-stack metadata helpers ---

    /**
     * Updates the metadata on this slot's canister and fires onSync.
     * No-op if the slot is empty.
     *
     * @param metadata the new metadata to apply
     */
    public void setMetadata(CanisterMetadata metadata) {
        if (canister.isEmpty()) {
            return;
        }
        CanisterItem.setMetadata(canister, metadata);
        onSync.run();
    }

    /**
     * Replaces gasket UUIDs on this slot's canister with fresh ones, clearing
     * partner refs. Preserves gasket presence (non-null UUID = gasket installed)
     * while avoiding duplicate UUIDs across creative-mode copies. No-op if
     * empty or no gaskets are installed.
     */
    public void stripGaskets() {
        if (canister.isEmpty()) {
            return;
        }
        CanisterMetadata meta = CanisterItem.getMetadata(canister);
        if (meta.topGasketId() != null || meta.bottomGasketId() != null) {
            CanisterItem.setMetadata(canister, meta.withFreshGasketIds());
        }
    }

    /**
     * Exports this slot's fluid content and metadata to an item-component
     * builder. Used when picking up a single-canister block to preserve
     * its contents on the resulting item.
     *
     * @param builder the component map builder to populate
     */
    public void exportComponents(DataComponentMap.Builder builder) {
        if (canister.isEmpty()) {
            return;
        }
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        if (!content.isEmpty()) {
            builder.set(GooDataComponents.CANISTER_FLUID_CONTENT.get(), content);
        }
        CanisterMetadata meta = CanisterItem.getMetadata(canister);
        if (meta.hasData()) {
            builder.set(GooDataComponents.CANISTER_METADATA.get(), meta);
        }
    }

    /**
     * Writes the handler's current fluid state back onto the canister
     * ItemStack so picking up the canister preserves its contents.
     * No-op if empty or no handler.
     */
    public void syncHandlerToStack() {
        if (canister.isEmpty() || handler == null) {
            return;
        }
        CanisterItem.setFluidContent(canister, handler.toFluidContent());
    }

    // --- Persistence ---

    /**
     * Serializes this slot's canister and stream snapshot into the given tag.
     * Empty slots write nothing. Caller is responsible for keying the tag
     * by slot index in a parent compound.
     *
     * @param tag the per-slot compound to write into
     */
    public void save(CompoundTag tag) {
        if (!canister.isEmpty()) {
            tag.put(TAG_CANISTER,
                    ItemStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, canister).getOrThrow());
        }
        if (streamType != null) {
            tag.putInt(TAG_STREAM_TYPE, streamType.ordinal());
        } else if (streamFluid != null) {
            FluidStack marker = new FluidStack(streamFluid, 1);
            tag.put(TAG_STREAM_FLUID,
                    FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, marker).getOrThrow());
        }
        if (streamRate != 0) {
            tag.putInt(TAG_STREAM_RATE, streamRate);
        }
        if (streamTick != 0L) {
            tag.putLong(TAG_STREAM_TICK, streamTick);
        }
    }

    /**
     * Restores this slot's canister and stream snapshot from the given tag.
     * Resets all state if the tag is absent. Does not fire callbacks (caller
     * is in load context and will handle invalidation).
     *
     * @param tag the per-slot compound to read from (may be empty)
     */
    public void load(CompoundTag tag) {
        canister = readCanister(tag);
        shape = canister.isEmpty() ? null : filledShape;
        streamType = readStreamType(tag);
        streamFluid = readStreamFluid(tag);
        streamRate = tag.getIntOr(TAG_STREAM_RATE, 0);
        streamTick = tag.getLongOr(TAG_STREAM_TICK, 0L);
    }

    private static ItemStack readCanister(CompoundTag tag) {
        Tag inner = tag.get(TAG_CANISTER);
        if (inner == null) {
            return ItemStack.EMPTY;
        }
        return ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, inner)
                .result().orElse(ItemStack.EMPTY);
    }

    private static @Nullable GooType readStreamType(CompoundTag tag) {
        int ordinal = tag.getIntOr(TAG_STREAM_TYPE, INVALID_ORDINAL);
        GooType[] types = GooType.values();
        return ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
    }

    private static @Nullable Fluid readStreamFluid(CompoundTag tag) {
        Tag inner = tag.get(TAG_STREAM_FLUID);
        if (inner == null) {
            return null;
        }
        FluidStack fs = FluidStack.CODEC.parse(NbtOps.INSTANCE, inner)
                .result().orElse(FluidStack.EMPTY);
        return fs.isEmpty() ? null : fs.getFluid();
    }
}
