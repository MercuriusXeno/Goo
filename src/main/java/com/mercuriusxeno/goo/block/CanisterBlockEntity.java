package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterPlacementValidator;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Block entity for the multi-canister block. Holds up to 9 canister items
 * in a 3x3 grid within a single block space. Each sub-canister retains its
 * own gasket UUIDs for the transport network.
 *
 * <p>Slot state delegated to {@link SlottedCanisterState}. Internal logic
 * delegated to: {@link CanisterSlotHandlers} (handler/pusher lifecycle),
 * {@link CanisterGasketOps} (gasket registration),
 * {@link CanisterEntitySerializer} (NBT),
 * {@link CanisterSlotLifecycle} (build/strip/shape/export).</p>
 */
public class CanisterBlockEntity extends BlockEntity implements ICanisterHolder, IGasketHolder {

    /** Maximum number of canister slots in the 3x3 grid. */
    public static final int MAX_SLOTS = 9;
    /** Center slot index in the 3x3 grid (default placement target). */
    static final int CENTER_SLOT = 4;
    /** NBT key for owner UUID. */
    private static final String TAG_OWNER_UUID = "OwnerUuid";

    /** Provides access to the gasket registry without a ServerLevel at call sites. */
    @Nullable IGasketRegistryAccess gasketRegistryAccess;

    /** Owner UUID: set when placed by a player. */
    private @Nullable UUID ownerUuid;

    /** Canister has no machine-level gaskets; slots manage their own. */
    private final GasketState gasketState = GasketState.none();

    /** Behavioral component owning slot arrays, handlers, and stream state. */
    private final SlottedCanisterState state;

    /**
     * Creates a new canister block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public CanisterBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CANISTER.get(), pos, state);
        this.state = new SlottedCanisterState(
            MAX_SLOTS,
            NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY),
            () -> BlockEntitySync.markDirtyAndSync(this),
            stacks -> CanisterSlotLifecycle.computeShape(stacks, MAX_SLOTS, CENTER_SLOT));
    }

    /** {@inheritDoc} */
    @Override
    public SlottedCanisterState containerState() { return state; }

    // --- Public canister API (delegated to CanisterSlotHandlers/CanisterGasketOps) ---

    /** Inserts a canister, optionally stripping gasket UUIDs for creative duplication.
     *
     * @param slot          the slot index
     * @param canisterStack the canister item stack to insert
     * @param stripGaskets  true to clear gasket UUIDs on the copy
     * @return true if inserted
     */
    public boolean insertCanister(int slot, ItemStack canisterStack, boolean stripGaskets) {
        if (level != null && !CanisterPlacementValidator.isSlotAllowed(level, worldPosition, slot)) {
            return false;
        }
        return CanisterSlotHandlers.insertCanister(this, slot, canisterStack, stripGaskets);
    }

    /** Removes the canister from the given slot.
     *
     * @param slot the slot index
     * @return the removed canister stack, or EMPTY
     */
    public ItemStack removeCanister(int slot) {
        return CanisterSlotHandlers.removeCanister(this, slot);
    }

    /** Returns the owner UUID, or null if unowned.
     *
     * @return the owner
     */
    @Nullable
    public UUID getOwner() { return ownerUuid; }

    /** Sets the owner UUID (called when placed by a player).
     *
     * @param owner the player UUID to set as owner
     */
    public void setOwner(UUID owner) {
        ownerUuid = owner;
        BlockEntitySync.markDirtyAndSync(this);
    }

    /** Assigns canister contents from a source ItemStack during initial placement.
     *
     * @param slot         target grid slot (clamped to center if out of range)
     * @param source       the held canister ItemStack being placed
     * @param stripGaskets true to clear gasket UUIDs (creative-mode duplication)
     */
    public void assignFromItemStack(int slot, ItemStack source, boolean stripGaskets) {
        int target = (slot < 0 || slot >= MAX_SLOTS) ? CENTER_SLOT : slot;
        ItemStack built = CanisterSlotLifecycle.buildCanisterFromStack(source);
        if (stripGaskets) { CanisterSlotLifecycle.stripGasketMetadata(built); }
        state.canisters.set(target, built);
        CanisterSlotHandlers.wireSlotAfterPlacement(this, target);
    }

    /** Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the canister block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CanisterBlockEntity be) {
        be.state.tickPushers();
    }

    // --- IGasketHolder ---

    /** {@inheritDoc} */
    @Override
    public GasketState gasketState() { return gasketState; }

    /** Slot-level gaskets always support both transmitter and receiver roles. */
    @Override
    public boolean supportsRole(GasketRole role) { return true; }

    /** {@inheritDoc} */
    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = CanisterBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    /** {@inheritDoc} */
    @Override
    public boolean allowsTuning(@Nullable UUID tunerOwner) {
        return ownerUuid == null || tunerOwner == null || tunerOwner.equals(ownerUuid);
    }

    /** {@inheritDoc} */
    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        IGasketHolder.super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER && slot >= 0 && slot < MAX_SLOTS) {
            CanisterSlotHandlers.rebuildSlotPusher(this, slot);
        }
    }

    // --- Framework lifecycle ---

    /** {@inheritDoc} */
    @Override
    public void setRemoved() {
        state.disposeAllPushers();
        CanisterGasketOps.deregisterAllGaskets(this);
        super.setRemoved();
    }

    /** {@inheritDoc} */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        CanisterEntitySerializer.saveCanisterList(output, state.canisters);
        if (ownerUuid != null) { output.store(TAG_OWNER_UUID, UUIDUtil.STRING_CODEC, ownerUuid); }
        CanisterEntitySerializer.saveStreamState(output, state.slots.streamType(), state.slots.streamFluid(), state.slots.streamRate(), state.slots.streamTick(), MAX_SLOTS);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        CanisterEntitySerializer.loadCanisterList(input, state.canisters, MAX_SLOTS);
        input.read(TAG_OWNER_UUID, UUIDUtil.STRING_CODEC).ifPresent(u -> ownerUuid = u);
        CanisterEntitySerializer.loadStreamState(input, state.slots.streamType(), state.slots.streamFluid(), state.slots.streamRate(), state.slots.streamTick(), MAX_SLOTS);
        CanisterSlotHandlers.rebuildAllSlotHandlers(this);
        state.invalidateShape();
    }

    /** {@inheritDoc} */
    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
            CanisterGasketOps.registerAllGaskets(this);
            CanisterSlotHandlers.rebuildAllSlotPushers(this);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        CanisterSlotHandlers.rebuildAllSlotPushers(this);
        CanisterGasketOps.forceAllTransmitterChunks(this, serverLevel);
    }

    /** {@inheritDoc} */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        int slot = CanisterSlotLifecycle.findSingleOccupiedSlot(state.canisters, MAX_SLOTS);
        if (slot >= 0) { CanisterSlotLifecycle.exportSlotComponents(builder, state.canisters.get(slot)); }
    }

}
