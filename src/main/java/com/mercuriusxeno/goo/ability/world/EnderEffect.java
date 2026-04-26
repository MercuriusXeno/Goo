package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Ender goo: places an end rod as a teleporter marker. */
final class EnderEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        BlockPos nodePos = pos.above();
        if (level.getBlockState(nodePos).isAir()) {
            level.setBlock(nodePos, Blocks.END_ROD.defaultBlockState(), Block.UPDATE_ALL);
        }
        // TODO: Full teleporter node system (look + shift to warp)
    }
}
