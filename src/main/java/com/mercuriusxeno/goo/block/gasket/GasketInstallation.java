package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.UUID;

/**
 * Utility for gasket lifecycle operations: popping gaskets as item drops
 * and clearing their registry state. Used when copper fittings displace
 * an installed gasket (mutual exclusivity).
 */
public final class GasketInstallation {

    private GasketInstallation() {
    }

    /**
     * Pops a gasket as an item drop at the given position, unlinks it from
     * any partner, and removes its registry location. No-op if gasketId is null.
     *
     * @param level    the current level
     * @param pos      the block position
     * @param gasketId the gasket UUID
     */
    public static void popGasket(Level level, BlockPos pos, UUID gasketId) {
        if (gasketId == null) {
            return;
        }
        Block.popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        if (level instanceof ServerLevel serverLevel) {
            GasketRegistry registry = GasketRegistry.get(serverLevel);
            registry.unlink(gasketId);
            registry.updateLocation(gasketId, null);
        }
    }
}
