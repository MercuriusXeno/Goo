package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Redistributes goo across a connected vat stack so fluid settles to the
 * bottom-most vats first, like a real contiguous tank. Called whenever fluid
 * changes or stack topology changes on the server side.
 */
public final class VatStackRedistributor {

    private VatStackRedistributor() {}

    /**
     * Collects all fluid in the stack containing {@code pos}, then fills
     * vats bottom-up, each to its own capacity before overflowing to the
     * next. Uses {@link GooFluidHandler#loadFrom} which suppresses change
     * callbacks to avoid re-entrance during bulk mutation.
     *
     * @param level the server level
     * @param pos   any vat position in the stack
     */
    public static void redistribute(Level level, BlockPos pos) {
        List<VatBlockEntity> stack = collectStack(level, pos);
        if (stack.size() <= 1 || anyRedistributing(stack)) { return; }
        setRedistributing(stack, true);
        try {
            doRedistribute(stack);
        } finally {
            setRedistributing(stack, false);
        }
    }

    /** Returns true if any vat in the stack is already mid-redistribution.
     *
     * @param stack the vat stack to check
     * @return true if any vat has the redistributing flag set
     */
    private static boolean anyRedistributing(List<VatBlockEntity> stack) {
        for (VatBlockEntity vat : stack) {
            if (vat.redistributing) { return true; }
        }
        return false;
    }

    /** Sets the redistributing guard flag on every vat in the stack.
     *
     * @param stack the vat stack
     * @param value the flag value to set
     */
    private static void setRedistributing(List<VatBlockEntity> stack, boolean value) {
        for (VatBlockEntity vat : stack) {
            vat.redistributing = value;
        }
    }

    /** Sums all goo, then fills vats bottom-up greedily.
     *
     * @param stack the vat stack, bottom-to-top
     */
    private static void doRedistribute(List<VatBlockEntity> stack) {
        Map<GooType, Long> pool = mergePool(stack);
        for (VatBlockEntity vat : stack) {
            applySlice(vat, pool);
        }
    }

    /** Merges all goo from every vat in the stack into a single mutable pool.
     *
     * @param stack the vat stack to merge
     * @return the merged goo pool
     */
    private static Map<GooType, Long> mergePool(List<VatBlockEntity> stack) {
        Map<GooType, Long> pool = new EnumMap<>(GooType.class);
        for (VatBlockEntity vat : stack) {
            for (Map.Entry<GooType, Long> e : vat.getContents().contents().entrySet()) {
                pool.merge(e.getKey(), e.getValue(), Long::sum);
            }
        }
        return pool;
    }

    /** Takes a capacity-sized slice from the pool and applies it to the vat if changed.
     *
     * @param vat  the target vat
     * @param pool the mutable goo pool to take from
     */
    private static void applySlice(VatBlockEntity vat, Map<GooType, Long> pool) {
        GooContents slice = takeSlice(pool, vat.getCapacity());
        if (!slice.equals(vat.getContents())) {
            vat.getFluidHandler().loadFrom(slice);
            vat.syncAfterRedistribution();
        }
    }

    /**
     * Takes up to {@code capacity} total mB from the pool, removing consumed
     * volume. Returns an immutable {@link GooContents} for one vat.
     *
     * @param pool     the mutable goo pool
     * @param capacity the capacity in mB
     * @return the goo contents
     */
    private static GooContents takeSlice(Map<GooType, Long> pool, long capacity) {
        if (pool.isEmpty() || capacity <= 0) { return GooContents.EMPTY; }
        Map<GooType, Long> slice = new EnumMap<>(GooType.class);
        long remaining = capacity;
        var it = pool.entrySet().iterator();
        while (it.hasNext() && remaining > 0) {
            remaining -= takeEntry(it, it.next(), remaining, slice);
        }
        return new GooContents(slice);
    }

    /** Consumes up to {@code remaining} mB from one pool entry, adds to slice, and returns amount taken.
     *
     * @param it        the pool iterator (for removal)
     * @param entry     the current pool entry
     * @param remaining the remaining capacity in mB
     * @param slice     the slice being built
     * @return the amount of mB taken
     */
    private static long takeEntry(Iterator<Map.Entry<GooType, Long>> it,
            Map.Entry<GooType, Long> entry, long remaining, Map<GooType, Long> slice) {
        long take = Math.min(entry.getValue(), remaining);
        slice.put(entry.getKey(), take);
        if (take >= entry.getValue()) {
            it.remove();
        } else {
            entry.setValue(entry.getValue() - take);
        }
        return take;
    }

    /**
     * Walks the stack vertically from {@code pos} to collect all connected
     * {@link VatBlockEntity} instances, returned bottom-to-top.
     *
     * @param level the current level
     * @param pos   the block position
     * @return the list
     */
    private static List<VatBlockEntity> collectStack(Level level, BlockPos pos) {
        BlockPos bottom = findStackBottom(level, pos);
        return collectUpward(level, bottom);
    }

    /** Walks upward from the bottom position, collecting VatBlockEntity instances.
     *
     * @param level  the current level
     * @param bottom the bottom-most vat position
     * @return the vat list, bottom-to-top
     */
    private static List<VatBlockEntity> collectUpward(Level level, BlockPos bottom) {
        List<VatBlockEntity> stack = new ArrayList<>();
        BlockPos cursor = bottom;
        while (level.getBlockEntity(cursor) instanceof VatBlockEntity vat) {
            stack.add(vat);
            if (!(level.getBlockState(cursor.above()).getBlock() instanceof VatBlock)) { break; }
            cursor = cursor.above();
        }
        return stack;
    }

    /** Walks downward from the given position to find the lowest vat in the stack.
     *
     * @param level the current level
     * @param pos   any position in the stack
     * @return the bottom-most vat position
     */
    private static BlockPos findStackBottom(Level level, BlockPos pos) {
        BlockPos bottom = pos;
        while (level.getBlockState(bottom.below()).getBlock() instanceof VatBlock) {
            bottom = bottom.below();
        }
        return bottom;
    }
}
