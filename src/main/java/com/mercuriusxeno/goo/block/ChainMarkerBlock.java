package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
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
 * Short-lived fuse block placed by chain world effects (Blaze, Frost,
 * Nether, Rock). No collision, no selection shape - purely visual.
 * The block entity ticks the fuse and fires the executor on expiry.
 */
public class ChainMarkerBlock extends BaseEntityBlock {

    public static final MapCodec<ChainMarkerBlock> CODEC = simpleCodec(ChainMarkerBlock::new);

    /** Outline shape: small centered cube so the block is barely selectable. */
    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 11, 11);

    public ChainMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Rendered entirely by the BER - no block model. */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** No collision - energy-type effects don't impede movement. */
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
        return new ChainMarkerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state,
            @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, GooBlockEntities.CHAIN_MARKER.get(),
                ChainMarkerBlockEntity::serverTick);
    }

    /** Spawns ambient particles based on the chain marker's goo type. */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
            @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) return;
        spawnAmbientParticles(be.getGooType(), be.getStackCount(),
                pos, level, random);
    }

    /** Emits goo-type-specific ambient particles scaled by stack count. */
    private static void spawnAmbientParticles(GooType type, int stacks,
            BlockPos pos, Level level, RandomSource random) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double spread = 0.25 + 0.1 * stacks;

        if (type == GooType.BLAZE) {
            for (int i = 0; i < 2 + stacks; i++) {
                double ox = (random.nextDouble() - 0.5) * spread * 2;
                double oy = (random.nextDouble() - 0.5) * spread * 2;
                double oz = (random.nextDouble() - 0.5) * spread * 2;
                level.addParticle(ParticleTypes.FLAME, cx + ox, cy + oy, cz + oz,
                        0, 0.02, 0);
            }
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.LAVA, cx, cy, cz, 0, 0, 0);
            }
        }
        // Future: frost snowflakes, nether soul particles, rock dust, etc.
    }
}
