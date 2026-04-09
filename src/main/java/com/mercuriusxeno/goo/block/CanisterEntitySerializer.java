package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Static serialization helpers for CanisterBlockEntity. Handles per-slot
 * stream state (type, rate, tick) and canister list persistence. Extracted
 * to keep the entity class focused on framework overrides and slot coordination.
 */
final class CanisterEntitySerializer {

    /** NBT key for canister inventory list. */
    static final String TAG_CANISTERS = "Canisters";
    /** NBT key for stream state compound. */
    static final String TAG_STREAMS = "Streams";
    /** NBT key for stream goo type ordinal. */
    private static final String TAG_TYPE = "type";
    /** NBT key for stream transfer rate. */
    private static final String TAG_RATE = "rate";
    /** NBT key for stream start tick. */
    private static final String TAG_TICK = "tick";
    /** Sentinel value indicating an invalid NBT ordinal. */
    private static final int INVALID_ORDINAL = -1;

    private CanisterEntitySerializer() {}

    /**
     * Serializes per-slot stream state to a ValueOutput.
     *
     * @param output         the value output to write to
     * @param slotStreamType per-slot stream goo types (nullable elements)
     * @param slotStreamRate per-slot stream rates
     * @param slotStreamTick per-slot stream ticks
     * @param maxSlots       number of slots
     */
    static void saveStreamState(
            ValueOutput output, @Nullable GooType[] slotStreamType,
            int[] slotStreamRate, long[] slotStreamTick, int maxSlots) {
        CompoundTag tag = buildStreamTag(slotStreamType, slotStreamRate, slotStreamTick, maxSlots);
        if (!tag.isEmpty()) {
            output.store(TAG_STREAMS, CompoundTag.CODEC, tag);
        }
    }

    /**
     * Collects non-null slot streams into a compound tag keyed by slot index.
     *
     * @param slotStreamType per-slot stream goo types (nullable elements)
     * @param slotStreamRate per-slot stream rates
     * @param slotStreamTick per-slot stream ticks
     * @param maxSlots       number of slots
     * @return the compound tag with stream data
     */
    private static CompoundTag buildStreamTag(
            @Nullable GooType[] slotStreamType, int[] slotStreamRate,
            long[] slotStreamTick, int maxSlots) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < maxSlots; i++) {
            if (slotStreamType[i] != null) {
                tag.put(String.valueOf(i), serializeSlotStream(
                        slotStreamType[i], slotStreamRate[i], slotStreamTick[i]));
            }
        }
        return tag;
    }

    /**
     * Serializes a single slot's stream type, rate, and tick into a CompoundTag.
     *
     * @param type the goo type
     * @param rate the transfer rate
     * @param tick the game tick
     * @return the serialized stream tag
     */
    private static CompoundTag serializeSlotStream(GooType type, int rate, long tick) {
        CompoundTag slotTag = new CompoundTag();
        slotTag.putInt(TAG_TYPE, type.ordinal());
        slotTag.putInt(TAG_RATE, rate);
        slotTag.putLong(TAG_TICK, tick);
        return slotTag;
    }

    /**
     * Restores per-slot stream state from a ValueInput.
     *
     * @param input          the value input to read from
     * @param slotStreamType per-slot stream goo types (nullable elements, written in-place)
     * @param slotStreamRate per-slot stream rates (written in-place)
     * @param slotStreamTick per-slot stream ticks (written in-place)
     * @param maxSlots       number of slots
     */
    static void loadStreamState(
            ValueInput input, @Nullable GooType[] slotStreamType,
            int[] slotStreamRate, long[] slotStreamTick, int maxSlots) {
        input.read(TAG_STREAMS, CompoundTag.CODEC).ifPresentOrElse(
            tag -> deserializeAllSlotStreams(tag, slotStreamType, slotStreamRate, slotStreamTick, maxSlots),
            () -> clearAllSlotStreams(slotStreamType, slotStreamRate, slotStreamTick, maxSlots));
    }

    /**
     * Deserializes stream state for all slots from a compound tag.
     *
     * @param tag            the compound tag containing per-slot stream data
     * @param slotStreamType per-slot stream goo types (written in-place)
     * @param slotStreamRate per-slot stream rates (written in-place)
     * @param slotStreamTick per-slot stream ticks (written in-place)
     * @param maxSlots       number of slots
     */
    private static void deserializeAllSlotStreams(
            CompoundTag tag, @Nullable GooType[] slotStreamType,
            int[] slotStreamRate, long[] slotStreamTick, int maxSlots) {
        for (int i = 0; i < maxSlots; i++) {
            String key = String.valueOf(i);
            if (tag.contains(key)) {
                deserializeSlotStream(i, tag.getCompoundOrEmpty(key), slotStreamType, slotStreamRate, slotStreamTick);
            } else {
                clearSlotStream(i, slotStreamType, slotStreamRate, slotStreamTick);
            }
        }
    }

    /**
     * Restores a single slot's stream type, rate, and tick from NBT.
     *
     * @param slot           the slot index
     * @param slotTag        the compound tag for this slot
     * @param slotStreamType per-slot stream goo types (written in-place)
     * @param slotStreamRate per-slot stream rates (written in-place)
     * @param slotStreamTick per-slot stream ticks (written in-place)
     */
    @SuppressWarnings("PMD.UseVarargs") // mutable out-params, not varargs
    private static void deserializeSlotStream(
            int slot, CompoundTag slotTag, @Nullable GooType[] slotStreamType,
            int[] slotStreamRate, long[] slotStreamTick) {
        GooType[] types = GooType.values();
        int ordinal = slotTag.getIntOr(TAG_TYPE, INVALID_ORDINAL);
        slotStreamType[slot] = ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
        slotStreamRate[slot] = slotTag.getIntOr(TAG_RATE, 0);
        slotStreamTick[slot] = slotTag.getLongOr(TAG_TICK, 0);
    }

    /**
     * Clears stream state for all slots.
     *
     * @param slotStreamType per-slot stream goo types (written in-place)
     * @param slotStreamRate per-slot stream rates (written in-place)
     * @param slotStreamTick per-slot stream ticks (written in-place)
     * @param maxSlots       number of slots
     */
    static void clearAllSlotStreams(
            @Nullable GooType[] slotStreamType, int[] slotStreamRate,
            long[] slotStreamTick, int maxSlots) {
        for (int i = 0; i < maxSlots; i++) {
            clearSlotStream(i, slotStreamType, slotStreamRate, slotStreamTick);
        }
    }

    /**
     * Clears stream state for a single slot.
     *
     * @param slot           the slot index
     * @param slotStreamType per-slot stream goo types (written in-place)
     * @param slotStreamRate per-slot stream rates (written in-place)
     * @param slotStreamTick per-slot stream ticks (written in-place)
     */
    @SuppressWarnings("PMD.UseVarargs") // mutable out-params, not varargs
    static void clearSlotStream(
            int slot, @Nullable GooType[] slotStreamType,
            int[] slotStreamRate, long[] slotStreamTick) {
        slotStreamType[slot] = null;
        slotStreamRate[slot] = 0;
        slotStreamTick[slot] = 0;
    }

    /**
     * Restores the 3x3 canister grid from the serialized list, padding with EMPTY.
     *
     * @param input     the value input to read from
     * @param canisters the canister list to populate (written in-place)
     * @param maxSlots  number of slots
     */
    static void loadCanisterList(ValueInput input, List<ItemStack> canisters, int maxSlots) {
        input.read(TAG_CANISTERS, ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < maxSlots; i++) {
                canisters.set(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
            }
        });
    }

    /**
     * Saves the canister grid to persistent storage.
     *
     * @param output    the value output to write to
     * @param canisters the canister list to serialize
     */
    static void saveCanisterList(ValueOutput output, List<ItemStack> canisters) {
        output.store(TAG_CANISTERS, ItemStack.OPTIONAL_CODEC.listOf(), canisters.stream().toList());
    }
}
