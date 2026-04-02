package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.EnumMap;
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
        if (stack.size() <= 1) return;

        // Guard: skip if any vat is already redistributing (re-entrance from onFluidChanged)
        for (VatBlockEntity vat : stack) {
            if (vat.redistributing) return;
        }

        // Set re-entrance guard on all vats before mutating
        for (VatBlockEntity vat : stack) {
            vat.redistributing = true;
        }
        try {
            doRedistribute(stack);
        } finally {
            for (VatBlockEntity vat : stack) {
                vat.redistributing = false;
            }
        }
    }

    /** Sums all goo, then fills vats bottom-up greedily. */
    private static void doRedistribute(List<VatBlockEntity> stack) {
        // 1. Merge all fluid into a mutable pool
        EnumMap<GooType, Long> pool = new EnumMap<>(GooType.class);
        for (VatBlockEntity vat : stack) {
            for (Map.Entry<GooType, Long> e : vat.getContents().contents().entrySet()) {
                pool.merge(e.getKey(), e.getValue(), Long::sum);
            }
        }

        // 2. Distribute bottom-up (stack list is already bottom-to-top)
        for (VatBlockEntity vat : stack) {
            long capacity = vat.getCapacity();
            GooContents slice = takeSlice(pool, capacity);
            GooContents current = vat.getContents();
            if (!slice.equals(current)) {
                vat.getFluidHandler().loadFrom(slice);
                vat.syncAfterRedistribution();
            }
        }
    }

    /**
     * Takes up to {@code capacity} total mB from the pool, removing consumed
     * volume. Returns an immutable {@link GooContents} for one vat.
     */
    private static GooContents takeSlice(EnumMap<GooType, Long> pool, long capacity) {
        if (pool.isEmpty() || capacity <= 0) return GooContents.EMPTY;

        EnumMap<GooType, Long> slice = new EnumMap<>(GooType.class);
        long remaining = capacity;

        var it = pool.entrySet().iterator();
        while (it.hasNext() && remaining > 0) {
            Map.Entry<GooType, Long> entry = it.next();
            long take = Math.min(entry.getValue(), remaining);
            slice.put(entry.getKey(), take);
            remaining -= take;
            long left = entry.getValue() - take;
            if (left <= 0) {
                it.remove();
            } else {
                entry.setValue(left);
            }
        }
        return new GooContents(slice);
    }

    /**
     * Walks the stack vertically from {@code pos} to collect all connected
     * {@link VatBlockEntity} instances, returned bottom-to-top.
     */
    private static List<VatBlockEntity> collectStack(Level level, BlockPos pos) {
        // Walk down to bottom
        BlockPos bottom = pos;
        while (level.getBlockState(bottom.below()).getBlock() instanceof VatBlock) {
            bottom = bottom.below();
        }

        // Walk up, collecting block entities
        List<VatBlockEntity> stack = new ArrayList<>();
        BlockPos cursor = bottom;
        while (true) {
            BlockEntity be = level.getBlockEntity(cursor);
            if (be instanceof VatBlockEntity vat) {
                stack.add(vat);
            } else {
                break;
            }
            if (!(level.getBlockState(cursor.above()).getBlock() instanceof VatBlock)) break;
            cursor = cursor.above();
        }
        return stack;
    }
}
