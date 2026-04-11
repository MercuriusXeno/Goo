package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Performs the rock chain effect: a directional implosion that mines
 * rock-compatible blocks in a 3x3 column travelling into the surface
 * the marker was attached to. Depth scales with stack count via
 * {@link EffectMath#computeImplosionDepth}. Mining is progressive: the
 * chain marker block entity calls {@link #mineLayer} once per server
 * tick, so the total break duration scales with depth.
 */
public final class RockExecutor {

    /** Half-width of the 3x3 footprint. */
    private static final int FOOTPRINT_HALF = 1;
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
     * @return the number of blocks destroyed in this layer
     */
    public static int mineLayer(ServerLevel level, BlockPos origin,
                                Direction placedFace, int stepIndex,
                                int stackCount) {
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        BlockPos layerCenter = resolveLayerCenter(origin, placedFace, stepIndex);
        int destroyed = mineFootprint(level, layerCenter, blastAxis);
        if (destroyed > 0) {
            spawnLayerDust(level, layerCenter, blastAxis, destroyed);
            playLayerSound(level, layerCenter, stackCount, stepIndex);
        }
        return destroyed;
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
        spawnLayerSonicBoom(level, layerCenter);
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
     * Mines the 3x3 footprint at the given layer center, perpendicular
     * to the blast axis.
     *
     * @param level       the server level
     * @param layerCenter the center of the current layer
     * @param blastAxis   the axis the blast travels along
     * @return the number of blocks destroyed in this layer
     */
    private static int mineFootprint(ServerLevel level, BlockPos layerCenter,
                                     Direction.Axis blastAxis) {
        int destroyed = 0;
        for (int a = -FOOTPRINT_HALF; a <= FOOTPRINT_HALF; a++) {
            for (int b = -FOOTPRINT_HALF; b <= FOOTPRINT_HALF; b++) {
                BlockPos target = offsetPerpendicular(layerCenter, blastAxis, a, b);
                if (tryMineBlock(level, target)) { destroyed++; }
            }
        }
        return destroyed;
    }

    /**
     * Attempts to mine a single block if it is in-bounds, non-air, and
     * rock-compatible.
     *
     * @param level  the server level
     * @param target the position to attempt
     * @return true if a block was destroyed
     */
    private static boolean tryMineBlock(ServerLevel level, BlockPos target) {
        if (!level.isInWorldBounds(target)) { return false; }
        BlockState state = level.getBlockState(target);
        if (state.isAir()) { return false; }
        if (!isRockBlock(level, state)) { return false; }
        level.destroyBlock(target, true);
        return true;
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
     * Spawns one Warden-style sonic-boom particle at the layer center
     * each tick the blast advances. Reads as a punching shockwave driving
     * deeper into the wall, layered on top of the dust slice.
     *
     * @param level       the server level
     * @param layerCenter the layer center position
     */
    private static void spawnLayerSonicBoom(ServerLevel level, BlockPos layerCenter) {
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        level.sendParticles(ParticleTypes.SONIC_BOOM, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
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
