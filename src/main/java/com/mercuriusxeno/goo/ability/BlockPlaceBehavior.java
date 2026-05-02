package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Instant {@link ChainBehavior} that places a single block at the
 * marker position when the fuse expires. The per-type state
 * derivation lives on the resolved {@link BlockPlacer} delegate.
 *
 * <p>Pipeline: on fuse expiry the configured placer paints the block;
 * {@link #isActive()} stays {@code false} so the chain marker BE
 * removes itself the next tick.</p>
 */
public final class BlockPlaceBehavior implements ChainBehavior {

    private static final String PARAM_PLACER = "placer";
    private static final String DEFAULT_PLACER = BlockPlacerType.GLOW_CRYSTAL;

    private final BlockPlacer placer;

    /**
     * Creates a block-place behavior with a resolved placer.
     *
     * @param placer the placer to invoke on fuse expiry
     */
    public BlockPlaceBehavior(BlockPlacer placer) {
        this.placer = placer;
    }

    /**
     * Factory method for {@link BehaviorType} registration.
     *
     * @param entry the behavior entry with params
     * @param def   the parent ability definition
     * @return a new BlockPlaceBehavior bound to the resolved placer
     */
    public static ChainBehavior fromEntry(BehaviorEntry entry, AbilityDefinition def) {
        String placerName = entry.params().getOrDefault(PARAM_PLACER, DEFAULT_PLACER);
        return new BlockPlaceBehavior(BlockPlacerType.byName(placerName));
    }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        placer.place(level, pos, be);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
    }

    @Override
    public void loadAdditional(ValueInput input) {
    }
}
