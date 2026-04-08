package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
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
import java.util.UUID;

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
    /** NBT key for the gasket UUID. */
    private static final String TAG_GASKET_ID = "GasketId";
    /** NBT key for the gasket partner. */
    private static final String TAG_PARTNER = "Partner";

    private ItemStack targetItem = ItemStack.EMPTY;

    /** Gasket UUID for the receiver gasket (null = no gasket installed). */
    private @Nullable UUID gasketId;

    /** Linked partner for the gasket. */
    private @Nullable GasketPartner partner;

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
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canisterBe.getCanister(slot).isEmpty()) { count++; }
        }
        return count;
    }

    // --- IGasketHolder (RECEIVER only) ---

    /** Returns the receiver gasket UUID, or null if not a receiver or no gasket assigned.
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

    /** Clears the receiver gasket UUID and partner.
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

    /** Only supports the RECEIVER role when a gasket is installed.
     *
     * @param role the gasket role
     * @return true if the condition is met
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return role == GasketRole.RECEIVER && getBlockState().getValue(PlexerBlock.HAS_GASKET);
    }

    /** Returns "plexer" as the face label for tuner display.
     *
     * @param role the gasket role
     * @return the face label
     */
    @Override
    public @Nullable String getFaceLabel(GasketRole role) { return FACE_LABEL; }

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
        GooValue required = lookup.lookup(targetId);
        if (required == null || required.isEmpty()) { return ItemStack.EMPTY; }
        if (lookup.isRestricted(targetId)) { return ItemStack.EMPTY; }

        List<SlotRef> slots = getExternalCanisterSlots();
        if (slots.isEmpty()) { return ItemStack.EMPTY; }

        if (!hasEnoughGoo(slots, required)) { return ItemStack.EMPTY; }

        consumeAllRequired(slots, required);
        setChanged();
        return new ItemStack(targetItem.getItem());
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
    private long getAvailableGoo(List<SlotRef> slots, GooType type) {
        long total = 0;
        for (SlotRef ref : slots) {
            GooContents contents = CanisterItem.getGooContents(ref.entity.getCanister(ref.slot));
            total += contents.getVolume(type);
        }
        return total;
    }

    /** Consumes the specified amount of a goo type across external canister slots.
     *
     * @param slots  the list of slot references
     * @param type   the goo type
     * @param amount volume in microblobs
     */
    private void consumeGoo(List<SlotRef> slots, GooType type, long amount) {
        long remaining = amount;
        for (SlotRef ref : slots) {
            if (remaining <= 0) { break; }
            ItemStack stack = ref.entity.getCanister(ref.slot);
            if (stack.isEmpty()) { continue; }
            long removed = CanisterItem.removeGoo(stack, type, remaining);
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
        if (gasketId != null) {
            output.putString(TAG_GASKET_ID, gasketId.toString());
        }
        if (partner != null) {
            output.store(TAG_PARTNER, GasketPartner.CODEC, partner);
        }
    }

    /** Restores target item and gasket state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        targetItem = input.read(TAG_TARGET_ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        String idStr = input.getStringOr(TAG_GASKET_ID, null);
        gasketId = idStr != null ? UUID.fromString(idStr) : null;
        partner = input.read(TAG_PARTNER, GasketPartner.CODEC).orElse(null);
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
