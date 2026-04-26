package com.mercuriusxeno.goo.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Shared sync utility for goo block entities. Marks dirty for chunk saving
 * and sends an update packet to tracking clients.
 */
public final class BlockEntitySync {

    /** Block update flags: notify neighbors + send to clients. */
    public static final int BLOCK_UPDATE_FLAGS = 3;

    private BlockEntitySync() {}

    /**
     * Marks the block entity dirty and sends a block update to tracking clients.
     *
     * @param be the block entity to sync
     */
    public static void markDirtyAndSync(BlockEntity be) {
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(
                be.getBlockPos(), be.getBlockState(), be.getBlockState(), BLOCK_UPDATE_FLAGS);
        }
    }

    /**
     * Invalidates cached capabilities so listeners re-query. Server-side only.
     *
     * @param be the block entity whose capabilities to invalidate
     */
    public static void invalidateCapabilities(BlockEntity be) {
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(be.getBlockPos());
        }
    }

    /**
     * Pushes an integer value into a blockstate property. Server-side only.
     *
     * @param be       the block entity owning the blockstate
     * @param property the integer property to update
     * @param value    the new value
     */
    public static void syncIntProperty(BlockEntity be, IntegerProperty property, int value) {
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.setBlock(be.getBlockPos(),
                be.getBlockState().setValue(property, value), BLOCK_UPDATE_FLAGS);
        }
    }
}
