package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Static helpers for dropping crucible internals (PMI, fuel rod,
 * reservoir blobs, gasket) when the block is broken.
 */
final class CrucibleDrops {

    private CrucibleDrops() { }

    /** Drops a gasket item if one is installed on the crucible.
     *
     * @param state the block state
     * @param level the current level
     * @param pos   the block position
     */
    static void dropGasket(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(CrucibleBlock.HAS_GASKET)) {
            Block.popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        }
    }

    /** Drops all crucible internal state as items when the block is broken.
     *
     * @param level the current level
     * @param pos   the block position
     */
    static void dropCrucibleContents(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return; }

        dropMeltingItem(crucible, level, pos);
        dropFuelRod(crucible, level, pos);
        dropReservoirAsBlobs(crucible, level, pos);
    }

    /** Drops the PMI with its remaining goo if present.
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private static void dropMeltingItem(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        ItemStack pmi = crucible.getMeltingItem();
        if (!pmi.isEmpty()) {
            Block.popResource(level, pos, pmi);
        }
    }

    /** Drops the depleted fuel rod if present.
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private static void dropFuelRod(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        ItemStack rod = crucible.getFuelRod();
        if (!rod.isEmpty()) {
            Block.popResource(level, pos, rod);
        }
    }

    /** Drops reservoir contents as one item per goo type (blob stack or omniblob).
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private static void dropReservoirAsBlobs(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        BlobStacks.dropAll(crucible.getReservoir(), level, pos);
    }
}
