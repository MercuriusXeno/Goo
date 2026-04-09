package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Glow goo: places a glowstone block as a permanent light source. */
final class GlowEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        BlockPos lightPos = pos.above();
        if (level.getBlockState(lightPos).isAir()) {
            level.setBlock(lightPos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        } else if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
