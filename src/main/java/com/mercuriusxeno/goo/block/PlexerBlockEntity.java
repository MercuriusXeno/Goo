package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plexer: reconstitutes items from goo in externally-attached canisters.
 * All 9 copper fittings on the top face are always available (no slot
 * constraints or matrix upgrades).
 */
public class PlexerBlockEntity extends BlockEntity implements ICanisterAttachable, IGasketHolder {

    /** Total canister positions on the 3x3 grid. */
    private static final int SLOT_COUNT = 9;
    /** Face label returned for tuner display. */
    private static final String FACE_LABEL = "plexer";
    /** NBT key for the observer target item. */
    private static final String TAG_TARGET_ITEM = "TargetItem";

    private ItemStack targetItem = ItemStack.EMPTY;

    /** Composed gasket state for the RECEIVER role. */
    private final GasketState gasketState = GasketState.single(GasketRole.RECEIVER, FACE_LABEL);

    /** Creates a plexer block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public PlexerBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.PLEXER.get(), pos, state);
    }

    // --- ICanisterAttachable ---

    /** Returns 9 (all copper fitting positions on the top face).
     *
     * @return the integer value
     */
    @Override
    public int maxTopAttachments() { return SLOT_COUNT; }

    /** Counts the occupied canister slots in the CanisterBlock above.
     *
     * @return the integer value
     */
    @Override
    public int currentTopAttachments() {
        if (level == null) { return 0; }
        BlockPos above = worldPosition.above();
        if (!(level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe)) {
            return 0;
        }
        return countOccupiedSlots(canisterBe);
    }

    /** Counts non-empty canister slots in the given block entity.
     *
     * @param canisterBe the canister block entity to inspect
     * @return the number of occupied slots
     */
    private int countOccupiedSlots(CanisterBlockEntity canisterBe) {
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canisterBe.getCanister(slot).isEmpty()) { count++; }
        }
        return count;
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
        return role == GasketRole.RECEIVER && getBlockState().getValue(PlexerBlock.HAS_GASKET);
    }

    // --- Target item ---

    /** Returns the target item in the observer slot.
     *
     * @return the target item
     */
    public ItemStack getTargetItem() { return targetItem; }

    /** Sets the target item for reconstitution (single-count copy, or EMPTY to clear).
     *
     * @param stack the item stack
     */
    public void setTargetItem(ItemStack stack) {
        this.targetItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    // --- Reconstitution (reads from external CanisterBlock above) ---

    /**
     * Attempts to reconstitute the target item from canister goo.
     * @return true if enough goo of reconstitute item, or empty if insufficient goo
     */
    public ItemStack tryReconstitute() {
        if (targetItem.isEmpty()) { return ItemStack.EMPTY; }
        Identifier targetId = BuiltInRegistries.ITEM.getKey(targetItem.getItem());
        return tryReconstitute(targetId, Goo.GOO_VALUES);
    }

    /** Testable seam: reconstitutes by Identifier without registry coupling.
     *
     * @param targetId the target item registry ID
     * @param lookup   the goo value lookup
     * @return the external canister slots
     */
    ItemStack tryReconstitute(Identifier targetId, IGooValueLookup lookup) {
        GooValue required = resolveRequired(targetId, lookup);
        if (required == null) { return ItemStack.EMPTY; }

        List<SlotRef> slots = getExternalCanisterSlots();
        if (slots.isEmpty()) { return ItemStack.EMPTY; }
        if (!hasEnoughGoo(slots, required)) { return ItemStack.EMPTY; }

        consumeAllRequired(slots, required);
        setChanged();
        return new ItemStack(targetItem.getItem());
    }

    /** Looks up the goo value for a target, returning null if absent or restricted.
     *
     * @param targetId the target item registry ID
     * @param lookup   the goo value lookup
     * @return the required goo value, or null if unavailable
     */
    private @Nullable GooValue resolveRequired(Identifier targetId, IGooValueLookup lookup) {
        GooValue required = lookup.lookup(targetId);
        if (required == null || required.isEmpty()) { return null; }
        if (lookup.isRestricted(targetId)) { return null; }
        return required;
    }

    /** Returns true if all required goo types are available in sufficient quantity.
     *
     * @param slots    the list of slot references
     * @param required the required goo value
     * @return the available goo
     */
    private boolean hasEnoughGoo(List<SlotRef> slots, GooValue required) {
        for (Map.Entry<GooType, Integer> entry : required.getAll().entrySet()) {
            if (getAvailableGoo(slots, entry.getKey()) < entry.getValue()) { return false; }
        }
        return true;
    }

    /** Consumes all required goo types from external canister slots.
     *
     * @param slots    the list of slot references
     * @param required the required goo value
     */
    private void consumeAllRequired(List<SlotRef> slots, GooValue required) {
        for (Map.Entry<GooType, Integer> entry : required.getAll().entrySet()) {
            consumeGoo(slots, entry.getKey(), entry.getValue());
        }
    }

    /** Reference to a canister slot in the CanisterBlock above.
     *
     * @param entity the item entity
     * @param slot   the slot index
     */
    private record SlotRef(CanisterBlockEntity entity, int slot) {}

    /** Returns slot references for all occupied canister slots above.
     *
     * @return the update tag
     */
    private List<SlotRef> getExternalCanisterSlots() {
        if (level == null) { return List.of(); }
        BlockPos above = worldPosition.above();
        if (!(level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe)) {
            return List.of();
        }
        return collectOccupiedSlotRefs(canisterBe);
    }

    /** Builds slot references for all occupied positions in the canister block entity.
     *
     * @param canisterBe the canister block entity above this plexer
     * @return list of occupied slot references
     */
    private List<SlotRef> collectOccupiedSlotRefs(CanisterBlockEntity canisterBe) {
        List<SlotRef> refs = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canisterBe.getCanister(slot).isEmpty()) {
                refs.add(new SlotRef(canisterBe, slot));
            }
        }
        return refs;
    }

    /** Sums the available volume of a specific goo type across external canister slots.
     *
     * @param slots the list of slot references
     * @param type  the goo type
     * @return the update packet
     */
    private int getAvailableGoo(List<SlotRef> slots, GooType type) {
        int total = 0;
        for (SlotRef ref : slots) {
            CanisterFluidContent content = CanisterItem.getFluidContent(ref.entity.getCanister(ref.slot));
            total += (content.getGooType() == type) ? content.amount() : 0;
        }
        return total;
    }

    /** Consumes the specified amount of a goo type across external canister slots.
     *
     * @param slots  the list of slot references
     * @param type   the goo type
     * @param amount volume in microblobs
     */
    private void consumeGoo(List<SlotRef> slots, GooType type, int amount) {
        int remaining = amount;
        for (SlotRef ref : slots) {
            if (remaining <= 0) { break; }
            ItemStack stack = ref.entity.getCanister(ref.slot);
            if (stack.isEmpty()) { continue; }
            int removed = CanisterItem.removeGoo(stack, type, remaining);
            remaining -= removed;
        }
    }

    // --- Serialization ---

    /** Persists target item and gasket state.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (!targetItem.isEmpty()) {
            output.store(TAG_TARGET_ITEM, ItemStack.CODEC, targetItem);
        }
        gasketState.save(output);
    }

    /** Restores target item and gasket state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        targetItem = input.read(TAG_TARGET_ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        gasketState.load(input);
    }

    /** Returns full NBT for initial chunk sync to clients.
     *
     * @param registries the registry provider
     * @return the result
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** Returns the sync packet sent when block entity data changes.
     *
     * @return the result
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
