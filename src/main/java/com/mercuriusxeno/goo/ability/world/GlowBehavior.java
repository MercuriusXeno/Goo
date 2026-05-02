package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock.CrystalSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Glow world effect: on initial blob impact, either grows an existing
 * glow crystal one size step or places (or stacks) a chain marker via
 * {@link EffectBlockPlacement}. Post-fuse behavior is owned by the
 * data-driven {@link com.mercuriusxeno.goo.ability.BlockPlaceBehavior}
 * pipeline (with the {@code glow_crystal} placer); see the
 * {@code glow_crystal} ability JSON for the wiring.
 */
public final class GlowBehavior implements WorldEffect {

    /** Block update flags: notify neighbors + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
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
    private static void growCrystal(Level level, BlockPos pos, BlockState state) {
        CrystalSize current = state.getValue(GlowCrystalBlock.SIZE);
        if (current == CrystalSize.LARGE) {
            return;
        }
        CrystalSize next = CrystalSize.values()[current.ordinal() + 1];
        level.setBlock(pos, state.setValue(GlowCrystalBlock.SIZE, next), BLOCK_UPDATE_FLAGS);
    }
}
