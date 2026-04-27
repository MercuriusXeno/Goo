package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.ChainBehavior;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock.CrystalShape;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock.CrystalSize;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Glow goo: places a permanent glow crystal light source. Stitched merge
 * of the prior GlowEffect (instant world hit, with grow-existing-crystal
 * logic) and GlowBehavior (chain marker fuse). Implements both
 * WorldEffect (instant blob impact) and ChainBehavior (fused detonation).
 */
public final class GlowBehavior implements WorldEffect, ChainBehavior {

    /**
     * Block update flags for setBlock calls.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    // --- WorldEffect (instant blob hit) ---

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

    // --- ChainBehavior (fused chain marker detonation) ---

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        CrystalSize size = CrystalSize.fromStacks(be.getStackCount());
        CrystalShape shape = be.isFlatBlob() ? CrystalShape.FLAT : CrystalShape.BUMP;
        Direction facing = be.getPlacedFace();

        BlockState crystal = GooBlocks.GLOW_CRYSTAL.get().defaultBlockState()
                .setValue(GlowCrystalBlock.FACING, facing)
                .setValue(GlowCrystalBlock.SHAPE, shape)
                .setValue(GlowCrystalBlock.SIZE, size);
        level.setBlock(pos, crystal, BLOCK_UPDATE_FLAGS);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        // Instant: never ticks.
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        // No state.
    }

    @Override
    public void loadAdditional(ValueInput input) {
        // No state.
    }
}
