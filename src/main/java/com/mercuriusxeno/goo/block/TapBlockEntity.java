package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
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
import java.util.UUID;

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
    /** NBT key for the gasket UUID. */
    private static final String TAG_GASKET_ID = "GasketId";
    /** NBT key for the gasket partner. */
    private static final String TAG_PARTNER = "Partner";

    /** Drip interval in ticks (40 ticks = 2 seconds). */
    static final int DRIP_INTERVAL = 40;

    /** Volume extracted per drip (1 blob = 1,000 mB). */
    static final int DRIP_VOLUME = 1000;

    /** Canister stored in the tap's body slot. */
    private @NonNull ItemStack canister = ItemStack.EMPTY;

    /** Gasket UUID for the receiver gasket (null = no gasket installed). */
    private @Nullable UUID gasketId;

    /** Linked partner for the gasket. */
    private @Nullable GasketPartner partner;

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

    /** Returns the receiver gasket UUID, or null if not a receiver.
     *
     * @param role the gasket role
     * @return the gasket id
     */
    @Override
    public @Nullable UUID getGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER ? gasketId : null;
    }

    /** Creates a receiver gasket UUID if one does not exist.
     *
     * @param role the gasket role
     * @return the UUID, or null
     */
    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) {
        if (role != GasketRole.RECEIVER) { return null; }
        if (gasketId == null) {
            gasketId = UUID.randomUUID();
            setChanged();
        }
        return gasketId;
    }

    /** Returns the receiver's linked partner, or null if unlinked.
     *
     * @param role the gasket role
     * @return the partner
     */
    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.RECEIVER ? partner : null;
    }

    /** Sets the receiver's linked partner.
     *
     * @param role       the gasket role
     * @param newPartner the new gasket partner, or null to clear
     */
    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner newPartner) {
        if (role != GasketRole.RECEIVER) { return; }
        partner = newPartner;
        setChanged();
    }

    /** Clears the gasket when popped by removal or mutual exclusivity.
     *
     * @param role the gasket role
     */
    @Override
    public void clearGasket(GasketRole role) {
        if (role != GasketRole.RECEIVER) { return; }
        gasketId = null;
        partner = null;
        setChanged();
    }

    /** Tap only supports RECEIVER when a gasket is installed.
     *
     * @param role the gasket role
     * @return true if the condition is met
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return role == GasketRole.RECEIVER && getBlockState().getValue(TapBlock.HAS_GASKET);
    }

    /** Returns "tap" as the face label for tuner display.
     *
     * @param role the gasket role
     * @return the face label
     */
    @Override
    public @Nullable String getFaceLabel(GasketRole role) { return FACE_LABEL; }

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
        saveCanister(output);
        saveGasketState(output);
    }

    /** Persists the canister item stack if present.
     *
     * @param output the value output to write to
     */
    private void saveCanister(ValueOutput output) {
        if (!canister.isEmpty()) {
            output.store(TAG_CANISTER, ItemStack.CODEC, canister);
        }
    }

    /** Persists gasket ID and partner reference if set.
     *
     * @param output the value output to write to
     */
    private void saveGasketState(ValueOutput output) {
        if (gasketId != null) {
            output.putString(TAG_GASKET_ID, gasketId.toString());
        }
        if (partner != null) {
            output.store(TAG_PARTNER, GasketPartner.CODEC, partner);
        }
    }

    /** Restores canister and gasket state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        canister = input.read(TAG_CANISTER, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        String idStr = input.getStringOr(TAG_GASKET_ID, null);
        gasketId = idStr != null ? UUID.fromString(idStr) : null;
        partner = input.read(TAG_PARTNER, GasketPartner.CODEC).orElse(null);
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
