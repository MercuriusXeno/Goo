package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.ChainBehavior;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Rock goo: directional progressive sonic-boom that mines rock-compatible
 * blocks in a 3x3 column travelling into the surface the marker was
 * attached to. Stitched merge of the prior RockEffect (instant world
 * hit, places a chain marker via EffectBlockPlacement), RockBehavior
 * (chain marker fuse, per-tick pipeline), and RockExecutor (the
 * layer-mining utilities, also reused by ProgressiveAreaBlock).
 */
public final class RockBehavior implements WorldEffect, ChainBehavior {

    /** Ticks the sonic-boom preview leads the actual break. */
    private static final int PREVIEW_DELAY_TICKS = 8;

    private static final String TAG_PIPELINE_TICK = "RockPipelineTick";
    private static final String TAG_MINING_DEPTH = "RockMiningDepth";
    private static final String TAG_STACK_SNAPSHOT = "RockStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "RockFace";
    private static final String DEFAULT_FACE_NAME = "up";

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

    private int pipelineTick;
    private int miningDepth;
    private int stackCount;
    private Direction placedFace = Direction.UP;

    // --- WorldEffect (instant blob hit) ---

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.rockImplosion(level, pos, targetFace);
    }

    // --- ChainBehavior (fused chain marker detonation) ---

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.stackCount = be.getStackCount();
        this.placedFace = be.getPlacedFace();
        this.miningDepth = ChainFootprint.tunnelDepth(stackCount);
        this.pipelineTick = 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (pipelineTick < miningDepth) {
            previewLayer(level, pos, placedFace, pipelineTick);
        }
        int breakIndex = pipelineTick - PREVIEW_DELAY_TICKS;
        if (breakIndex >= 0 && breakIndex < miningDepth) {
            mineLayer(level, pos, placedFace, breakIndex, stackCount);
        }
        pipelineTick++;
    }

    @Override
    public boolean isActive() {
        return pipelineTick < miningDepth + PREVIEW_DELAY_TICKS;
    }

    @Override
    public int getMinedLayers() {
        int breakIndex = pipelineTick - PREVIEW_DELAY_TICKS;
        return Math.max(0, breakIndex);
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_PIPELINE_TICK, pipelineTick);
        output.putInt(TAG_MINING_DEPTH, miningDepth);
        output.putInt(TAG_STACK_SNAPSHOT, stackCount);
        output.putString(TAG_FACE_SNAPSHOT, placedFace.getName());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        pipelineTick = input.getIntOr(TAG_PIPELINE_TICK, 0);
        miningDepth = input.getIntOr(TAG_MINING_DEPTH, 0);
        stackCount = input.getIntOr(TAG_STACK_SNAPSHOT, 1);
        String faceName = input.getStringOr(TAG_FACE_SNAPSHOT, DEFAULT_FACE_NAME);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }

    // --- Layer mining utility (used by chain pipeline + ProgressiveAreaBlock) ---

    /**
     * Mines a single 3x3 layer of the implosion column at the given
     * {@code stepIndex} into the wall, and emits dust + break sound
     * localized to that layer.
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
        ItemStack silkTool = buildSilkTouchTool(level);
        List<ItemStack> drops = new ArrayList<>();
        List<int[]> footprint = ChainFootprint.layerFootprint(stackCount);
        int destroyed = mineFootprint(level, layerCenter, blastAxis, silkTool, drops, footprint);
        if (destroyed > 0) {
            ejectDrops(level, origin, placedFace, drops);
            spawnLayerDust(level, layerCenter, blastAxis, destroyed);
            playLayerSound(level, layerCenter, stackCount, stepIndex);
        }
        return destroyed;
    }

    /**
     * Creates a diamond pickaxe with silk touch for loot context.
     * @param level TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
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
     * center for {@code stepIndex}, without touching blocks.
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
     * @param origin TODO PARAM DESCRIPTION
     * @param placedFace TODO PARAM DESCRIPTION
     * @param stepIndex TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
     * layer (stepIndex 0) must land on the hit block itself. */
    private static BlockPos resolveLayerCenter(BlockPos origin, Direction placedFace, int stepIndex) {
        return origin.relative(placedFace.getOpposite(), stepIndex + 1);
    }

    /**
     * Mines the footprint at the given layer center.
     * @param level TODO PARAM DESCRIPTION
     * @param layerCenter TODO PARAM DESCRIPTION
     * @param blastAxis TODO PARAM DESCRIPTION
     * @param tool TODO PARAM DESCRIPTION
     * @param drops TODO PARAM DESCRIPTION
     * @param footprint TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
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
     * rock-compatible.
     * @param level TODO PARAM DESCRIPTION
     * @param target TODO PARAM DESCRIPTION
     * @param tool TODO PARAM DESCRIPTION
     * @param drops TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
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

    /**
     * Silk-breaks a single block, dropping items at its position.
     * @param level TODO PARAM DESCRIPTION
     * @param pos TODO PARAM DESCRIPTION
     */
    public static void silkBreakSingle(ServerLevel level, BlockPos pos) {
        if (!canMineRockAt(level, pos)) { return; }
        ItemStack tool = buildSilkTouchTool(level);
        List<ItemStack> drops = new ArrayList<>();
        if (tryMineBlock(level, pos, tool, drops)) {
            for (ItemStack drop : drops) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    /**
     * Returns true if the block at target is in-bounds, non-air, and rock-compatible.
     * @param level TODO PARAM DESCRIPTION
     * @param target TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
     */
    private static boolean canMineRockAt(ServerLevel level, BlockPos target) {
        if (!level.isInWorldBounds(target)) { return false; }
        BlockState state = level.getBlockState(target);
        return !state.isAir() && isRockBlock(level, state);
    }

    /**
     * Merges new drops into an accumulator.
     * @param accumulator TODO PARAM DESCRIPTION
     * @param newDrops TODO PARAM DESCRIPTION
     */
    private static void mergeDrops(List<ItemStack> accumulator, List<ItemStack> newDrops) {
        for (ItemStack drop : newDrops) {
            if (!tryMergeInto(accumulator, drop)) {
                accumulator.add(drop.copy());
            }
        }
    }

    /**
     * Tries to stack the drop into an existing accumulator entry.
     * @param accumulator TODO PARAM DESCRIPTION
     * @param drop TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
     */
    private static boolean tryMergeInto(List<ItemStack> accumulator, ItemStack drop) {
        for (ItemStack existing : accumulator) {
            if (ItemStack.isSameItemSameComponents(existing, drop)
                    && existing.getCount() + drop.getCount() <= existing.getMaxStackSize()) {
                existing.grow(drop.getCount());
                return true;
            }
        }
        return false;
    }

    /**
     * Ejects accumulated drops behind the marker.
     * @param level TODO PARAM DESCRIPTION
     * @param origin TODO PARAM DESCRIPTION
     * @param placedFace TODO PARAM DESCRIPTION
     * @param drops TODO PARAM DESCRIPTION
     */
    private static void ejectDrops(ServerLevel level, BlockPos origin,
                                   Direction placedFace, List<ItemStack> drops) {
        BlockPos ejectPos = origin.relative(placedFace);
        for (ItemStack stack : drops) {
            Block.popResource(level, ejectPos, stack);
        }
    }

    /**
     * Offsets a position in the two axes perpendicular to the blast axis.
     * @param center TODO PARAM DESCRIPTION
     * @param blastAxis TODO PARAM DESCRIPTION
     * @param a TODO PARAM DESCRIPTION
     * @param b TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
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
     * @param level TODO PARAM DESCRIPTION
     * @param state TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
     */
    private static boolean isRockBlock(ServerLevel level, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) { return false; }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        return AbilityMath.isRockCompatible(value);
    }

    /**
     * Spawns dust at the layer center.
     * @param level TODO PARAM DESCRIPTION
     * @param layerCenter TODO PARAM DESCRIPTION
     * @param blastAxis TODO PARAM DESCRIPTION
     * @param destroyed TODO PARAM DESCRIPTION
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
     * @param level TODO PARAM DESCRIPTION
     * @param layerCenter TODO PARAM DESCRIPTION
     * @param blastDir TODO PARAM DESCRIPTION
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
     * Plays a localized stone-break sound for this layer.
     * @param level TODO PARAM DESCRIPTION
     * @param layerCenter TODO PARAM DESCRIPTION
     * @param stackCount TODO PARAM DESCRIPTION
     * @param stepIndex TODO PARAM DESCRIPTION
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
