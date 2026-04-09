package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tap block entity: drips goo from a canister placed in its body slot.
 * On a timer, extracts 1 blob (1,000 mB) from the canister and spawns it
 * as a blob item entity below the spigot. Optionally has a choral gasket
 * for remote fluid reception (RECEIVER role).
 */
public class TapBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements IGasketHolder {

    /** Face label returned for tuner display. */
    private static final String FACE_LABEL = "tap";
    /** NBT key for the canister item. */
    private static final String TAG_CANISTER = "Canister";

    /** Drip interval in ticks (40 ticks = 2 seconds). */
    static final int DRIP_INTERVAL = 40;

    /** Volume extracted per drip (1 blob = 1,000 mB). */
    static final int DRIP_VOLUME = 1000;

    /** Canister stored in the tap's body slot. */
    private @NonNull ItemStack canister = ItemStack.EMPTY;

    /** Composed gasket state for the RECEIVER role. */
    private final GasketState gasketState = GasketState.single(GasketRole.RECEIVER, FACE_LABEL);

    /** Creates a new tap block entity.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public TapBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.TAP.get(), pos, state);
    }

    // --- Canister slot ---

    /** Returns the canister in the tap's slot (may be EMPTY).
     *
     * @return the canister
     */
    public @NonNull ItemStack getCanister() {
        return canister;
    }

    /**
     * Inserts a canister into the tap's slot. Returns false if the slot is occupied.
     *
     * @param stack the item stack
     * @return true if the condition is met
     */
    public boolean insertCanister(ItemStack stack) {
        if (!canister.isEmpty()) { return false; }
        canister = stack.copyWithCount(1);
        markDirtyAndSync();
        return true;
    }

    /** Removes and returns the canister from the tap's slot.
     *
     * @return the item stack
     */
    public @NonNull ItemStack removeCanister() {
        if (canister.isEmpty()) { return ItemStack.EMPTY; }
        ItemStack removed = canister;
        canister = ItemStack.EMPTY;
        markDirtyAndSync();
        return removed;
    }

    // --- Goo pass-through (delegates to canister ItemStack) ---

    /** Returns the goo contents of the inserted canister, or EMPTY.
     *
     * @return the goo contents
     */
    public GooContents getGooContents() {
        return canister.isEmpty() ? GooContents.EMPTY : CanisterItem.getGooContents(canister);
    }

    /** Inserts goo into the canister. Returns the amount actually accepted.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the long value
     */
    public long insertGoo(GooType type, long volume) {
        if (canister.isEmpty()) { return 0L; }
        long accepted = CanisterItem.addGoo(canister, type, volume);
        if (accepted > 0) { markDirtyAndSync(); }
        return accepted;
    }

    /** Extracts goo from the canister. Returns the amount actually removed.
     *
     * @param type      the goo type
     * @param requested volume in microblobs to extract
     * @return the long value
     */
    public long extractGoo(GooType type, long requested) {
        if (canister.isEmpty()) { return 0L; }
        long removed = CanisterItem.removeGoo(canister, type, requested);
        if (removed > 0) { markDirtyAndSync(); }
        return removed;
    }

    /** Returns true if the canister has remaining capacity.
     *
     * @return true if accept goo
     */
    public boolean canAcceptGoo() {
        if (canister.isEmpty()) { return false; }
        GooContents contents = CanisterItem.getGooContents(canister);
        int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(canister);
        return contents.totalVolume() < com.mercuriusxeno.goo.item.ContainerCapacity.canisterCapacity(compression);
    }

    // --- IGasketHolder (RECEIVER only) ---

    /** {@inheritDoc} */
    @Override
    public GasketState gasketState() { return gasketState; }

    /** {@inheritDoc} */
    @Override
    public Runnable gasketSyncCallback() { return this::setChanged; }

    /** {@inheritDoc} Checks blockstate in addition to role. */
    @Override
    public boolean supportsRole(GasketRole role) {
        return role == GasketRole.RECEIVER && getBlockState().getValue(TapBlock.HAS_GASKET);
    }

    // --- Tick and drip logic ---

    /**
     * Server tick handler. Currently a no-op: dripping is blocked until entity
     * blobs exist. The tap's intended function is to drop entity blobs, not
     * item blobs - that system is WIP/todo.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param tap   the tap block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
            TapBlockEntity tap) {
        // TODO: implement entity-blob dripping once the blob entity type exists
    }

    /** Marks dirty and sends sync packet to tracking clients. */
    private void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    // --- Serialization ---

    /** Persists canister and gasket state.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (!canister.isEmpty()) {
            output.store(TAG_CANISTER, ItemStack.CODEC, canister);
        }
        gasketState.save(output);
    }

    /** Restores canister and gasket state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        canister = input.read(TAG_CANISTER, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        gasketState.load(input);
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
}
