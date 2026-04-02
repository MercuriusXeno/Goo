package com.mercuriusxeno.goo.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import org.jspecify.annotations.NonNull;

/**
 * Non-flowing goo fluid variants. Goo sits in place at the level it was
 * placed and never spreads, decays, or changes state on its own.
 * Overriding tick() is sufficient because spread() is called from tick().
 */
public final class GooFluid {

    private GooFluid() {}

    /**
     * Source (full-block) goo fluid. Overrides tick to prevent
     * any autonomous state changes or spreading.
     */
    public static class Source extends BaseFlowingFluid.Source {

        /** Creates a non-flowing source fluid with the given properties. */
        public Source(Properties properties) {
            super(properties);
        }

        /** No-op: goo does not tick, spread, or decay. */
        @Override
        public void tick(@NonNull ServerLevel level, @NonNull BlockPos pos,
                         @NonNull BlockState blockState, @NonNull FluidState fluidState) {
            // intentionally empty - goo stays where placed
        }
    }

    /**
     * Flowing (partial-level) goo fluid. Overrides tick to prevent
     * any autonomous state changes. Used for levels 1-7.
     */
    public static class Flowing extends BaseFlowingFluid.Flowing {

        /** Creates a non-flowing flowing-variant fluid with the given properties. */
        public Flowing(Properties properties) {
            super(properties);
        }

        /** No-op: goo does not tick, spread, or decay. */
        @Override
        public void tick(@NonNull ServerLevel level, @NonNull BlockPos pos,
                         @NonNull BlockState blockState, @NonNull FluidState fluidState) {
            // intentionally empty - goo stays where placed
        }
    }
}
