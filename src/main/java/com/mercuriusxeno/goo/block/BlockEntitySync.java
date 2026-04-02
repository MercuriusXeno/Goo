package com.mercuriusxeno.goo.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Shared sync utility for goo block entities. Marks dirty for chunk saving
 * and sends an update packet to tracking clients.
 */
public final class BlockEntitySync {

    private BlockEntitySync() {}

    /** Marks the block entity dirty and sends a block update to tracking clients. */
    public static void markDirtyAndSync(BlockEntity be) {
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(
                be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
        }
    }

    /** Invalidates cached capabilities so listeners re-query. Server-side only. */
    public static void invalidateCapabilities(BlockEntity be) {
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(be.getBlockPos());
        }
    }

    /** Pushes an integer value into a blockstate property. Server-side only. */
    public static void syncIntProperty(BlockEntity be, IntegerProperty property, int value) {
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.setBlock(be.getBlockPos(),
                be.getBlockState().setValue(property, value), 3);
        }
    }
}
