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

    private ItemStack targetItem = ItemStack.EMPTY;

    /** Gasket UUID for the receiver gasket (null = no gasket installed). */
    private @Nullable UUID gasketId;

    /** Linked partner for the gasket. */
    private @Nullable GasketPartner partner;

    public PlexerBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.PLEXER.get(), pos, state);
    }

    // --- ICanisterAttachable ---

    @Override
    public int maxTopAttachments() { return SLOT_COUNT; }

    @Override
    public int currentTopAttachments() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        if (!(level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe)) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canisterBe.getCanister(slot).isEmpty()) count++;
        }
        return count;
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

    @Override
    public void clearGasket(GasketRole role) {
        if (role != GasketRole.RECEIVER) return;
        gasketId = null;
        partner = null;
        setChanged();
    }

    @Override
    public boolean supportsRole(GasketRole role) {
        if (role != GasketRole.RECEIVER) return false;
        return getBlockState().getValue(PlexerBlock.HAS_GASKET);
    }

    @Override
    public @Nullable String getFaceLabel(GasketRole role) { return "plexer"; }

    // --- Target item ---

    /** Returns the target item in the observer slot. */
    public ItemStack getTargetItem() { return targetItem; }

    public void setTargetItem(ItemStack stack) {
        this.targetItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    // --- Reconstitution (reads from external CanisterBlock above) ---

    /**
     * Attempts to reconstitute the target item from canister goo.
     * @return the result item, or empty if insufficient goo
     */
    public ItemStack tryReconstitute() {
        if (targetItem.isEmpty()) return ItemStack.EMPTY;
        Identifier targetId = BuiltInRegistries.ITEM.getKey(targetItem.getItem());
        return tryReconstitute(targetId, Goo.GOO_VALUES);
    }

    /** Testable seam: reconstitutes by Identifier without registry coupling. */
    ItemStack tryReconstitute(Identifier targetId, IGooValueLookup lookup) {
        GooValue required = lookup.lookup(targetId);
        if (required == null || required.isEmpty()) return ItemStack.EMPTY;

        List<SlotRef> slots = getExternalCanisterSlots();
        if (slots.isEmpty()) return ItemStack.EMPTY;

        if (!hasEnoughGoo(slots, required)) return ItemStack.EMPTY;

        consumeAllRequired(slots, required);
        setChanged();
        return new ItemStack(targetItem.getItem());
    }

    /** Returns true if all required goo types are available in sufficient quantity. */
    private boolean hasEnoughGoo(List<SlotRef> slots, GooValue required) {
        for (Map.Entry<GooType, Integer> entry : required.getAll().entrySet()) {
            if (getAvailableGoo(slots, entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    /** Consumes all required goo types from external canister slots. */
    private void consumeAllRequired(List<SlotRef> slots, GooValue required) {
        for (Map.Entry<GooType, Integer> entry : required.getAll().entrySet()) {
            consumeGoo(slots, entry.getKey(), entry.getValue());
        }
    }

    /** Reference to a canister slot in the CanisterBlock above. */
    private record SlotRef(CanisterBlockEntity entity, int slot) {}

    /** Returns slot references for all occupied canister slots above. */
    private List<SlotRef> getExternalCanisterSlots() {
        if (level == null) return List.of();
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

    /** Sums the available volume of a specific goo type across external canister slots. */
    private long getAvailableGoo(List<SlotRef> slots, GooType type) {
        long total = 0;
        for (SlotRef ref : slots) {
            GooContents contents = CanisterItem.getGooContents(ref.entity.getCanister(ref.slot));
            total += contents.getVolume(type);
        }
        return total;
    }

    /** Consumes the specified amount of a goo type across external canister slots. */
    private void consumeGoo(List<SlotRef> slots, GooType type, long amount) {
        long remaining = amount;
        for (SlotRef ref : slots) {
            if (remaining <= 0) break;
            ItemStack stack = ref.entity.getCanister(ref.slot);
            if (stack.isEmpty()) continue;
            long removed = CanisterItem.removeGoo(stack, type, remaining);
            remaining -= removed;
        }
    }

    // --- Serialization ---

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (!targetItem.isEmpty()) {
            output.store("TargetItem", ItemStack.CODEC, targetItem);
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
        targetItem = input.read("TargetItem", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        String idStr = input.getStringOr("GasketId", null);
        gasketId = idStr != null ? UUID.fromString(idStr) : null;
        partner = input.read("Partner", GasketPartner.CODEC).orElse(null);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
