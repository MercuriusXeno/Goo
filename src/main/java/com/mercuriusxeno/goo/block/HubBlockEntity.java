package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;
import java.util.UUID;

/**
 * Hub: holds up to 8 canisters in radial slots (N, NE, E, SE, S, SW, W, NW).
 * Central input on top auto-routes goo to canisters with remaining capacity.
 */
public class HubBlockEntity extends BlockEntity implements ISlottedGooContainer, IGasketHolder, ICanisterAttachable {

    public static final int MAX_CANISTERS = 8;
    private final NonNullList<ItemStack> canisters = NonNullList.withSize(MAX_CANISTERS, ItemStack.EMPTY);

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

    public HubBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.HUB.get(), pos, state);
    }

    /** Static tick entrypoint for the block entity ticker. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HubBlockEntity be) {
        be.serverTick();
    }

    /** Ticks all active slot pushers. */
    private void serverTick() {
        for (IGasketPusher pusher : slotPushers) {
            if (pusher != null) pusher.tick();
        }
    }

    // --- ICanisterAttachable (external canister on hub top) ---

    /** Hub accepts exactly 1 canister on its center top face. */
    @Override
    public int maxTopAttachments() { return 1; }

    /** Checks if a CanisterBlock above has a canister in center slot (4). */
    @Override
    public int currentTopAttachments() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        if (level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe) {
            return canisterBe.getCanister(CanisterBlock.CENTER_SLOT).isEmpty() ? 0 : 1;
        }
        return 0;
    }

    /** Hub only allows the center slot (4) for top attachment. */
    @Override
    public java.util.Set<Integer> allowedSlots() {
        return java.util.Set.of(CanisterBlock.CENTER_SLOT);
    }

    /**
     * Inserts a canister into the first empty slot.
     * Convenience method for non-player automation.
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
        if (!canInsertAt(slot, canisterStack)) return false;
        canisters.set(slot, canisterStack.copyWithCount(1));
        slotHandlers[slot] = createSlotHandler(slot);
        rebuildSlotPusher(slot);
        onSlotStructureChanged();
        return true;
    }

    /** Returns true if the given slot can accept a canister insertion. */
    private boolean canInsertAt(int slot, ItemStack canisterStack) {
        if (slot < 0 || slot >= MAX_CANISTERS) return false;
        if (!(canisterStack.getItem() instanceof CanisterItem)) return false;
        return canisters.get(slot).isEmpty();
    }

    /**
     * Removes the canister from a specific slot.
     *
     * @param slot the slot index (0-7)
     * @return the removed stack, or {@link ItemStack#EMPTY} if slot was empty
     */
    public ItemStack removeCanister(int slot) {
        if (slot < 0 || slot >= MAX_CANISTERS) return ItemStack.EMPTY;
        if (canisters.get(slot).isEmpty()) return ItemStack.EMPTY;
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
        if (slot < 0 || slot >= MAX_CANISTERS) return ItemStack.EMPTY;
        return canisters.get(slot);
    }

    /**
     * Route incoming goo to canisters with remaining capacity.
     * Returns the amount actually routed.
     */
    public long routeGoo(GooType type, long amount) {
        long remaining = amount;
        for (int i = 0; i < MAX_CANISTERS && remaining > 0; i++) {
            GooFluidHandler handler = slotHandlers[i];
            if (handler == null) continue;
            int toInsert = (int) Math.min(remaining, Integer.MAX_VALUE);
            int added = handler.insertGoo(type, toInsert, false);
            remaining -= added;
        }
        if (remaining < amount) markDirtyAndSync();
        return amount - remaining;
    }

    /** {@inheritDoc} */
    @Override
    public void onSlotChanged() {
        markDirtyAndSync();
    }

    // --- ISlottedGooContainer overrides (delegate to live handlers) ---

    /** {@inheritDoc} Reads from the live handler if available. */
    @Override
    public GooContents getSlotGooContents(int slot) {
        GooFluidHandler h = (slot >= 0 && slot < MAX_CANISTERS) ? slotHandlers[slot] : null;
        return h != null ? h.toGooContents() : GooContents.EMPTY;
    }

    /** {@inheritDoc} Delegates to the slot's live handler. */
    @Override
    public long insertGoo(int slot, GooType incomingType, long volume) {
        GooFluidHandler h = (slot >= 0 && slot < MAX_CANISTERS) ? slotHandlers[slot] : null;
        if (h == null) return 0L;
        return h.insertGoo(incomingType, (int) Math.min(volume, Integer.MAX_VALUE), false);
    }

    /** {@inheritDoc} Delegates to the slot's live handler. */
    @Override
    public long extractGoo(int slot, GooType type, long requested) {
        GooFluidHandler h = (slot >= 0 && slot < MAX_CANISTERS) ? slotHandlers[slot] : null;
        if (h == null) return 0L;
        return h.extractGoo(type, (int) Math.min(requested, Integer.MAX_VALUE), false);
    }

    /** {@inheritDoc} Checks the slot's live handler for remaining capacity. */
    @Override
    public boolean canAccept(int slot) {
        if (slot < 0 || slot >= MAX_CANISTERS || canisters.get(slot).isEmpty()) return false;
        GooFluidHandler h = slotHandlers[slot];
        if (h == null) return false;
        int compression = GooEnchantments.getCompressionLevel(canisters.get(slot));
        return h.totalVolume() < ContainerCapacity.canisterCapacity(compression);
    }

    /** Returns the live fluid handler for a slot, or null if the slot is empty. */
    public @Nullable GooFluidHandler getSlotFluidHandler(int slot) {
        if (slot < 0 || slot >= MAX_CANISTERS) return null;
        return slotHandlers[slot];
    }

    /** Returns the stream goo type for a slot, or null if no active stream. */
    public @Nullable GooType getSlotStreamType(int slot, long currentTick) {
        if (slot < 0 || slot >= MAX_CANISTERS) return null;
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamType[slot] : null;
    }

    /** Returns the stream rate for a slot in mB/tick, or 0 if no active stream. */
    public int getSlotStreamRate(int slot, long currentTick) {
        if (slot < 0 || slot >= MAX_CANISTERS) return 0;
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamRate[slot] : 0;
    }

    // --- Per-slot fluid handler lifecycle ---

    /** Creates a live handler for the given slot, loaded from the canister ItemStack. */
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

    /** Rebuilds slot handlers for all occupied slots. */
    private void rebuildAllSlotHandlers() {
        for (int i = 0; i < MAX_CANISTERS; i++) {
            slotHandlers[i] = canisters.get(i).isEmpty() ? null : createSlotHandler(i);
        }
    }

    // --- Per-slot gasket pusher lifecycle ---

    /** Rebuilds the pusher for a slot based on its bottom gasket state. */
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

    /** Rebuilds pushers for all occupied slots. */
    private void rebuildAllSlotPushers() {
        for (int i = 0; i < MAX_CANISTERS; i++) {
            rebuildSlotPusher(i);
        }
    }

    // --- IGasketHolder tuner dispatch ---

    /** {@inheritDoc} Delegates to {@link HubBlock#hitSlot}. */
    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = HubBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    /** {@inheritDoc} Hub has a central intake gasket. */
    @Override
    public boolean hasIntake() { return true; }

    // --- IGasketHolder (intake = RECEIVER only) ---

    @Override
    public @Nullable UUID getGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER ? intakeGasketId : null;
    }

    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) {
        if (role != GasketRole.RECEIVER) return null;
        if (intakeGasketId == null) {
            intakeGasketId = UUID.randomUUID();
            markDirtyAndSync();
        }
        return intakeGasketId;
    }

    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.RECEIVER ? intakePartner : null;
    }

    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        if (role != GasketRole.RECEIVER) return;
        intakePartner = partner;
        markDirtyAndSync();
    }

    /** Clears the intake gasket UUID, partner, and blockstate when popped by copper fitting. */
    @Override
    public void clearGasket(GasketRole role) {
        if (role != GasketRole.RECEIVER) return;
        intakeGasketId = null;
        intakePartner = null;
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getValue(HubBlock.HAS_GASKET)) {
                level.setBlock(worldPosition, state.setValue(HubBlock.HAS_GASKET, false), 3);
            }
        }
        markDirtyAndSync();
    }

    /** {@inheritDoc} Rebuilds the slot's pusher when its transmitter partner changes. */
    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        IGasketHolder.super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER && slot >= 0 && slot < MAX_CANISTERS) {
            rebuildSlotPusher(slot);
        }
    }

    // --- Dynamic VoxelShape ---

    /** Returns the cached composite shape of frame + occupied slots. */
    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    /** Computes the union of the frame and all occupied slot shapes. */
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

    /** Returns the block-level fluid handler for Transfer API capability registration. */
    public HubFluidHandler getFluidHandler() {
        return new HubFluidHandler(this);
    }

    // --- Sync ---

    /** Marks dirty and sends sync packet to tracking clients. */
    private void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    /** Captures gasket registry access and rebuilds pushers when the level is assigned. */
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
        if (!(level instanceof ServerLevel serverLevel)) return;
        rebuildAllSlotPushers();
        forceAllTransmitterChunks(serverLevel);
    }

    /** Forces the chunk of each receiver gasket's transmitter partner. */
    private void forceAllTransmitterChunks(ServerLevel serverLevel) {
        IGasketRegistryAccess access = () -> GasketRegistry.get(serverLevel);
        GasketPusher.forceTransmitterChunk(intakeGasketId, access, serverLevel, worldPosition);
        for (int i = 0; i < MAX_CANISTERS; i++) {
            ItemStack stack = canisters.get(i);
            if (stack.isEmpty()) continue;
            CanisterMetadata meta = CanisterItem.getMetadata(stack);
            GasketPusher.forceTransmitterChunk(meta.topGasketId(), access, serverLevel, worldPosition);
        }
    }

    // --- Serialization ---

    /** Persists canister inventory and gasket state. */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("Canisters", ItemStack.OPTIONAL_CODEC.listOf(), canisters.stream().toList());
        if (intakeGasketId != null) {
            output.store("IntakeGasketId", UUIDUtil.STRING_CODEC, intakeGasketId);
        }
        if (intakePartner != null) {
            output.store("IntakePartner", GasketPartner.CODEC, intakePartner);
        }
        saveStreamState(output);
    }

    /** Serializes per-slot stream state for client sync. */
    private void saveStreamState(ValueOutput output) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < MAX_CANISTERS; i++) {
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

    /** Restores canister inventory and gasket state. */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        input.read("Canisters", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < MAX_CANISTERS && i < list.size(); i++) {
                canisters.set(i, list.get(i));
            }
        });
        intakeGasketId = input.read("IntakeGasketId", UUIDUtil.STRING_CODEC).orElse(null);
        intakePartner = input.read("IntakePartner", GasketPartner.CODEC).orElse(null);
        loadStreamState(input);
        rebuildAllSlotHandlers();
        invalidateShape();
    }

    /** Restores per-slot stream state from the update tag. */
    private void loadStreamState(ValueInput input) {
        GooType[] types = GooType.values();
        input.read("Streams", CompoundTag.CODEC).ifPresentOrElse(tag -> {
            for (int i = 0; i < MAX_CANISTERS; i++) {
                String key = String.valueOf(i);
                if (tag.contains(key)) {
                    CompoundTag slot = tag.getCompoundOrEmpty(key);
                    int ordinal = slot.getIntOr("type", -1);
                    slotStreamType[i] = ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
                    slotStreamRate[i] = slot.getIntOr("rate", 0);
                    slotStreamTick[i] = slot.getLongOr("tick", 0);
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

    // --- Item component bridge (loot table copy_components) ---

    /** Exposes canisters as a data component so the loot table can copy them to the dropped item. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        List<ItemStack> nonEmpty = canisters.stream().filter(s -> !s.isEmpty()).toList();
        if (!nonEmpty.isEmpty()) {
            builder.set(GooDataComponents.HUB_CANISTERS.get(), nonEmpty);
        }
    }

    /** Restores canisters from the item's data component when placed. */
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
