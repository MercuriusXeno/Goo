package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * Shared scaffolding for invisible, non-colliding, BE-backed effect blocks
 * like {@link ChainMarkerBlock}. Subclasses
 * supply their {@code codec()}, {@code newBlockEntity()}, {@code getTicker()}
 * and {@code animateTick()} overrides; this base covers the uniform render
 * shape, empty collision, and small selection outline.
 */
public abstract class AbstractEffectBlock extends BaseEntityBlock {

    /**
     * Small centered cube so the block is barely selectable.
     */
    protected static final VoxelShape SELECTION_SHAPE = box(5, 5, 5, 11, 11, 11);

    /**
     * Block center offset (0.5 blocks). Used by effect-block particle dispatch.
     */
    protected static final double BLOCK_CENTER = 0.5;

    /**
     * Spread diameter multiplier applied to random particle offset ranges.
     */
    protected static final double SPREAD_DIAMETER = 2;

    /**
     * Base constructor. Subclasses forward their own {@link Properties}.
     *
     * @param properties the block properties
     */
    protected AbstractEffectBlock(Properties properties) {
        super(properties);
    }

    /**
     * Effect blocks are rendered entirely by particles or a BER, never by a model.
     *
     * @param state the block state
     * @return {@link RenderShape#INVISIBLE}
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * Effect blocks never impede movement.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return an empty voxel shape
     */
    @Override
    protected @NonNull VoxelShape getCollisionShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Small outline for selection/targeting, shared across all effect blocks.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return a small centered cube voxel shape
     */
    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SELECTION_SHAPE;
    }
}
