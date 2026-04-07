package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unified immutable data component for multi-type goo volume storage.
 * Replaces both BucketContents and CanisterContents for all goo containers:
 * buckets, canisters, vats, crucible reservoirs, and partially melted items.
 *
 * <p>Each entry maps a GooType to a volume in microblobs (mB).
 * Mutation methods return new instances; this record is never modified in place.</p>
 *
 * @param contents the map of goo types to volumes in microblobs
 */
public record GooContents(Map<GooType, Long> contents) implements TooltipProvider {

    /** Empty container with no goo. */
    public static final GooContents EMPTY = new GooContents(Map.of());

    /** Persistent codec: serializes goo type keys via StringRepresentable. */
    public static final Codec<GooContents> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(
                StringRepresentable.fromValues(GooType::values),
                Codec.LONG
            ).fieldOf("contents").forGetter(GooContents::contents)
        ).apply(instance, GooContents::new)
    );

    /** Network codec: writes entry count, then ordinal + volume per entry. */
    public static final StreamCodec<ByteBuf, GooContents> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public GooContents decode(ByteBuf buf) {
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                return new GooContents(readEntries(buf, count));
            }

            @Override
            public void encode(ByteBuf buf, GooContents value) {
                ByteBufCodecs.VAR_INT.encode(buf, value.contents.size());
                for (Map.Entry<GooType, Long> entry : value.contents.entrySet()) {
                    ByteBufCodecs.VAR_INT.encode(buf, entry.getKey().ordinal());
                    ByteBufCodecs.VAR_LONG.encode(buf, entry.getValue());
                }
            }
        };

    /** Reads ordinal-volume pairs from a byte buffer into an EnumMap.
     *
     * @param buf   the byte buffer
     * @param count the number of entries to read
     * @return the decoded map
     */
    private static Map<GooType, Long> readEntries(ByteBuf buf, int count) {
        Map<GooType, Long> map = new EnumMap<>(GooType.class);
        for (int i = 0; i < count; i++) {
            int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
            long volume = ByteBufCodecs.VAR_LONG.decode(buf);
            if (ordinal >= 0 && ordinal < GooType.values().length) { map.put(GooType.values()[ordinal], volume); }
        }
        return map;
    }

    /** Defensive copy constructor: filters non-positive values, wraps in unmodifiable EnumMap. */
    public GooContents {
        contents = filterPositive(contents);
    }

    /** Retains only entries with positive volume, wrapping the result as unmodifiable.
     *
     * @param input the raw contents map
     * @return filtered, unmodifiable map (or Map.of() if empty)
     */
    private static Map<GooType, Long> filterPositive(Map<GooType, Long> input) {
        if (input.isEmpty()) { return Map.of(); }
        Map<GooType, Long> filtered = new EnumMap<>(GooType.class);
        input.forEach((type, vol) -> { if (vol > 0) { filtered.put(type, vol); } });
        return filtered.isEmpty() ? Map.of() : Collections.unmodifiableMap(filtered);
    }

    /**
     * Returns true if no goo of any type is stored.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /**
     * Returns the total volume across all goo types.
     *
     * @return total volume in microblobs
     */
    public long totalVolume() {
        long total = 0;
        for (long v : contents.values()) {
            total += v;
        }
        return total;
    }

    /**
     * Returns how many distinct goo types are present.
     *
     * @return the number of goo types stored
     */
    public int typeCount() {
        return contents.size();
    }

    /**
     * Returns true if exactly one goo type is present.
     *
     * @return true if exactly one type is stored
     */
    public boolean isSingleType() {
        return contents.size() == 1;
    }

    /**
     * Returns the single goo type if exactly one is present, or null otherwise.
     *
     * @return the single goo type, or null
     */
    @Nullable
    public GooType getSingleType() {
        if (contents.size() != 1) { return null; }
        return contents.keySet().iterator().next();
    }

    /**
     * Returns the goo type with the highest volume, or null if empty. Ties break by enum ordinal.
     *
     * @return the dominant goo type, or null if empty
     */
    @Nullable
    public GooType largestType() {
        GooType largest = null;
        long highest = 0;
        for (var e : contents.entrySet()) {
            if (beatsCurrentLeader(e.getValue(), e.getKey(), highest, largest)) {
                highest = e.getValue();
                largest = e.getKey();
            }
        }
        return largest;
    }

    /** Returns true if the candidate volume/type beats the current leader.
     *
     * @param vol       the candidate volume
     * @param candidate the candidate goo type
     * @param highest   the current highest volume
     * @param leader    the current leader type
     * @return true if the candidate should replace the leader
     */
    private static boolean beatsCurrentLeader(long vol, GooType candidate,
                                               long highest, GooType leader) {
        return vol > highest || (vol == highest && winsOrdinalTie(candidate, leader));
    }

    /**
     * Returns true if the candidate wins a tie against the current leader by ordinal.
     *
     * @param candidate the challenger goo type
     * @param current   the current leader, or null
     * @return true if the candidate should replace the leader
     */
    private static boolean winsOrdinalTie(GooType candidate, GooType current) {
        return current == null || candidate.ordinal() < current.ordinal();
    }

    /**
     * Returns the volume of a specific goo type, or 0 if absent.
     *
     * @param type the goo type to query
     * @return volume in microblobs
     */
    public long getVolume(GooType type) {
        return contents.getOrDefault(type, 0L);
    }

    /**
     * Returns an unmodifiable view of all contents.
     *
     * @return map of goo type to volume in microblobs
     */
    public Map<GooType, Long> getAll() {
        return contents;
    }

    /**
     * Returns a new GooContents with the given volume added to the specified type.
     *
     * @param type   the goo type to add to
     * @param amount the volume to add in microblobs
     * @return new contents with the addition applied
     */
    public GooContents withAdded(GooType type, long amount) {
        if (amount <= 0) { return this; }
        Map<GooType, Long> newMap = new EnumMap<>(GooType.class);
        newMap.putAll(contents);
        newMap.merge(type, amount, Long::sum);
        return new GooContents(newMap);
    }

    /**
     * Returns a new GooContents with all entries from the other contents merged in.
     * Each type's volume is summed.
     *
     * @param other the contents to merge in
     * @return new contents with both sets combined
     */
    public GooContents mergeWith(GooContents other) {
        if (other.isEmpty()) { return this; }
        if (this.isEmpty()) { return other; }
        GooContents result = this;
        for (Map.Entry<GooType, Long> entry : other.contents.entrySet()) {
            result = result.withAdded(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Returns a new GooContents with the given volume removed from the specified type.
     *
     * @param type   the goo type to remove from
     * @param amount the volume to remove in microblobs
     * @return new contents with the removal applied
     */
    public GooContents withRemoved(GooType type, long amount) {
        if (amount <= 0 || !contents.containsKey(type)) { return this; }
        return new GooContents(removeFromMap(type, amount));
    }

    /** Creates a copy of the contents map with the given volume removed from one type.
     *
     * @param type   the goo type to reduce
     * @param amount the volume to subtract
     * @return the new map (may have the type removed entirely if depleted)
     */
    private Map<GooType, Long> removeFromMap(GooType type, long amount) {
        Map<GooType, Long> newMap = new EnumMap<>(GooType.class);
        newMap.putAll(contents);
        long remaining = newMap.getOrDefault(type, 0L) - amount;
        if (remaining <= 0) { newMap.remove(type); }
        else { newMap.put(type, remaining); }
        return newMap;
    }

    /**
     * Adds up to the remaining capacity of goo, returning new contents.
     * The caller provides the total capacity externally; this method only
     * adds what fits, capping at that capacity.
     *
     * @param type     the goo type to add
     * @param amount   the amount requested to add
     * @param capacity the total capacity of the container
     * @return new contents with the capped addition
     */
    public GooContents withCappedAdd(GooType type, long amount, long capacity) {
        if (amount <= 0) { return this; }
        long space = capacity - totalVolume();
        if (space <= 0) { return this; }
        long accepted = Math.min(amount, space);
        return withAdded(type, accepted);
    }

    /**
     * Returns how much of the requested amount was actually accepted by
     * {@link #withCappedAdd}. Useful for callers that need to know the delta.
     *
     * @param amount   the amount requested to add
     * @param capacity the total capacity of the container
     * @return the amount that would be accepted (0 if full)
     */
    public long cappedAddAmount(long amount, long capacity) {
        if (amount <= 0) { return 0; }
        long space = capacity - totalVolume();
        if (space <= 0) { return 0; }
        return Math.min(amount, space);
    }

    /**
     * No-op: icon tooltips are handled by GooTooltipHandler for all container types.
     *
     * @param context          the tooltip context
     * @param tooltip          the tooltip line consumer
     * @param flag             the tooltip flag (normal or advanced)
     * @param componentGetter  the component getter
     */
    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip,
            TooltipFlag flag, DataComponentGetter componentGetter) {
        // Intentionally empty: GooTooltipHandler renders icon+volume lines
    }
}
