package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Invisible field block that persists after frost goo freezes an area.
 * Prevents frozen blocks from melting (via packed ice) for a duration,
 * then converts packed ice back to regular ice on expiry and removes itself.
 */
public class FrostFieldBlock extends BaseEntityBlock {

    public static final MapCodec<FrostFieldBlock> CODEC = simpleCodec(FrostFieldBlock::new);

    /** Small centered cube so the block is barely selectable. */
    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 11, 11);

    public FrostFieldBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Fully invisible -- no block model, no BER needed. */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** No collision -- players walk through the field. */
    @Override
    protected @NonNull VoxelShape getCollisionShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }

    /** Small outline for selection/targeting. */
    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new FrostFieldBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state,
            @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, GooBlockEntities.FROST_FIELD.get(),
                FrostFieldBlockEntity::serverTick);
    }

    /** Spawns snowflake particles around the field center. */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
            @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof FrostFieldBlockEntity be)) return;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double spread = be.getRadius() * 0.5;
        for (int i = 0; i < 2 + be.getStacks(); i++) {
            double ox = (random.nextDouble() - 0.5) * spread * 2;
            double oy = (random.nextDouble() - 0.5) * spread * 2;
            double oz = (random.nextDouble() - 0.5) * spread * 2;
            level.addParticle(ParticleTypes.SNOWFLAKE,
                    cx + ox, cy + oy, cz + oz, 0, -0.02, 0);
        }
    }
}
