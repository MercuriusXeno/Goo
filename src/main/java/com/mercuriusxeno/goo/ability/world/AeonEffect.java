package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Aeon goo: places obsidian as a blast-resistant barrier. */
final class AeonEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        BlockPos barPos = pos.above();
        if (level.getBlockState(barPos).isAir()) {
            level.setBlock(barPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
        // TODO: Custom aeon barrier block with blast resistance
    }
}
