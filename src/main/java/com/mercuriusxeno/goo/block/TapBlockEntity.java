package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    /** Drip interval in ticks (40 ticks = 2 seconds). */
    static final int DRIP_INTERVAL = 40;

    /** Volume extracted per drip (1 blob = 1,000 mB). */
    static final int DRIP_VOLUME = 1000;

    private int timer = 0;

    /** Canister stored in the tap's body slot. */
    private @NonNull ItemStack canister = ItemStack.EMPTY;

    /** Gasket UUID for the receiver gasket (null = no gasket installed). */
    private @Nullable UUID gasketId;

    /** Linked partner for the gasket. */
    private @Nullable GasketPartner partner;

    /** Creates a new tap block entity. */
    public TapBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.TAP.get(), pos, state);
    }

    // --- Canister slot ---

    /** Returns the canister in the tap's slot (may be EMPTY). */
    public @NonNull ItemStack getCanister() {
        return canister;
    }

    /**
     * Inserts a canister into the tap's slot. Returns false if the slot is occupied.
     */
    public boolean insertCanister(ItemStack stack) {
        if (!canister.isEmpty()) return false;
        canister = stack.copyWithCount(1);
        markDirtyAndSync();
        return true;
    }

    /** Removes and returns the canister from the tap's slot. */
    public @NonNull ItemStack removeCanister() {
        if (canister.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = canister;
        canister = ItemStack.EMPTY;
        markDirtyAndSync();
        return removed;
    }

    // --- Goo pass-through (delegates to canister ItemStack) ---

    /** Returns the goo contents of the inserted canister, or EMPTY. */
    public GooContents getGooContents() {
        return canister.isEmpty() ? GooContents.EMPTY : CanisterItem.getGooContents(canister);
    }

    /** Inserts goo into the canister. Returns the amount actually accepted. */
    public long insertGoo(GooType type, long volume) {
        if (canister.isEmpty()) return 0L;
        long accepted = CanisterItem.addGoo(canister, type, volume);
        if (accepted > 0) markDirtyAndSync();
        return accepted;
    }

    /** Extracts goo from the canister. Returns the amount actually removed. */
    public long extractGoo(GooType type, long requested) {
        if (canister.isEmpty()) return 0L;
        long removed = CanisterItem.removeGoo(canister, type, requested);
        if (removed > 0) markDirtyAndSync();
        return removed;
    }

    /** Returns true if the canister has remaining capacity. */
    public boolean canAcceptGoo() {
        if (canister.isEmpty()) return false;
        GooContents contents = CanisterItem.getGooContents(canister);
        int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(canister);
        return contents.totalVolume() < com.mercuriusxeno.goo.item.ContainerCapacity.canisterCapacity(compression);
    }

    // --- IGasketHolder (RECEIVER only) ---

    @Override
    public @Nullable UUID getGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER ? gasketId : null;
    }

    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) {
        if (role != GasketRole.RECEIVER) return null;
        if (gasketId == null) {
            gasketId = UUID.randomUUID();
            setChanged();
        }
        return gasketId;
    }

    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.RECEIVER ? partner : null;
    }

    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner newPartner) {
        if (role != GasketRole.RECEIVER) return;
        partner = newPartner;
        setChanged();
    }

    /** Clears the gasket when popped by removal or mutual exclusivity. */
    @Override
    public void clearGasket(GasketRole role) {
        if (role != GasketRole.RECEIVER) return;
        gasketId = null;
        partner = null;
        setChanged();
    }

    /** Tap only supports RECEIVER when a gasket is installed. */
    @Override
    public boolean supportsRole(GasketRole role) {
        if (role != GasketRole.RECEIVER) return false;
        return getBlockState().getValue(TapBlock.HAS_GASKET);
    }

    @Override
    public @Nullable String getFaceLabel(GasketRole role) { return "tap"; }

    // --- Tick and drip logic ---

    /**
     * Server tick handler. Currently a no-op: dripping is blocked until entity
     * blobs exist. The tap's intended function is to drop entity blobs, not
     * item blobs - that system is WIP/todo.
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

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (!canister.isEmpty()) {
            output.store("Canister", ItemStack.CODEC, canister);
        }
        if (gasketId != null) {
            output.putString("GasketId", gasketId.toString());
        }
        if (partner != null) {
            output.store("Partner", GasketPartner.CODEC, partner);
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        canister = input.read("Canister", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        String idStr = input.getStringOr("GasketId", null);
        gasketId = idStr != null ? UUID.fromString(idStr) : null;
        partner = input.read("Partner", GasketPartner.CODEC).orElse(null);
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
}
