package com.mercuriusxeno.goo.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A non-melting mod variant of vanilla {@link IceBlock}, used internally by
 * the frost chain effect. Visually, audibly, and mechanically indistinguishable
 * from {@code minecraft:ice}: same texture, same model, same sound, same
 * friction, same mining behavior, same silk-touch drops (silk touch yields a
 * vanilla {@code minecraft:ice} item via the block properties' loot table
 * override).
 *
 * <p>The only difference is that this block never melts. Placed permanently
 * by the frost cold snap effect. There is no item form; it is unobtainable
 * in its true form. The block id {@code goo:magicked_ice} exists for
 * internal identification and mod interop.</p>
 */
public class MagickedIceBlock extends IceBlock {

    public static final MapCodec<MagickedIceBlock> CODEC = simpleCodec(MagickedIceBlock::new);

    /** Creates a new magicked ice block.
     *
     * @param properties the block properties
     */
    public MagickedIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull MapCodec<? extends IceBlock> codec() {
        return CODEC;
    }

    /**
     * Overrides vanilla ice break behavior: drops the ice item normally
     * instead of converting to water. No silk touch required.
     *
     * @param level         the current level
     * @param player        the player breaking the block
     * @param pos           the block position
     * @param state         the block state
     * @param blockEntity   the block entity, or null
     * @param destroyedWith the tool used
     */
    @Override
    public void playerDestroy(@NonNull Level level, @NonNull Player player,
            @NonNull BlockPos pos, @NonNull BlockState state,
            @Nullable BlockEntity blockEntity, @NonNull ItemStack destroyedWith) {
        dropResources(state, level, pos, blockEntity, player, destroyedWith);
    }

    /**
     * Anti-melt: suppresses the inherited light-driven melt.
     *
     * @param state  the current block state
     * @param level  the server level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    protected void randomTick(@NonNull BlockState state, @NonNull ServerLevel level,
                              @NonNull BlockPos pos, @NonNull RandomSource random) {
        // No-op: magicked ice never melts.
    }

    /**
     * Belt-and-suspenders companion to the {@link #randomTick} override:
     * report the block as non-random-ticking at the scheduler level too.
     * Even though the properties should be built without
     * {@code .randomTicks()}, this guarantees no random ticks are ever
     * requested for this block regardless of how the properties were copied.
     *
     * @param state the current block state
     * @return always false
     */
    @Override
    protected boolean isRandomlyTicking(@NonNull BlockState state) {
        return false;
    }
}
