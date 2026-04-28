package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Static helpers for the crucible melting pipeline: per-tick drain,
 * fuel conversion/consumption, ignition spray, boiling effects, and
 * LIT blockstate management. Keeps framework overrides in CrucibleBlockEntity.
 */
final class CrucibleMelting {

    /**
     * Block update flags: notify neighbors + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private CrucibleMelting() {
    }

    /**
     * Instance-level server tick dispatcher.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     */
    static void serverTick(CrucibleBlockEntity be, Level level, BlockPos pos, BlockState state) {
        tickIgnitionSpray(be);
        handleMeltingTick(be, level, pos);
        handleBoilingEffects(be, level, pos);
        be.gasketPusher.tick();

        boolean lit = be.isEnabled() && be.hasFuel();
        if (lit != state.getValue(CrucibleBlock.LIT)) {
            level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), BLOCK_UPDATE_FLAGS);
        }
    }

    /**
     * Per-tick melting: drains goo from the PMI pool into the reservoir.
     * Skips if disabled, no fuel, or no meltable item.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void handleMeltingTick(CrucibleBlockEntity be, Level level, BlockPos pos) {
        if (!be.isEnabled()) {
            return;
        }
        if (!hasMeltableItem(be)) {
            return;
        }
        if (!be.hasFuel()) {
            return;
        }
        processMeltCycle(be, level, pos);
    }

    /**
     * Runs one melt cycle: fuel conversion, drain, effects, fuel consumption, and cleanup.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void processMeltCycle(CrucibleBlockEntity be, Level level, BlockPos pos) {
        convertFreshRodToDepleted(be);
        drainFromPool(be);
        spawnActiveEffects(be, level, pos, true);
        consumeFuelTick(be);
        clearFinishedMeltingItem(be);
        be.syncToClients();
    }

    /**
     * Spawns boiling bubbles and embers whenever the rod is heated and goo is present,
     * regardless of whether there is an item being melted. This lets players enable
     * boiling at will by inserting a fuel rod into goo-filled basins.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void handleBoilingEffects(CrucibleBlockEntity be, Level level, BlockPos pos) {
        if (!be.isEnabled()) {
            return;
        }
        if (!be.hasFuel()) {
            return;
        }
        if (hasMeltableItem(be)) {
            return;
        }
        spawnActiveEffects(be, level, pos, false);
    }

    /**
     * Converts a vanilla blaze rod to a depleted blaze rod on its first burn tick.
     *
     * @param be the crucible block entity
     */
    private static void convertFreshRodToDepleted(CrucibleBlockEntity be) {
        if (be.fuelRod.is(Items.BLAZE_ROD)) {
            be.fuelRod = DepletedBlazeRodItem.createFresh();
            beginIgnitionSpray(be);
        }
    }

    /**
     * Consumes one fuel tick, destroying the rod when fully exhausted.
     *
     * @param be the crucible block entity
     */
    private static void consumeFuelTick(CrucibleBlockEntity be) {
        if (!DepletedBlazeRodItem.consumeTick(be.fuelRod)) {
            be.fuelRod = ItemStack.EMPTY;
        }
    }

    /**
     * Begins a sustained single-spark spray when the blaze rod first contacts the basin.
     *
     * @param be the crucible block entity
     */
    private static void beginIgnitionSpray(CrucibleBlockEntity be) {
        Level level = be.getLevel();
        be.ignitionSprayTicks = CrucibleBlockEntity.IGNITION_BASE_TICKS
                + (level != null ? level.getRandom().nextInt(CrucibleBlockEntity.IGNITION_RANDOM_TICKS) : 0);
    }

    /**
     * Spawns four cardinal sparks per tick while the ignition spray is active.
     *
     * @param be the crucible block entity
     */
    private static void tickIgnitionSpray(CrucibleBlockEntity be) {
        int ticks = be.ignitionSprayTicks;
        if (ticks <= 0) {
            return;
        }
        be.ignitionSprayTicks = ticks - 1;
        Level level = be.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            CrucibleParticleHelper.spawnIgnitionSparks(serverLevel, be.getBlockPos());
        }
    }

    /**
     * Spawns embers and goo bubbles while the crucible is actively processing.
     * Ember frequency scales with whether an item is being melted.
     *
     * @param be      the crucible block entity
     * @param level   the current level
     * @param pos     the block position
     * @param melting true if actively melting an item
     */
    private static void spawnActiveEffects(CrucibleBlockEntity be, Level level, BlockPos pos, boolean melting) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CrucibleParticleHelper.spawnEmbers(serverLevel, pos, level.getRandom(), melting);
        spawnBubblesIfGooPresent(be, serverLevel, pos);
    }

    /**
     * Spawns goo-colored bubbles if there is goo in the reservoir or pool.
     *
     * @param be          the crucible block entity
     * @param serverLevel the server level
     * @param pos         the block position
     */
    private static void spawnBubblesIfGooPresent(CrucibleBlockEntity be, ServerLevel serverLevel, BlockPos pos) {
        int totalGoo = be.reservoir.totalVolume() + be.getPoolVolume();
        if (totalGoo <= 0) {
            return;
        }
        GooType dominant = resolveDominantType(be);
        if (dominant == null) {
            return;
        }
        float surfaceY = CrucibleParticleHelper.computeSurfaceY(totalGoo);
        CrucibleParticleHelper.spawnGooBubbles(
                serverLevel, pos, surfaceY, dominant.getColor(), serverLevel.getRandom(),
                be.bubbleHistory);
    }

    /**
     * Returns the dominant goo type from the reservoir, falling back to the PMI pool.
     *
     * @param be the crucible block entity
     * @return the dominant type, or null if no goo is present
     */
    private static @Nullable GooType resolveDominantType(CrucibleBlockEntity be) {
        GooType dominant = be.reservoir.largestType();
        return dominant != null ? dominant : dominantPoolType(be);
    }

    /**
     * Returns the largest goo type in the PMI pool, or null if empty.
     *
     * @param be the crucible block entity
     * @return the goo type, or null
     */
    private static @Nullable GooType dominantPoolType(CrucibleBlockEntity be) {
        if (be.meltingItem.isEmpty()) {
            return null;
        }
        return PartiallyMeltedItem.getContents(be.meltingItem).largestType();
    }

    /**
     * Returns true if a PMI with remaining goo is loaded.
     *
     * @param be the crucible block entity
     * @return true if meltable item
     */
    static boolean hasMeltableItem(CrucibleBlockEntity be) {
        return !be.meltingItem.isEmpty()
                && !PartiallyMeltedItem.isFullyMelted(be.meltingItem);
    }

    /**
     * Drains extractionRate() mB from the PMI pool, distributed proportionally
     * across all goo types present. Each type receives at least 1 mB per tick
     * (or its remaining volume if less).
     *
     * @param be the crucible block entity
     */
    private static void drainFromPool(CrucibleBlockEntity be) {
        GooContents pmiContents = PartiallyMeltedItem.getContents(be.meltingItem);
        int totalRemaining = pmiContents.totalVolume();
        if (totalRemaining <= 0) {
            return;
        }

        int rate = CrucibleMath.extractionRate(totalRemaining, 0);
        Map<GooType, Integer> shares = CrucibleMath.computeDrainShares(pmiContents, rate);
        applyDrainShares(be, shares);
    }

    /**
     * Drains each goo type's share from the PMI and inserts it into the reservoir.
     *
     * @param be     the crucible block entity
     * @param shares the per-type drain amounts
     */
    private static void applyDrainShares(CrucibleBlockEntity be, Map<GooType, Integer> shares) {
        for (Map.Entry<GooType, Integer> entry : shares.entrySet()) {
            int drained = PartiallyMeltedItem.drain(
                    be.meltingItem, entry.getKey(), entry.getValue());
            be.reservoir.insertGoo(entry.getKey(), drained, false);
        }
    }

    /**
     * Clears the melting item when all goo has been fully drained.
     *
     * @param be the crucible block entity
     */
    private static void clearFinishedMeltingItem(CrucibleBlockEntity be) {
        if (be.meltingItem.isEmpty()) {
            return;
        }
        if (PartiallyMeltedItem.isFullyMelted(be.meltingItem)) {
            be.meltingItem = ItemStack.EMPTY;
        }
    }
}
