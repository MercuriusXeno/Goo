package com.mercuriusxeno.goo.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * A non-melting mod variant of vanilla {@link IceBlock}, used internally by
 * the frost chain effect. Visually, audibly, and mechanically indistinguishable
 * from {@code minecraft:ice}: same texture, same model, same sound, same
 * friction, same mining behavior, same silk-touch drops (silk touch yields a
 * vanilla {@code minecraft:ice} item via the block properties' loot table
 * override).
 *
 * <p>The only difference is that this block has no random-tick melt path.
 * {@link FrostFieldBlockEntity} places it during the freeze and reverts it to
 * vanilla ice on expiry, after which normal ice melting resumes.</p>
 *
 * <p>There is no item form for this block; it is unobtainable in its true
 * form. The mod-only block id {@code goo:magicked_ice} exists solely for
 * internal identification by {@link FrostFieldBlockEntity}'s thaw pass and
 * for F3 debug / mod interop.</p>
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
     * Anti-melt override: suppresses the inherited light-driven melt that
     * {@link IceBlock#randomTick} applies. The block remains ice-shaped until
     * {@link FrostFieldBlockEntity} swaps it to vanilla ice on field expiry.
     *
     * @param state  the current block state
     * @param level  the server level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    protected void randomTick(@NonNull BlockState state, @NonNull ServerLevel level,
                              @NonNull BlockPos pos, @NonNull RandomSource random) {
        // No-op: magicked ice does not melt while a frost field protects it.
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
