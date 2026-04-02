package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Block entity for the multi-canister block. Holds up to 9 canister items
 * in a 3x3 grid within a single block space. Each sub-canister retains its
 * own gasket UUIDs for the transport network.
 */
public class CanisterBlockEntity extends BlockEntity implements ISlottedGooContainer, IGasketHolder {

    /** Maximum number of canister slots in the 3x3 grid. */
    public static final int MAX_SLOTS = 9;
    /** Center slot index in the 3x3 grid (default placement target). */
    private static final int CENTER_SLOT = 4;

    /** Returns true if the slot index is outside the valid [0, MAX_SLOTS) range. */
    private boolean isSlotOutOfRange(int slot) {
        return slot < 0 || slot >= MAX_SLOTS;
    }

    /** Canister stacks stored in the 3x3 grid, indexed 0-8. */
    private final NonNullList<ItemStack> canisters = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);

    /**
     * Pending goo contents from applyImplicitComponents. Resolved to a specific
     * slot by {@link #assignPendingToSlot(int)} after placement.
     */
    private @Nullable GooContents pendingGooContents;

    /**
     * Pending metadata from applyImplicitComponents. Resolved to a specific
     * slot by {@link #assignPendingToSlot(int)} after placement.
     */
    private @Nullable CanisterMetadata pendingMetadata;

    /** Owner UUID: set when placed by a player. Used for tuner ownership checks. */
    private @Nullable UUID ownerUuid;

    /** Cached composite shape of all occupied slots. Null when dirty. */
    private @Nullable VoxelShape cachedShape;

    /** Live fluid handlers per slot - non-null when the slot is occupied. */
    private final @Nullable GooFluidHandler[] slotHandlers = new GooFluidHandler[MAX_SLOTS];

    /** Per-slot gasket pushers - non-null when the slot has an active bottom gasket partner. */
    private final @Nullable IGasketPusher[] slotPushers = new IGasketPusher[MAX_SLOTS];

    /** Provides access to the gasket registry without a ServerLevel at call sites. */
    private @Nullable IGasketRegistryAccess gasketRegistryAccess;

    // --- Per-slot stream state (synced to client for BER rendering) ---

    /** Per-slot goo type of the active incoming stream, or null if idle. */
    private final @Nullable GooType[] slotStreamType = new GooType[MAX_SLOTS];

    /** Per-slot transfer rate of the active stream in mB/tick. */
    private final int[] slotStreamRate = new int[MAX_SLOTS];

    /** Per-slot game tick of the last stream event. */
    private final long[] slotStreamTick = new long[MAX_SLOTS];

    /** Creates a new canister block entity at the given position. */
    public CanisterBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CANISTER.get(), pos, state);
    }


    // --- Slot access ---

    /** Returns the canister stack in the given slot without removing it. */
    @Override
    public ItemStack getCanister(int slot) {
        if (isSlotOutOfRange(slot)) return ItemStack.EMPTY;
        return canisters.get(slot);
    }

    /** Delegates to {@link #markDirtyAndSync()} for interface callers. */
    @Override
    public void onSlotChanged() {
        markDirtyAndSync();
    }

    /** {@inheritDoc} Reads from the live handler if available. */
    @Override
    public GooContents getSlotGooContents(int slot) {
        GooFluidHandler h = isSlotOutOfRange(slot) ? null : slotHandlers[slot];
        return h != null ? h.toGooContents() : GooContents.EMPTY;
    }

    /** {@inheritDoc} Delegates to the slot's live handler. */
    @Override
    public long insertGoo(int slot, GooType incomingType, long volume) {
        GooFluidHandler h = isSlotOutOfRange(slot) ? null : slotHandlers[slot];
        if (h == null) return 0L;
        return h.insertGoo(incomingType, (int) Math.min(volume, Integer.MAX_VALUE), false);
    }

    /** {@inheritDoc} Delegates to the slot's live handler. */
    @Override
    public long extractGoo(int slot, GooType type, long requested) {
        GooFluidHandler h = isSlotOutOfRange(slot) ? null : slotHandlers[slot];
        if (h == null) return 0L;
        return h.extractGoo(type, (int) Math.min(requested, Integer.MAX_VALUE), false);
    }

    /** {@inheritDoc} Checks the slot's live handler for remaining capacity. */
    @Override
    public boolean canAccept(int slot) {
        if (isSlotOutOfRange(slot) || canisters.get(slot).isEmpty()) return false;
        GooFluidHandler h = slotHandlers[slot];
        if (h == null) return false;
        int compression = getCompressionLevel(canisters.get(slot));
        return h.totalVolume() < ContainerCapacity.canisterCapacity(compression);
    }

    /** Returns true if the given slot can accept a canister insertion. */
    private boolean canInsertAt(int slot, ItemStack canisterStack) {
        if (isSlotOutOfRange(slot)) return false;
        if (!(canisterStack.getItem() instanceof CanisterItem)) return false;
        return canisters.get(slot).isEmpty();
    }

    /**
     * Inserts a canister item into the given slot, preserving gasket UUIDs.
     * Returns true if inserted, false if slot is occupied or stack is invalid.
     */
    public boolean insertCanister(int slot, ItemStack canisterStack) {
        return insertCanister(slot, canisterStack, false);
    }

    /**
     * Inserts a canister item into the given slot.
     * When stripGaskets is true (creative-mode duplication), gasket UUIDs and
     * partners are cleared so the placed copy doesn't collide with the original.
     */
    public boolean insertCanister(int slot, ItemStack canisterStack, boolean stripGaskets) {
        if (!canInsertAt(slot, canisterStack)) return false;
        ItemStack copy = canisterStack.copyWithCount(1);
        if (stripGaskets) {
            stripGasketMetadata(copy);
        }
        canisters.set(slot, copy);
        slotHandlers[slot] = createSlotHandler(slot);
        rebuildSlotPusher(slot);
        onSlotStructureChanged(slot, true);
        return true;
    }

    /**
     * Removes the canister from the given slot.
     * Returns the removed stack, or EMPTY if slot was empty.
     */
    public ItemStack removeCanister(int slot) {
        if (isSlotOutOfRange(slot)) return ItemStack.EMPTY;
        if (canisters.get(slot).isEmpty()) return ItemStack.EMPTY;
        disposeSlotPusher(slot);
        syncSlotToItemStack(slot);
        slotHandlers[slot] = null;
        deregisterSlotGaskets(slot);
        ItemStack removed = canisters.get(slot).copy();
        canisters.set(slot, ItemStack.EMPTY);
        onSlotStructureChanged(slot, false);
        return removed;
    }

    /**
     * Shared lifecycle after a slot's canister changes. Invalidates shape and
     * capabilities, manages gasket registration, and syncs to clients.
     *
     * @param slot     the slot that changed
     * @param register true to register gaskets (insert), false to skip (remove already deregistered)
     */
    private void onSlotStructureChanged(int slot, boolean register) {
        invalidateShape();
        BlockEntitySync.invalidateCapabilities(this);
        if (register) {
            registerSlotGaskets(slot);
        }
        markDirtyAndSync();
    }

    // --- Owner ---

    /** Returns the owner UUID, or null if unowned. */
    @Nullable
    public UUID getOwner() {
        return ownerUuid;
    }

    /** Sets the owner UUID (called when placed by a player). */
    public void setOwner(UUID owner) {
        ownerUuid = owner;
        markDirtyAndSync();
    }

    // --- Utility ---

    /** Returns true if any slot contains a canister. */
    public boolean hasAnyCanister() {
        for (ItemStack stack : canisters) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    // --- Pending contents (from item placement) ---

    /**
     * Assigns pending contents (from the placed item's data component) to the
     * given slot. Creates a canister ItemStack in that slot from the pending data.
     * Also handles fresh empty canisters (no data component, pendingContents is null).
     */
    public void assignPendingToSlot(int slot) {
        if (isSlotOutOfRange(slot)) slot = CENTER_SLOT;
        canisters.set(slot, buildCanisterFromPending());
        slotHandlers[slot] = createSlotHandler(slot);
        onSlotStructureChanged(slot, true);
    }

    /**
     * Assigns canister contents to a slot by reading directly from the source
     * ItemStack. Used during initial block placement because applyImplicitComponents
     * has not yet run - the pending fields are still null at that point.
     *
     * @param slot         target grid slot (clamped to center if out of range)
     * @param source       the held canister ItemStack being placed
     * @param stripGaskets true to clear gasket UUIDs (creative-mode duplication)
     */
    public void assignFromItemStack(int slot, ItemStack source, boolean stripGaskets) {
        if (isSlotOutOfRange(slot)) slot = CENTER_SLOT;
        ItemStack built = buildCanisterFromStack(source);
        if (stripGaskets) {
            stripGasketMetadata(built);
        }
        canisters.set(slot, built);
        slotHandlers[slot] = createSlotHandler(slot);
        onSlotStructureChanged(slot, true);
    }

    /**
     * Replaces gasket UUIDs with fresh ones and clears partner refs.
     * Preserves the physical gasket presence (non-null UUID = gasket installed)
     * while avoiding duplicate UUIDs across creative-mode copies.
     */
    private static void stripGasketMetadata(ItemStack stack) {
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        if (meta.topGasketId() != null || meta.bottomGasketId() != null) {
            CanisterItem.setMetadata(stack, meta.withFreshGasketIds());
        }
    }

    /** Builds a canister stack from pending placement data and clears the pending state. */
    private ItemStack buildCanisterFromPending() {
        ItemStack canisterStack = new ItemStack(GooItems.CANISTER.get());
        if (pendingGooContents != null && !pendingGooContents.isEmpty()) {
            CanisterItem.setGooContents(canisterStack, pendingGooContents);
        }
        if (pendingMetadata != null && pendingMetadata.hasData()) {
            CanisterItem.setMetadata(canisterStack, pendingMetadata);
        }
        pendingGooContents = null;
        pendingMetadata = null;
        return canisterStack;
    }

    /**
     * Builds a canister stack by reading goo data directly from a source ItemStack.
     * Bypasses the pending fields so placement works before applyImplicitComponents runs.
     */
    private ItemStack buildCanisterFromStack(ItemStack source) {
        ItemStack canisterStack = new ItemStack(GooItems.CANISTER.get());
        GooContents goo = CanisterItem.getGooContents(source);
        if (!goo.isEmpty()) {
            CanisterItem.setGooContents(canisterStack, goo);
        }
        CanisterMetadata meta = CanisterItem.getMetadata(source);
        if (meta.hasData()) {
            CanisterItem.setMetadata(canisterStack, meta);
        }
        return canisterStack;
    }

    // --- Dynamic VoxelShape ---

    /** Returns the cached composite shape of all occupied slots. */
    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    /** Computes the union of occupied slot shapes. */
    private VoxelShape computeShape() {
        VoxelShape result = Shapes.empty();
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!canisters.get(i).isEmpty()) {
                result = Shapes.or(result, CanisterBlock.slotShape(i));
            }
        }
        return result.isEmpty() ? CanisterBlock.slotShape(CENTER_SLOT) : result;
    }

    /** Invalidates the cached shape (call after slot changes). */
    private void invalidateShape() {
        cachedShape = null;
    }

    // --- Sync ---

    /** Marks dirty and sends sync packet to tracking clients. */
    private void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    // --- Per-slot fluid handlers ---

    /**
     * Creates a live {@link GooFluidHandler} for the given slot, loaded from
     * the canister ItemStack's current {@link GooContents}. The onChange callback
     * syncs handler state back to the ItemStack data component.
     */
    private GooFluidHandler createSlotHandler(int slot) {
        ItemStack stack = canisters.get(slot);
        int compression = getCompressionLevel(stack);
        int capacity = (int) ContainerCapacity.canisterCapacity(compression);
        GooFluidHandler handler = new GooFluidHandler(capacity,
            () -> syncSlotToItemStack(slot),
            () -> level != null ? level.getGameTime() : 0L);
        handler.loadFrom(CanisterItem.getGooContents(stack));
        return handler;
    }

    /** Reads the Compression enchantment level from a canister ItemStack. */
    private static int getCompressionLevel(ItemStack stack) {
        return GooEnchantments.getCompressionLevel(stack);
    }

    /** Writes the slot handler's current state back to the canister ItemStack. */
    private void syncSlotToItemStack(int slot) {
        ItemStack stack = canisters.get(slot);
        if (stack.isEmpty() || slotHandlers[slot] == null) return;
        CanisterItem.setGooContents(stack, slotHandlers[slot].toGooContents());
        snapshotSlotStream(slot);
        markDirtyAndSync();
    }

    /** Copies the handler's transient stream state to block entity fields for sync. */
    private void snapshotSlotStream(int slot) {
        GooFluidHandler h = slotHandlers[slot];
        if (h == null) return;
        long tick = level != null ? level.getGameTime() : 0L;
        slotStreamType[slot] = h.getStreamType(tick);
        slotStreamRate[slot] = h.getStreamRate(tick);
        slotStreamTick[slot] = tick;
    }

    /** Returns the live fluid handler for a slot, or null if the slot is empty. */
    public @Nullable GooFluidHandler getSlotFluidHandler(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) return null;
        return slotHandlers[slot];
    }

    /** Returns the stream goo type for a slot, or null if no active stream. */
    public @Nullable GooType getSlotStreamType(int slot, long currentTick) {
        if (slot < 0 || slot >= MAX_SLOTS) return null;
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamType[slot] : null;
    }

    /** Returns the stream rate for a slot in mB/tick, or 0 if no active stream. */
    public int getSlotStreamRate(int slot, long currentTick) {
        if (slot < 0 || slot >= MAX_SLOTS) return 0;
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamRate[slot] : 0;
    }

    /** Rebuilds slot handlers for all occupied slots (used after deserialization). */
    private void rebuildAllSlotHandlers() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotHandlers[i] = canisters.get(i).isEmpty() ? null : createSlotHandler(i);
        }
    }

    // --- Per-slot gasket pushers ---

    /**
     * Rebuilds the gasket pusher for a slot. Creates a pusher if the slot has
     * a live handler and its bottom gasket has a partner; otherwise disposes
     * and nulls the existing pusher.
     */
    private void rebuildSlotPusher(int slot) {
        disposeSlotPusher(slot);
        GooFluidHandler handler = slotHandlers[slot];
        if (handler == null) return;
        ItemStack stack = canisters.get(slot);
        if (stack.isEmpty()) return;
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        if (meta.bottomGasketId() == null || meta.bottomPartner() == null) return;

        final int s = slot;
        GasketPusher pusher = new GasketPusher(
            handler,
            () -> CanisterItem.getMetadata(canisters.get(s)).bottomGasketId(),
            () -> CanisterItem.getMetadata(canisters.get(s)).bottomPartner(),
            this::getLevel,
            this::getBlockPos,
            () -> syncSlotToItemStack(s),
            gasketRegistryAccess);
        pusher.rebuildCache();
        slotPushers[slot] = pusher;
    }

    /** Disposes and nulls the pusher for the given slot. */
    private void disposeSlotPusher(int slot) {
        IGasketPusher existing = slotPushers[slot];
        if (existing != null) {
            existing.dispose();
            slotPushers[slot] = null;
        }
    }

    /** Rebuilds pushers for all occupied slots (used after deserialization or setLevel). */
    private void rebuildAllSlotPushers() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            rebuildSlotPusher(i);
        }
    }

    /** Static tick entrypoint for the block entity ticker. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CanisterBlockEntity be) {
        be.serverTick();
    }

    /** Ticks all active slot pushers. */
    private void serverTick() {
        for (IGasketPusher pusher : slotPushers) {
            if (pusher != null) pusher.tick();
        }
    }

    // --- Gasket registration ---

    /** Registers gasket locations for all occupied slots. */
    private void registerAllGaskets() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!canisters.get(i).isEmpty()) {
                registerSlotGaskets(i);
            }
        }
    }

    /** Registers gasket locations for the slot's canister in the gasket registry. Only registers faces that have gasket UUIDs (opt-in via choral gasket installation). */
    private void registerSlotGaskets(int slot) {
        if (gasketRegistryAccess == null || !(level instanceof ServerLevel serverLevel)) return;
        ItemStack stack = canisters.get(slot);
        if (stack.isEmpty()) return;

        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        GasketRegistry registry = gasketRegistryAccess.get();
        ResourceKey<Level> dimension = serverLevel.dimension();
        if (meta.topGasketId() != null) {
            registry.updateLocation(meta.topGasketId(),
                new GasketLocation(dimension, worldPosition, true, slot));
        }
        if (meta.bottomGasketId() != null) {
            registry.updateLocation(meta.bottomGasketId(),
                new GasketLocation(dimension, worldPosition, false, slot));
        }
    }

    /** Deregisters gasket locations for a specific slot. Handles partial gasket presence. */
    private void deregisterSlotGaskets(int slot) {
        if (gasketRegistryAccess == null) return;
        ItemStack stack = canisters.get(slot);
        if (stack.isEmpty()) return;

        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        GasketRegistry registry = gasketRegistryAccess.get();
        if (meta.topGasketId() != null) {
            registry.updateLocation(meta.topGasketId(), null);
        }
        if (meta.bottomGasketId() != null) {
            registry.updateLocation(meta.bottomGasketId(), null);
        }
    }

    /** Deregisters all gasket locations (block broken or removed). */
    private void deregisterAllGaskets() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!canisters.get(i).isEmpty()) {
                deregisterSlotGaskets(i);
            }
        }
    }

    /** Disposes pushers and deregisters all gasket locations before removal. */
    @Override
    public void setRemoved() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            disposeSlotPusher(i);
        }
        deregisterAllGaskets();
        super.setRemoved();
    }

    // --- IGasketHolder tuner dispatch ---

    /** {@inheritDoc} Delegates to {@link CanisterBlock#hitSlot}. */
    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = CanisterBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    /** {@inheritDoc} Checks tuner owner against canister owner. */
    @Override
    public boolean allowsTuning(@Nullable UUID tunerOwner) {
        if (ownerUuid == null) return true;
        if (tunerOwner == null) return true;
        return tunerOwner.equals(ownerUuid);
    }

    // --- IGasketHolder (non-slot: canister always needs slot context) ---

    /** Canister has no machine-level gasket - always requires a slot. */
    @Override
    public @Nullable UUID getGasketId(GasketRole role) { return null; }

    /** Canister has no machine-level gasket - always requires a slot. */
    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) { return null; }

    /** Canister has no machine-level partner - always requires a slot. */
    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) { return null; }

    /** Canister has no machine-level partner - always requires a slot. */
    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {}

    /** {@inheritDoc} Rebuilds the slot's pusher when its transmitter partner changes. */
    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        IGasketHolder.super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER && !isSlotOutOfRange(slot)) {
            rebuildSlotPusher(slot);
        }
    }

    // --- Serialization ---

    /** Writes canister grid, owner UUID, and stream state to persistent storage. */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("Canisters", ItemStack.OPTIONAL_CODEC.listOf(), canisters.stream().toList());
        if (ownerUuid != null) {
            output.store("OwnerUuid", UUIDUtil.STRING_CODEC, ownerUuid);
        }
        saveStreamState(output);
    }

    /** Serializes per-slot stream state for client sync. */
    private void saveStreamState(ValueOutput output) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotStreamType[i] != null) {
                CompoundTag slot = new CompoundTag();
                slot.putInt("type", slotStreamType[i].ordinal());
                slot.putInt("rate", slotStreamRate[i]);
                slot.putLong("tick", slotStreamTick[i]);
                tag.put(String.valueOf(i), slot);
            }
        }
        if (!tag.isEmpty()) {
            output.store("Streams", CompoundTag.CODEC, tag);
        }
    }

    /** Restores per-slot stream state from the update tag. */
    private void loadStreamState(ValueInput input) {
        GooType[] types = GooType.values();
        input.read("Streams", CompoundTag.CODEC).ifPresentOrElse(tag -> {
            for (int i = 0; i < MAX_SLOTS; i++) {
                String key = String.valueOf(i);
                if (tag.contains(key)) {
                    CompoundTag slot = tag.getCompoundOrEmpty(key);
                    int ordinal = slot.getIntOr("type", -1);
                    slotStreamType[i] = ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
                    slotStreamRate[i] = slot.getIntOr("rate", 0);
                    slotStreamTick[i] = slot.getLongOr("tick", 0);
                } else {
                    clearSlotStream(i);
                }
            }
        }, () -> {
            for (int i = 0; i < MAX_SLOTS; i++) clearSlotStream(i);
        });
    }

    /** Clears stream state for a single slot. */
    private void clearSlotStream(int i) {
        slotStreamType[i] = null;
        slotStreamRate[i] = 0;
        slotStreamTick[i] = 0;
    }

    /** Restores canister grid and owner UUID from persistent storage. */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        input.read("Canisters", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < MAX_SLOTS; i++) {
                canisters.set(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
            }
        });
        input.read("OwnerUuid", UUIDUtil.STRING_CODEC)
            .ifPresent(u -> ownerUuid = u);
        loadStreamState(input);
        rebuildAllSlotHandlers();
        invalidateShape();
    }

    /** Captures gasket registry, registers gaskets, and rebuilds pushers when the level is assigned. */
    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
            registerAllGaskets();
            rebuildAllSlotPushers();
        }
    }

    /** Rebuilds pusher caches (cascade) and forces transmitter chunks (bidirectional) on chunk load. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) return;
        rebuildAllSlotPushers();
        forceAllTransmitterChunks(serverLevel);
    }

    /** Forces the chunk of each slot's transmitter partner (receiver-side bidirectional forcing). */
    private void forceAllTransmitterChunks(ServerLevel serverLevel) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            ItemStack stack = canisters.get(i);
            if (stack.isEmpty()) continue;
            CanisterMetadata meta = CanisterItem.getMetadata(stack);
            GasketPusher.forceTransmitterChunk(meta.topGasketId(), gasketRegistryAccess, serverLevel, worldPosition);
        }
    }

    /** Returns full NBT for initial chunk sync to clients. */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** Returns the sync packet sent when block entity data changes. */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Item component bridge ---

    /** Returns the index of the sole occupied slot, or -1 if zero or multiple are occupied. */
    private int findSingleOccupiedSlot() {
        int found = -1;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!canisters.get(i).isEmpty()) {
                if (found != -1) return -1;
                found = i;
            }
        }
        return found;
    }

    /**
     * Exposes canister contents for loot table copy_components.
     * Single-canister: exports GOO_CONTENTS + CANISTER_METADATA.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        int slot = findSingleOccupiedSlot();
        if (slot != -1) {
            exportSlotComponents(builder, canisters.get(slot));
        }
    }

    /** Exports a single slot's goo contents and metadata to the item component builder. */
    private void exportSlotComponents(DataComponentMap.Builder builder, ItemStack slot) {
        GooContents goo = CanisterItem.getGooContents(slot);
        if (!goo.isEmpty()) {
            builder.set(GooDataComponents.GOO_CONTENTS.get(), goo);
        }
        CanisterMetadata meta = CanisterItem.getMetadata(slot);
        if (meta.hasData()) {
            builder.set(GooDataComponents.CANISTER_METADATA.get(), meta);
        }
    }

    /** Reads canister contents from the item's data components when placed. */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        GooContents goo = getter.get(GooDataComponents.GOO_CONTENTS.get());
        if (goo != null) {
            pendingGooContents = goo;
        }
        CanisterMetadata meta = getter.get(GooDataComponents.CANISTER_METADATA.get());
        if (meta != null) {
            pendingMetadata = meta;
        }
    }
}
