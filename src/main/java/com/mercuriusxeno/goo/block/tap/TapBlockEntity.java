package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
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
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tap block entity: drips goo from a canister placed in its body slot.
 * On a timer, extracts 1 blob (1,000 mB) from the canister and spawns it
 * as a blob item entity below the spigot. Optionally has a choral gasket
 * for remote fluid reception (RECEIVER role).
 */
public class TapBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements ICanisterHolder, IGasketHolder {

    /**
     * The tap has exactly one canister slot.
     */
    public static final int SLOT = 0;
    /**
     * Drip interval in ticks (40 ticks = 2 seconds).
     */
    static final int DRIP_INTERVAL = 40;
    /**
     * Volume extracted per drip (1 blob = 1,000 mB).
     */
    static final int DRIP_VOLUME = 1000;
    /**
     * Face label returned for tuner display.
     */
    private static final String FACE_LABEL = "tap";
    /**
     * NBT key for the canister item.
     */
    private static final String TAG_CANISTER = "Canister";
    /**
     * Slot state holding the single canister.
     */
    private final SlottedCanisterData state;

    /**
     * Composed gasket state for the RECEIVER role.
     */
    private final GasketState gasketState = GasketState.single(GasketRole.RECEIVER, FACE_LABEL);

    /**
     * Creates a new tap block entity.
     *
     * @param pos    the block position
     * @param bstate the block state
     */
    public TapBlockEntity(BlockPos pos, BlockState bstate) {
        super(GooBlockEntities.TAP.get(), pos, bstate);
        this.state = new SlottedCanisterData(1,
                i -> Shapes.empty(),
                slots -> Shapes.empty(),
                () -> BlockEntitySync.markDirtyAndSync(this));
    }

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

    // --- Canister slot (single-slot convenience) ---

    /**
     * {@inheritDoc}
     */
    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    /**
     * Returns the canister in the tap's slot (may be EMPTY).
     *
     * @return the canister item stack, or EMPTY if none is inserted
     */
    public @NonNull ItemStack getCanister() {
        return state.getCanister(SLOT);
    }

    /**
     * Inserts a canister into the tap's slot. Returns false if the slot is occupied.
     *
     * @param stack the canister item stack to insert
     * @return true if the canister was inserted, false if slot was occupied
     */
    public boolean insertCanister(ItemStack stack) {
        if (!getCanister().isEmpty()) {
            return false;
        }
        state.slots[SLOT].setCanister(stack.copyWithCount(1));
        state.slots[SLOT].buildHandler(() -> level != null ? level.getGameTime() : 0L);
        markDirtyAndSync();
        return true;
    }

    // --- Goo pass-through (delegates to ICanisterHolder slot 0) ---

    /**
     * Removes and returns the canister from the tap's slot.
     *
     * @return the removed canister item stack, or EMPTY if slot was empty
     */
    public @NonNull ItemStack removeCanister() {
        ItemStack current = getCanister();
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        state.slots[SLOT].clear();
        markDirtyAndSync();
        return current;
    }

    /**
     * Returns the fluid content of the inserted canister, or EMPTY.
     *
     * @return the fluid content of the inserted canister, or EMPTY
     */
    public CanisterFluidContent getFluidContent() {
        return getSlotFluidContent(SLOT);
    }

    /**
     * Inserts goo into the canister. Returns the amount actually accepted.
     *
     * @param type   the goo type to insert
     * @param volume volume in microblobs to insert
     * @return the amount actually accepted (mB)
     */
    public int insertGoo(GooType type, int volume) {
        return insertGoo(SLOT, type, volume);
    }

    /**
     * Extracts goo from the canister. Returns the amount actually removed.
     *
     * @param type      the goo type to extract
     * @param requested the desired volume in microblobs
     * @return the amount actually extracted (mB)
     */
    public int extractGoo(GooType type, int requested) {
        return extractGoo(SLOT, type, requested);
    }

    // --- IGasketHolder (RECEIVER only) ---

    /**
     * Returns true if the canister has remaining capacity.
     *
     * @return true if the canister has remaining capacity for goo
     */
    public boolean canAcceptGoo() {
        return canAccept(SLOT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GasketState gasketState() {
        return gasketState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Runnable gasketSyncCallback() {
        return this::setChanged;
    }

    // --- Tick and drip logic ---

    /**
     * {@inheritDoc} Checks blockstate in addition to role.
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return role == GasketRole.RECEIVER && getBlockState().getValue(TapBlock.HAS_GASKET);
    }

    /**
     * Marks dirty and sends sync packet to tracking clients.
     */
    private void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    // --- Serialization ---

    /**
     * Persists canister and gasket state.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        ItemStack can = getCanister();
        if (!can.isEmpty()) {
            output.store(TAG_CANISTER, ItemStack.CODEC, can);
        }
        gasketState.save(output);
    }

    /**
     * Restores canister and gasket state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        ItemStack loaded = input.read(TAG_CANISTER, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        state.slots[SLOT].setCanister(loaded);
        if (!loaded.isEmpty()) {
            state.slots[SLOT].buildHandler(() -> level != null ? level.getGameTime() : 0L);
        }
        gasketState.load(input);
    }

    /**
     * Returns full NBT for initial chunk sync to clients.
     *
     * @param registries the registry provider
     * @return the update tag
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /**
     * Returns the sync packet sent when block entity data changes.
     *
     * @return the update packet
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
