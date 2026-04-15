package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.GlowCrystalBlock.CrystalSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Glow goo world effect: places a chain marker that detonates into
 * a permanent glow crystal light source. Stacking increases the
 * crystal size and light level. Throwing at an existing crystal
 * grows it in-place without a fuse.
 */
final class GlowEffect implements WorldEffect {

    /** Block update flags for setBlock calls. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof GlowCrystalBlock) {
            growCrystal(level, pos, state);
            return;
        }
        EffectBlockPlacement.glowCrystal(level, pos, targetFace);
    }

    /**
     * Increases the crystal size by one step if not already at max.
     *
     * @param level the current level
     * @param pos   the crystal block position
     * @param state the current block state
     */
    private void growCrystal(Level level, BlockPos pos, BlockState state) {
        CrystalSize current = state.getValue(GlowCrystalBlock.SIZE);
        if (current == CrystalSize.LARGE) { return; }
        CrystalSize next = CrystalSize.values()[current.ordinal() + 1];
        level.setBlock(pos, state.setValue(GlowCrystalBlock.SIZE, next), BLOCK_UPDATE_FLAGS);
    }
}
