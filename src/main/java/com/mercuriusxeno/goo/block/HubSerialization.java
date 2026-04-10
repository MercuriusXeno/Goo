package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Static helpers for hub stream-state serialization and chunk forcing.
 * Extracted from HubBlockEntity to keep the framework override surface
 * under the TooManyMethods threshold.
 */
final class HubSerialization {

    /** NBT key for stream state compound. */
    private static final String TAG_STREAMS = "Streams";
    /** NBT key for stream goo type ordinal. */
    private static final String TAG_TYPE = "type";
    /** NBT key for stream transfer rate. */
    private static final String TAG_RATE = "rate";
    /** NBT key for stream start tick. */
    private static final String TAG_TICK = "tick";
    /** Sentinel value indicating an invalid NBT ordinal. */
    private static final int INVALID_ORDINAL = -1;

    private HubSerialization() {}

    /**
     * Serializes per-slot stream state for client sync.
     *
     * @param be     the hub block entity
     * @param output the value output to write to
     */
    static void saveStreamState(HubBlockEntity be, ValueOutput output) {
        SlottedCanisterState state = be.containerState();
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            appendSlotStream(state, i, tag);
        }
        if (!tag.isEmpty()) {
            output.store(TAG_STREAMS, CompoundTag.CODEC, tag);
        }
    }

    /** Appends a single slot's stream state to the compound tag if present.
     *
     * @param state the container state
     * @param slot  the slot index
     * @param tag   the parent compound tag to append to
     */
    private static void appendSlotStream(SlottedCanisterState state, int slot, CompoundTag tag) {
        if (state.slots.streamType()[slot] == null) { return; }
        CompoundTag entry = new CompoundTag();
        entry.putInt(TAG_TYPE, state.slots.streamType()[slot].ordinal());
        entry.putInt(TAG_RATE, state.slots.streamRate()[slot]);
        entry.putLong(TAG_TICK, state.slots.streamTick()[slot]);
        tag.put(String.valueOf(slot), entry);
    }

    /**
     * Restores per-slot stream state from the update tag.
     *
     * @param be    the hub block entity
     * @param input the value input to read from
     */
    static void loadStreamState(HubBlockEntity be, ValueInput input) {
        SlottedCanisterState state = be.containerState();
        input.read(TAG_STREAMS, CompoundTag.CODEC).ifPresentOrElse(
            tag -> restoreAllSlotStreams(state, tag),
            () -> clearAllSlotStreams(state));
    }

    /** Restores stream state for all slots from a compound tag.
     *
     * @param state the container state
     * @param tag   the compound tag containing slot stream data
     */
    private static void restoreAllSlotStreams(SlottedCanisterState state, CompoundTag tag) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            restoreSlotStream(state, i, tag);
        }
    }

    /** Restores a single slot's stream state from the tag, or clears it.
     *
     * @param state the container state
     * @param slot  the slot index
     * @param tag   the parent compound tag
     */
    private static void restoreSlotStream(SlottedCanisterState state, int slot, CompoundTag tag) {
        String key = String.valueOf(slot);
        if (!tag.contains(key)) {
            clearSlotStream(state, slot);
            return;
        }
        applySlotStreamEntry(state, slot, tag.getCompoundOrEmpty(key));
    }

    /**
     * Applies deserialized stream fields from a compound tag to a single slot.
     * @param state the container state to update
     * @param slot the slot index
     * @param entry the compound tag containing type, rate, and tick fields
     */
    private static void applySlotStreamEntry(SlottedCanisterState state, int slot, CompoundTag entry) {
        int ordinal = entry.getIntOr(TAG_TYPE, INVALID_ORDINAL);
        GooType[] types = GooType.values();
        state.slots.streamType()[slot] = ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
        state.slots.streamRate()[slot] = entry.getIntOr(TAG_RATE, 0);
        state.slots.streamTick()[slot] = entry.getLongOr(TAG_TICK, 0);
    }

    /** Clears stream state for all slots.
     *
     * @param state the container state
     */
    private static void clearAllSlotStreams(SlottedCanisterState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            clearSlotStream(state, i);
        }
    }

    /**
     * Zeroes out the stream state for a single slot.
     *
     * @param state the container state
     * @param slot  the slot index
     */
    private static void clearSlotStream(SlottedCanisterState state, int slot) {
        state.slots.streamType()[slot] = null;
        state.slots.streamRate()[slot] = 0;
        state.slots.streamTick()[slot] = 0;
    }

    /**
     * Forces the chunk of each receiver gasket's transmitter partner.
     *
     * @param be          the hub block entity
     * @param serverLevel the server level
     * @param pos         the block position
     */
    static void forceAllTransmitterChunks(HubBlockEntity be, ServerLevel serverLevel, BlockPos pos) {
        IGasketRegistryAccess access = () -> GasketRegistry.get(serverLevel);
        GasketPusher.forceTransmitterChunk(
            be.gasketState().getId(GasketRole.RECEIVER), access, serverLevel, pos);
        forceSlotTransmitterChunks(be.containerState(), access, serverLevel, pos);
    }

    /** Forces the chunk for each occupied slot's top gasket transmitter.
     *
     * @param state       the container state
     * @param access      the gasket registry accessor
     * @param serverLevel the server level
     * @param pos         the block position
     */
    private static void forceSlotTransmitterChunks(
            SlottedCanisterState state, IGasketRegistryAccess access,
            ServerLevel serverLevel, BlockPos pos) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            ItemStack stack = state.canisters.get(i);
            if (stack.isEmpty()) { continue; }
            CanisterMetadata meta = CanisterItem.getMetadata(stack);
            GasketPusher.forceTransmitterChunk(meta.topGasketId(), access, serverLevel, pos);
        }
    }
}
