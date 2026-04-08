package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Hub: holds up to 8 canisters in radial slots (N, NE, E, SE, S, SW, W, NW).
 * Central input on top auto-routes goo to canisters with remaining capacity.
 */
public class HubBlockEntity extends BlockEntity implements ISlottedGooContainer, IGasketHolder, ICanisterAttachable {

    public static final int MAX_CANISTERS = 8;
    /** Block update flags: notify neighbors + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /** Sentinel value indicating an invalid NBT ordinal. */
    private static final int INVALID_ORDINAL = -1;

    // --- NBT tag keys ---
    /** NBT key for canister inventory list. */
    private static final String TAG_CANISTERS = "Canisters";
    /** NBT key for intake gasket UUID. */
    private static final String TAG_INTAKE_GASKET_ID = "IntakeGasketId";
    /** NBT key for intake gasket partner. */
    private static final String TAG_INTAKE_PARTNER = "IntakePartner";
    /** NBT key for stream state compound. */
    private static final String TAG_STREAMS = "Streams";
    /** NBT key for stream goo type ordinal. */
    private static final String TAG_TYPE = "type";
    /** NBT key for stream transfer rate. */
    private static final String TAG_RATE = "rate";
    /** NBT key for stream start tick. */
    private static final String TAG_TICK = "tick";

    private final List<ItemStack> canisters = NonNullList.withSize(MAX_CANISTERS, ItemStack.EMPTY);

    /** Decoupled registry access, captured in setLevel to avoid direct GasketRegistry.get calls. */
    private @Nullable IGasketRegistryAccess gasketRegistryAccess;

    /** Gasket UUID for the intake receiver at the top of the spindle. */
    private @Nullable UUID intakeGasketId;

    /** Linked partner for the intake gasket. */
    private @Nullable GasketPartner intakePartner;

    /** Live fluid handlers per slot - non-null when the slot is occupied. */
    private final @Nullable GooFluidHandler[] slotHandlers = new GooFluidHandler[MAX_CANISTERS];

    /** Per-slot gasket pushers - non-null when the slot has an active bottom gasket partner. */
    private final @Nullable IGasketPusher[] slotPushers = new IGasketPusher[MAX_CANISTERS];

    /** Cached composite shape of frame + occupied slots. Null when dirty. */
    private @Nullable VoxelShape cachedShape;

    // --- Per-slot stream state (synced to client for BER rendering) ---
    private final @Nullable GooType[] slotStreamType = new GooType[MAX_CANISTERS];
    private final int[] slotStreamRate = new int[MAX_CANISTERS];
    private final long[] slotStreamTick = new long[MAX_CANISTERS];

    /** Creates a hub block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public HubBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.HUB.get(), pos, state);
    }

    /** Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HubBlockEntity be) {
        be.serverTick();
    }

    /** Ticks all active slot pushers. */
    private void serverTick() {
        for (IGasketPusher pusher : slotPushers) {
            if (pusher != null) { pusher.tick(); }
        }
    }

    // --- ICanisterAttachable (external canister on hub top) ---

    /** Hub accepts exactly 1 canister on its center top face.
     *
     * @return the integer value
     */
    @Override
    public int maxTopAttachments() { return 1; }

    /** Checks if a CanisterBlock above has a canister in center slot (4).
     *
     * @return the integer value
     */
    @Override
    public int currentTopAttachments() {
        if (level == null) { return 0; }
        BlockPos above = worldPosition.above();
        if (level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe) {
            return canisterBe.getCanister(CanisterBlock.CENTER_SLOT).isEmpty() ? 0 : 1;
        }
        return 0;
    }

    /** Hub only allows the center slot (4) for top attachment.
     *
     * @return the set
     */
    @Override
    public java.util.Set<Integer> allowedSlots() {
        return java.util.Set.of(CanisterBlock.CENTER_SLOT);
    }

    /**
     * Inserts a canister into the first empty slot.
     * Convenience method for non-player automation.
     *
     * @param canisterStack the canister item stack
     * @return true if the condition is met
     */
    public boolean insertCanister(ItemStack canisterStack) {
        for (int i = 0; i < MAX_CANISTERS; i++) {
            if (canInsertAt(i, canisterStack)) {
                return insertCanister(i, canisterStack);
            }
        }
        return false;
    }

    /**
     * Inserts a canister into a specific slot.
     *
     * @param slot the slot index (0-7)
     * @param canisterStack the canister item stack to insert
     * @return true if inserted, false if slot is occupied or stack is invalid
     */
    public boolean insertCanister(int slot, ItemStack canisterStack) {
        if (!canInsertAt(slot, canisterStack)) { return false; }
        canisters.set(slot, canisterStack.copyWithCount(1));
        slotHandlers[slot] = createSlotHandler(slot);
        rebuildSlotPusher(slot);
        onSlotStructureChanged();
        return true;
    }

    /** Returns true if the given slot can accept a canister insertion.
     *
     * @param slot          the slot index
     * @param canisterStack the canister item stack
     * @return true if insert at
     */
    private boolean canInsertAt(int slot, ItemStack canisterStack) {
        return slot >= 0 && slot < MAX_CANISTERS
                && canisterStack.getItem() instanceof CanisterItem
                && canisters.get(slot).isEmpty();
    }

    /**
     * Removes the canister from a specific slot.
     *
     * @param slot the slot index (0-7)
     * @return the removed stack, or {@link ItemStack#EMPTY} if slot was empty
     */
    public ItemStack removeCanister(int slot) {
        if (slot < 0 || slot >= MAX_CANISTERS) { return ItemStack.EMPTY; }
        if (canisters.get(slot).isEmpty()) { return ItemStack.EMPTY; }
        disposeSlotPusher(slot);
        syncSlotToItemStack(slot);
        slotHandlers[slot] = null;
        ItemStack removed = canisters.get(slot).copy();
        canisters.set(slot, ItemStack.EMPTY);
        onSlotStructureChanged();
        return removed;
    }

    /** Shared lifecycle after a slot's canister changes. Invalidates shape, capabilities, and syncs. */
    private void onSlotStructureChanged() {
        invalidateShape();
        BlockEntitySync.invalidateCapabilities(this);
        markDirtyAndSync();
    }

    /**
     * Returns the canister in the given slot without removing it.
     *
     * @param slot the slot index (0-7)
     * @return the stack in that slot (may be empty)
     */
    @Override
    public ItemStack getCanister(int slot) {
        if (slot < 0 || slot >= MAX_CANISTERS) { return ItemStack.EMPTY; }
        return canisters.get(slot);
    }

    /**
     * Route incoming goo to canisters with remaining capacity.
     * Returns the amount actually routed.
     *
     * @param type   the goo type
     * @param amount volume in microblobs
     * @return the long value
     */
    public long routeGoo(GooType type, long amount) {
        long remaining = amount;
        for (int i = 0; i < MAX_CANISTERS && remaining > 0; i++) {
            GooFluidHandler handler = slotHandlers[i];
            if (handler == null) { continue; }
            int toInsert = (int) Math.min(remaining, Integer.MAX_VALUE);
            int added = handler.insertGoo(type, toInsert, false);
            remaining -= added;
        }
        if (remaining < amount) { markDirtyAndSync(); }
        return amount - remaining;
    }

    /** {@inheritDoc} */
    @Override
    public void onSlotChanged() {
        markDirtyAndSync();
    }

    // --- ISlottedGooContainer overrides (delegate to live handlers) ---

    /** {@inheritDoc} Reads from the live handler if available.
     *
     * @param slot the slot index
     * @return the slot goo contents
     */
    @Override
    public GooContents getSlotGooContents(int slot) {
        GooFluidHandler h = (slot >= 0 && slot < MAX_CANISTERS) ? slotHandlers[slot] : null;
        return h != null ? h.toGooContents() : GooContents.EMPTY;
    }

    /** {@inheritDoc} Delegates to the slot's live handler.
     *
     * @param slot         the slot index
     * @param incomingType the goo type to insert
     * @param volume       volume in microblobs
     * @return the long value
     */
    @Override
    public long insertGoo(int slot, GooType incomingType, long volume) {
        GooFluidHandler h = (slot >= 0 && slot < MAX_CANISTERS) ? slotHandlers[slot] : null;
        if (h == null) { return 0L; }
        return h.insertGoo(incomingType, (int) Math.min(volume, Integer.MAX_VALUE), false);
    }

    /** {@inheritDoc} Delegates to the slot's live handler.
     *
     * @param slot      the slot index
     * @param type      the goo type
     * @param requested volume in microblobs to extract
     * @return the long value
     */
    @Override
    public long extractGoo(int slot, GooType type, long requested) {
        GooFluidHandler h = (slot >= 0 && slot < MAX_CANISTERS) ? slotHandlers[slot] : null;
        if (h == null) { return 0L; }
        return h.extractGoo(type, (int) Math.min(requested, Integer.MAX_VALUE), false);
    }

    /** {@inheritDoc} Checks the slot's live handler for remaining capacity.
     *
     * @param slot the slot index
     * @return true if accept
     */
    @Override
    public boolean canAccept(int slot) {
        if (slot < 0 || slot >= MAX_CANISTERS || canisters.get(slot).isEmpty()) { return false; }
        GooFluidHandler h = slotHandlers[slot];
        if (h == null) { return false; }
        int compression = GooEnchantments.getCompressionLevel(canisters.get(slot));
        return h.totalVolume() < ContainerCapacity.canisterCapacity(compression);
    }

    /** Returns the live fluid handler for a slot, or null if the slot is empty.
     *
     * @param slot the slot index
     * @return the slot fluid handler
     */
    public @Nullable GooFluidHandler getSlotFluidHandler(int slot) {
        if (slot < 0 || slot >= MAX_CANISTERS) { return null; }
        return slotHandlers[slot];
    }

    /** Returns the stream goo type for a slot, or null if no active stream.
     *
     * @param slot        the slot index
     * @param currentTick the current game tick
     * @return the slot stream type
     */
    public @Nullable GooType getSlotStreamType(int slot, long currentTick) {
        if (slot < 0 || slot >= MAX_CANISTERS) { return null; }
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamType[slot] : null;
    }

    /** Returns the stream rate for a slot in mB/tick, or 0 if no active stream.
     *
     * @param slot        the slot index
     * @param currentTick the current game tick
     * @return the slot stream rate
     */
    public int getSlotStreamRate(int slot, long currentTick) {
        if (slot < 0 || slot >= MAX_CANISTERS) { return 0; }
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamRate[slot] : 0;
    }

    // --- Per-slot fluid handler lifecycle ---

    /** Creates a live handler for the given slot, loaded from the canister ItemStack.
     *
     * @param slot the slot index
     * @return the new slot handler
     */
    private GooFluidHandler createSlotHandler(int slot) {
        ItemStack stack = canisters.get(slot);
        int compression = GooEnchantments.getCompressionLevel(stack);
        int capacity = (int) ContainerCapacity.canisterCapacity(compression);
        GooFluidHandler handler = new GooFluidHandler(capacity,
            () -> syncSlotToItemStack(slot),
            () -> level != null ? level.getGameTime() : 0L);
        handler.loadFrom(CanisterItem.getGooContents(stack));
        return handler;
    }

    /** Writes the slot handler's current state back to the canister ItemStack.
     *
     * @param slot the slot index
     */
    private void syncSlotToItemStack(int slot) {
        ItemStack stack = canisters.get(slot);
        if (stack.isEmpty() || slotHandlers[slot] == null) { return; }
        CanisterItem.setGooContents(stack, slotHandlers[slot].toGooContents());
        snapshotSlotStream(slot);
        markDirtyAndSync();
    }

    /** Copies the handler's transient stream state to block entity fields for sync.
     *
     * @param slot the slot index
     */
    private void snapshotSlotStream(int slot) {
        GooFluidHandler h = slotHandlers[slot];
        if (h == null) { return; }
        long tick = level != null ? level.getGameTime() : 0L;
        slotStreamType[slot] = h.getStreamType(tick);
        slotStreamRate[slot] = h.getStreamRate(tick);
        slotStreamTick[slot] = tick;
    }

    /** Rebuilds slot handlers for all occupied slots. */
    private void rebuildAllSlotHandlers() {
        for (int i = 0; i < MAX_CANISTERS; i++) {
            slotHandlers[i] = canisters.get(i).isEmpty() ? null : createSlotHandler(i);
        }
    }

    // --- Per-slot gasket pusher lifecycle ---

    /** Rebuilds the pusher for a slot based on its bottom gasket state.
     *
     * @param slot the slot index
     */
    private void rebuildSlotPusher(int slot) {
        disposeSlotPusher(slot);
        GooFluidHandler handler = slotHandlers[slot];
        if (handler == null) { return; }
        ItemStack stack = canisters.get(slot);
        if (stack.isEmpty()) { return; }
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        if (meta.bottomGasketId() == null || meta.bottomPartner() == null) { return; }

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

    /** Disposes and nulls the pusher for the given slot.
     *
     * @param slot the slot index
     */
    private void disposeSlotPusher(int slot) {
        IGasketPusher existing = slotPushers[slot];
        if (existing != null) {
            existing.dispose();
            slotPushers[slot] = null;
        }
    }

    /** Rebuilds pushers for all occupied slots. */
    private void rebuildAllSlotPushers() {
        for (int i = 0; i < MAX_CANISTERS; i++) {
            rebuildSlotPusher(i);
        }
    }

    // --- IGasketHolder tuner dispatch ---

    /** {@inheritDoc} Delegates to {@link HubBlock#hitSlot}.
     *
     * @param hit the ray trace hit result
     * @return the integer value
     */
    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = HubBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    /** {@inheritDoc} Hub has a central intake gasket.
     *
     * @return true if intake
     */
    @Override
    public boolean hasIntake() { return true; }

    // --- IGasketHolder (intake = RECEIVER only) ---

    /** Returns the intake gasket UUID if the role is RECEIVER, null otherwise.
     *
     * @param role the gasket role
     * @return the gasket id
     */
    @Override
    public @Nullable UUID getGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER ? intakeGasketId : null;
    }

    /** Creates a gasket UUID for the intake if one does not exist.
     *
     * @param role the gasket role
     * @return the UUID, or null
     */
    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) {
        if (role != GasketRole.RECEIVER) { return null; }
        if (intakeGasketId == null) {
            intakeGasketId = UUID.randomUUID();
            markDirtyAndSync();
        }
        return intakeGasketId;
    }

    /** Returns the intake partner if the role is RECEIVER, null otherwise.
     *
     * @param role the gasket role
     * @return the partner
     */
    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.RECEIVER ? intakePartner : null;
    }

    /** Sets the intake partner for the RECEIVER role.
     *
     * @param role    the gasket role
     * @param partner the gasket partner, or null to clear
     */
    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        if (role != GasketRole.RECEIVER) { return; }
        intakePartner = partner;
        markDirtyAndSync();
    }

    /** Clears the intake gasket UUID, partner, and blockstate when popped by copper fitting.
     *
     * @param role the gasket role
     */
    @Override
    public void clearGasket(GasketRole role) {
        if (role != GasketRole.RECEIVER) { return; }
        intakeGasketId = null;
        intakePartner = null;
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getValue(HubBlock.HAS_GASKET)) {
                level.setBlock(worldPosition, state.setValue(HubBlock.HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
            }
        }
        markDirtyAndSync();
    }

    /** {@inheritDoc} Rebuilds the slot's pusher when its transmitter partner changes.
     *
     * @param role    the gasket role
     * @param slot    the slot index
     * @param partner the gasket partner, or null to clear
     */
    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        IGasketHolder.super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER && slot >= 0 && slot < MAX_CANISTERS) {
            rebuildSlotPusher(slot);
        }
    }

    // --- Dynamic VoxelShape ---

    /** Returns the cached composite shape of frame + occupied slots.
     *
     * @return the cached shape
     */
    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    /** Computes the union of the frame and all occupied slot shapes.
     *
     * @return the computed shape
     */
    private VoxelShape computeShape() {
        VoxelShape result = HubBlock.frameShape();
        for (int i = 0; i < MAX_CANISTERS; i++) {
            if (!canisters.get(i).isEmpty()) {
                result = Shapes.or(result, HubBlock.slotShape(i));
            }
        }
        return result;
    }

    /** Invalidates the cached shape (call after slot changes). */
    private void invalidateShape() {
        cachedShape = null;
    }

    /** Returns the block-level fluid handler for Transfer API capability registration.
     *
     * @return the fluid handler
     */
    public HubFluidHandler getFluidHandler() {
        return new HubFluidHandler(this);
    }

    // --- Sync ---

    /** Marks dirty and sends sync packet to tracking clients. */
    private void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    /** Captures gasket registry access and rebuilds pushers when the level is assigned.
     *
     * @param level the current level
     */
    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
            rebuildAllSlotPushers();
        }
    }

    /** Rebuilds pusher caches (cascade) and forces transmitter chunks (bidirectional) on chunk load. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        rebuildAllSlotPushers();
        forceAllTransmitterChunks(serverLevel);
    }

    /** Forces the chunk of each receiver gasket's transmitter partner.
     *
     * @param serverLevel the server level
     */
    private void forceAllTransmitterChunks(ServerLevel serverLevel) {
        IGasketRegistryAccess access = () -> GasketRegistry.get(serverLevel);
        GasketPusher.forceTransmitterChunk(intakeGasketId, access, serverLevel, worldPosition);
        for (int i = 0; i < MAX_CANISTERS; i++) {
            ItemStack stack = canisters.get(i);
            if (stack.isEmpty()) { continue; }
            CanisterMetadata meta = CanisterItem.getMetadata(stack);
            GasketPusher.forceTransmitterChunk(meta.topGasketId(), access, serverLevel, worldPosition);
        }
    }

    // --- Serialization ---

    /** Persists canister inventory and gasket state.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_CANISTERS, ItemStack.OPTIONAL_CODEC.listOf(), canisters.stream().toList());
        if (intakeGasketId != null) {
            output.store(TAG_INTAKE_GASKET_ID, UUIDUtil.STRING_CODEC, intakeGasketId);
        }
        if (intakePartner != null) {
            output.store(TAG_INTAKE_PARTNER, GasketPartner.CODEC, intakePartner);
        }
        saveStreamState(output);
    }

    /** Serializes per-slot stream state for client sync.
     *
     * @param output the value output to write to
     */
    private void saveStreamState(ValueOutput output) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < MAX_CANISTERS; i++) {
            if (slotStreamType[i] != null) {
                CompoundTag slot = new CompoundTag();
                slot.putInt(TAG_TYPE, slotStreamType[i].ordinal());
                slot.putInt(TAG_RATE, slotStreamRate[i]);
                slot.putLong(TAG_TICK, slotStreamTick[i]);
                tag.put(String.valueOf(i), slot);
            }
        }
        if (!tag.isEmpty()) {
            output.store(TAG_STREAMS, CompoundTag.CODEC, tag);
        }
    }

    /** Restores canister inventory and gasket state.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        input.read(TAG_CANISTERS, ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < MAX_CANISTERS && i < list.size(); i++) {
                canisters.set(i, list.get(i));
            }
        });
        intakeGasketId = input.read(TAG_INTAKE_GASKET_ID, UUIDUtil.STRING_CODEC).orElse(null);
        intakePartner = input.read(TAG_INTAKE_PARTNER, GasketPartner.CODEC).orElse(null);
        loadStreamState(input);
        rebuildAllSlotHandlers();
        invalidateShape();
    }

    /** Restores per-slot stream state from the update tag.
     *
     * @param input the value input to read from
     */
    private void loadStreamState(ValueInput input) {
        GooType[] types = GooType.values();
        input.read(TAG_STREAMS, CompoundTag.CODEC).ifPresentOrElse(tag -> {
            for (int i = 0; i < MAX_CANISTERS; i++) {
                String key = String.valueOf(i);
                if (tag.contains(key)) {
                    CompoundTag slot = tag.getCompoundOrEmpty(key);
                    int ordinal = slot.getIntOr(TAG_TYPE, INVALID_ORDINAL);
                    slotStreamType[i] = ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
                    slotStreamRate[i] = slot.getIntOr(TAG_RATE, 0);
                    slotStreamTick[i] = slot.getLongOr(TAG_TICK, 0);
                } else {
                    slotStreamType[i] = null;
                    slotStreamRate[i] = 0;
                    slotStreamTick[i] = 0;
                }
            }
        }, () -> {
            for (int i = 0; i < MAX_CANISTERS; i++) {
                slotStreamType[i] = null;
                slotStreamRate[i] = 0;
                slotStreamTick[i] = 0;
            }
        });
    }

    /** Returns full NBT for initial chunk sync to clients.
     *
     * @param registries the registry provider
     * @return the update tag
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** Returns the sync packet sent when block entity data changes.
     *
     * @return the update packet
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Item component bridge (loot table copy_components) ---

    /** Exposes canisters as a data component so the loot table can copy them to the dropped item.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        List<ItemStack> nonEmpty = canisters.stream().filter(s -> !s.isEmpty()).toList();
        if (!nonEmpty.isEmpty()) {
            builder.set(GooDataComponents.HUB_CANISTERS.get(), nonEmpty);
        }
    }

    /** Restores canisters from the item's data component when placed.
     *
     * @param getter the data component getter
     */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        List<ItemStack> fromItem = getter.get(GooDataComponents.HUB_CANISTERS.get());
        if (fromItem != null) {
            for (int i = 0; i < MAX_CANISTERS; i++) {
                canisters.set(i, i < fromItem.size() ? fromItem.get(i) : ItemStack.EMPTY);
            }
        }
    }
}
