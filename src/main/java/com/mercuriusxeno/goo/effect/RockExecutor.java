package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs the rock chain effect: a directional implosion that mines
 * rock-compatible blocks in a 3x3 column travelling into the surface
 * the marker was attached to. Depth scales with stack count via
 * {@link ChainFootprint#tunnelDepth}. Mining is progressive: the
 * chain marker block entity calls {@link #mineLayer} once per server
 * tick, so the total break duration scales with depth.
 */
public final class RockExecutor {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Dust particles per destroyed block in a single layer. */
    private static final int DUST_PARTICLES_PER_BLOCK = 4;
    /** Spread perpendicular to the blast axis (roughly the 3x3 footprint). */
    private static final double DUST_PERP_SPREAD = 0.45;
    /** Spread along the blast axis: one layer thick. */
    private static final double DUST_ALONG_SPREAD = 0.12;
    /** Dust particle velocity. */
    private static final double DUST_PARTICLE_SPEED = 0.02;
    /** Base volume per layer break sound. */
    private static final float LAYER_VOLUME_BASE = 0.55f;
    /** Extra volume per stack for the per-layer break sound. */
    private static final float LAYER_VOLUME_PER_STACK = 0.08f;
    /** Starting pitch for layer 0. */
    private static final float LAYER_PITCH_BASE = 0.75f;
    /** Pitch reduction per step as the blast digs deeper. */
    private static final float LAYER_PITCH_STEP = 0.03f;
    /** Minimum pitch after step-based reduction. */
    private static final float LAYER_PITCH_MIN = 0.45f;
    /** Block break level event ID (sends break particles to clients). */
    private static final int BREAK_EFFECT_EVENT = 2001;

    private RockExecutor() {}

    /**
     * Mines a single 3x3 layer of the implosion column at the given
     * {@code stepIndex} into the wall, and emits dust + break sound
     * localized to that layer. The sonic-boom shockwave preview for
     * this layer is emitted separately (earlier) via
     * {@link #previewLayer} so it leads the destruction by a few ticks.
     *
     * @param level      the server level
     * @param origin     the anchor (marker) block position
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @param stackCount the raw stack count (scales sound volume)
     * @param flatMode   true for flat (taxicab circle) footprint
     * @return the number of blocks destroyed in this layer
     */
    public static int mineLayer(ServerLevel level, BlockPos origin,
                                Direction placedFace, int stepIndex,
                                int stackCount, boolean flatMode) {
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        BlockPos layerCenter = resolveLayerCenter(origin, placedFace, stepIndex);
        ItemStack silkTool = buildSilkTouchTool(level);
        List<ItemStack> drops = new ArrayList<>();
        List<int[]> footprint = flatMode
                ? ChainFootprint.flatFootprint(stackCount)
                : ChainFootprint.layerFootprint(stackCount);
        int destroyed = mineFootprint(level, layerCenter, blastAxis, silkTool, drops, footprint);
        if (destroyed > 0) {
            ejectDrops(level, origin, placedFace, drops);
            spawnLayerDust(level, layerCenter, blastAxis, destroyed);
            playLayerSound(level, layerCenter, stackCount, stepIndex);
        }
        return destroyed;
    }

    /** Creates a diamond pickaxe with silk touch for loot context.
     *
     * @param level the server level (provides registry access)
     * @return a silk-touch diamond pickaxe
     */
    private static ItemStack buildSilkTouchTool(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        Holder<Enchantment> silkTouch = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        tool.enchant(silkTouch, 1);
        return tool;
    }

    /**
     * Emits the warden-style sonic-boom shockwave particle at the layer
     * center for {@code stepIndex}, without touching blocks. The caller
     * schedules this a few ticks ahead of {@link #mineLayer} for the
     * same {@code stepIndex} so the shockwave visually leads the break.
     *
     * @param level      the server level
     * @param origin     the anchor (marker) block position
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     */
    public static void previewLayer(ServerLevel level, BlockPos origin,
                                    Direction placedFace, int stepIndex) {
        BlockPos layerCenter = resolveLayerCenter(origin, placedFace, stepIndex);
        BlockPos particlePos = layerCenter.relative(placedFace);
        spawnLayerSonicBoom(level, particlePos, placedFace.getOpposite());
    }

    /** Marker sits in the air block adjacent to the hit face; the first
     * layer (stepIndex 0) must land on the hit block itself, one step into
     * the wall from the marker. Offsetting by {@code stepIndex + 1} keeps
     * the blob's own air block out of the footprint.
     *
     * @param origin     the anchor (marker) block position
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @return the block position at the center of the given blast layer
     */
    private static BlockPos resolveLayerCenter(BlockPos origin, Direction placedFace, int stepIndex) {
        return origin.relative(placedFace.getOpposite(), stepIndex + 1);
    }

    /**
     * Mines the footprint at the given layer center using the provided
     * 2D offsets perpendicular to the blast axis.
     *
     * @param level       the server level
     * @param layerCenter the center of the current layer
     * @param blastAxis   the axis the blast travels along
     * @param tool        the silk-touch tool for loot context
     * @param drops       accumulator for mined block drops
     * @param footprint   2D offsets from {@link ChainFootprint}
     * @return the number of blocks destroyed in this layer
     */
    private static int mineFootprint(ServerLevel level, BlockPos layerCenter,
                                     Direction.Axis blastAxis, ItemStack tool,
                                     List<ItemStack> drops, List<int[]> footprint) {
        int destroyed = 0;
        for (int[] offset : footprint) {
            BlockPos target = offsetPerpendicular(layerCenter, blastAxis, offset[0], offset[1]);
            if (tryMineBlock(level, target, tool, drops)) { destroyed++; }
        }
        return destroyed;
    }

    /**
     * Attempts to mine a single block if it is in-bounds, non-air, and
     * rock-compatible. Drops use silk touch loot context so blocks drop
     * themselves (e.g. stone drops stone, not cobblestone). Drops are
     * accumulated into the provided list rather than spawned in-place.
     *
     * @param level  the server level
     * @param target the position to attempt
     * @param tool   the silk-touch tool for loot context
     * @param drops  accumulator for block drops
     * @return true if a block was destroyed
     */
    private static boolean tryMineBlock(ServerLevel level, BlockPos target,
                                        ItemStack tool, List<ItemStack> drops) {
        if (!canMineRockAt(level, target)) { return false; }
        BlockState state = level.getBlockState(target);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(target) : null;

        mergeDrops(drops, Block.getDrops(state, level, target, blockEntity, null, tool));
        state.spawnAfterBreak(level, target, tool, true);

        level.levelEvent(BREAK_EFFECT_EVENT, target, Block.getId(state));
        level.removeBlock(target, false);
        return true;
    }

    /** Returns true if the block at target is in-bounds, non-air, and rock-compatible.
     *
     * @param level  the server level
     * @param target the block position to check
     * @return true if the block can be mined by rock
     */
    private static boolean canMineRockAt(ServerLevel level, BlockPos target) {
        if (!level.isInWorldBounds(target)) { return false; }
        BlockState state = level.getBlockState(target);
        return !state.isAir() && isRockBlock(level, state);
    }

    /**
     * Merges new drops into an accumulator, stacking with existing entries
     * where possible so the final ejection produces fewer item entities.
     *
     * @param accumulator the running drop list
     * @param newDrops    drops from a single block break
     */
    private static void mergeDrops(List<ItemStack> accumulator, List<ItemStack> newDrops) {
        for (ItemStack drop : newDrops) {
            boolean merged = false;
            for (ItemStack existing : accumulator) {
                if (ItemStack.isSameItemSameComponents(existing, drop)
                        && existing.getCount() + drop.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(drop.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) { accumulator.add(drop.copy()); }
        }
    }

    /**
     * Ejects accumulated drops behind the marker (the face the player
     * placed on), so all items land in a pile at the origin.
     *
     * @param level      the server level
     * @param origin     the marker block position
     * @param placedFace the face the marker was attached to
     * @param drops      the accumulated drops to eject
     */
    private static void ejectDrops(ServerLevel level, BlockPos origin,
                                   Direction placedFace, List<ItemStack> drops) {
        BlockPos ejectPos = origin.relative(placedFace);
        for (ItemStack stack : drops) {
            Block.popResource(level, ejectPos, stack);
        }
    }

    /**
     * Offsets a position in the two axes perpendicular to the blast axis,
     * producing one cell of the 3x3 footprint at the current blast layer.
     *
     * @param center    the center of the current blast layer
     * @param blastAxis the axis the blast travels along
     * @param a         first perpendicular offset
     * @param b         second perpendicular offset
     * @return the footprint cell position
     */
    private static BlockPos offsetPerpendicular(BlockPos center,
                                                Direction.Axis blastAxis,
                                                int a, int b) {
        return switch (blastAxis) {
            case X -> center.offset(0, a, b);
            case Y -> center.offset(a, 0, b);
            case Z -> center.offset(a, b, 0);
        };
    }

    /**
     * Checks if a block's item form is rock-compatible by goo composition.
     *
     * @param level the server level
     * @param state the block state to check
     * @return true if the block is rock-compatible
     */
    private static boolean isRockBlock(ServerLevel level, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) { return false; }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        return EffectMath.isRockCompatible(value);
    }

    /**
     * Spawns dust at the layer center: a thin slice perpendicular to
     * the blast axis, sized by the number of blocks destroyed in this layer.
     *
     * @param level       the server level
     * @param layerCenter the layer center position
     * @param blastAxis   the axis the blast travels along
     * @param destroyed   the number of blocks destroyed in this layer
     */
    private static void spawnLayerDust(ServerLevel level, BlockPos layerCenter,
                                       Direction.Axis blastAxis, int destroyed) {
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        int count = DUST_PARTICLES_PER_BLOCK * destroyed;
        double spreadX = blastAxis == Direction.Axis.X ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        double spreadY = blastAxis == Direction.Axis.Y ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        double spreadZ = blastAxis == Direction.Axis.Z ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        level.sendParticles(ParticleTypes.DUST_PLUME,
                cx, cy, cz, count, spreadX, spreadY, spreadZ, DUST_PARTICLE_SPEED);
    }

    /**
     * Spawns an oriented sonic-boom particle at the layer center, flat
     * against the blast plane. The direction ordinal is encoded in
     * xDist so the client particle provider can orient the quad.
     *
     * @param level       the server level
     * @param layerCenter the layer center position
     * @param blastDir    the blast travel direction
     */
    private static void spawnLayerSonicBoom(ServerLevel level, BlockPos layerCenter,
                                            Direction blastDir) {
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        level.sendParticles(GooParticles.ORIENTED_BOOM.get(),
                cx, cy, cz, 0, blastDir.ordinal(), 0.0, 0.0, 1.0);
    }

    /**
     * Plays a localized stone-break sound for this layer. Pitch dips
     * slightly per step to suggest the blast grinding deeper.
     *
     * @param level       the server level
     * @param layerCenter the layer center position
     * @param stackCount  the raw stack count (scales volume)
     * @param stepIndex   zero-based layer offset along the blast direction
     */
    private static void playLayerSound(ServerLevel level, BlockPos layerCenter,
                                       int stackCount, int stepIndex) {
        float volume = LAYER_VOLUME_BASE + LAYER_VOLUME_PER_STACK * stackCount;
        float pitch = Math.max(LAYER_PITCH_MIN,
                LAYER_PITCH_BASE - LAYER_PITCH_STEP * stepIndex);
        level.playSound(null, layerCenter, SoundEvents.STONE_BREAK,
                SoundSource.BLOCKS, volume, pitch);
    }
}
