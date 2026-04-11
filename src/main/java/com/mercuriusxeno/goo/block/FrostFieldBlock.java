package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Invisible field block that persists after frost goo freezes an area.
 * Prevents frozen blocks from melting (via {@link MagickedIceBlock}, a
 * non-melting mod ice variant) for a duration, then converts magicked ice
 * back to vanilla ice on expiry and removes itself.
 */
public class FrostFieldBlock extends AbstractEffectBlock {

    public static final MapCodec<FrostFieldBlock> CODEC = simpleCodec(FrostFieldBlock::new);

    /** Spread multiplier for radius-based particle range. */
    private static final double SPREAD_FACTOR = 0.5;
    /** Base particle count for snowflake effects. */
    private static final int BASE_PARTICLE_COUNT = 2;
    /** Downward velocity for snowflake particles. */
    private static final double SNOWFLAKE_FALL_SPEED = -0.02;

    /** Creates a frost field block with the given properties.
     *
     * @param properties the block properties
     */
    public FrostFieldBlock(Properties properties) {
        super(properties);
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Creates the frost field block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new FrostFieldBlockEntity(pos, state);
    }

    /** Registers the server-side duration tick dispatcher.
     *
     * @param level the current level
     * @param state the block state
     * @param type  the goo type
     * @return the ticker
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state,
            @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) { return null; }
        return createTickerHelper(type, GooBlockEntities.FROST_FIELD.get(),
                FrostFieldBlockEntity::serverTick);
    }

    /** Spawns snowflake particles around the field center.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
            @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof FrostFieldBlockEntity be)) { return; }
        spawnSnowflakes(level, pos, random, be);
    }

    /** Spawns snowflake particles scattered around the block center.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param random the random source
     * @param be     the frost field block entity
     */
    private void spawnSnowflakes(Level level, BlockPos pos, RandomSource random,
                                  FrostFieldBlockEntity be) {
        double spread = be.getRadius() * SPREAD_FACTOR * SPREAD_DIAMETER;
        for (int i = 0; i < BASE_PARTICLE_COUNT + be.getStacks(); i++) {
            double ox = (random.nextDouble() - BLOCK_CENTER) * spread;
            double oy = (random.nextDouble() - BLOCK_CENTER) * spread;
            double oz = (random.nextDouble() - BLOCK_CENTER) * spread;
            level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + BLOCK_CENTER + ox, pos.getY() + BLOCK_CENTER + oy, pos.getZ() + BLOCK_CENTER + oz, 0, SNOWFLAKE_FALL_SPEED, 0);
        }
    }
}
