package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.ChainBehavior;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Blaze goo: directional progressive burn that mines and auto-smelts a
 * tunnel of blocks in front of the marker. Stitched merge of the prior
 * BlazeEffect (instant world hit, places a chain marker via
 * EffectBlockPlacement), BlazeBehavior (chain marker fuse, per-tick
 * pipeline), and BlazeExecutor (the layer-mining utilities).
 */
public final class BlazeBehavior implements WorldEffect, ChainBehavior {

    /** Ticks the flame preview leads the actual break. */
    private static final int PREVIEW_DELAY_TICKS = 8;

    private static final String TAG_PIPELINE_TICK = "BlazePipelineTick";
    private static final String TAG_MINING_DEPTH = "BlazeMiningDepth";
    private static final String TAG_STACK_SNAPSHOT = "BlazeStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "BlazeFace";
    private static final String DEFAULT_FACE_NAME = "up";

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Fortune level applied to ore drops. */
    private static final int FORTUNE_LEVEL = 3;
    /** Block break level event ID (sends break particles to clients). */
    private static final int BREAK_EFFECT_EVENT = 2001;

    /** Flame particles per destroyed block in a single layer. */
    private static final int FLAME_PARTICLES_PER_BLOCK = 6;
    /** Lava drip particles per destroyed block. */
    private static final int LAVA_PARTICLES_PER_BLOCK = 2;
    /** Ember particles per destroyed block. */
    private static final int EMBER_PARTICLES_PER_BLOCK = 4;
    /** Spread perpendicular to the blast axis. */
    private static final double FLAME_PERP_SPREAD = 0.45;
    /** Spread along the blast axis: one layer thick. */
    private static final double FLAME_ALONG_SPREAD = 0.12;
    /** Upward velocity for flame particles. */
    private static final double FLAME_PARTICLE_SPEED = 0.05;
    /** Speed for ember particles - slower, floatier than main flames. */
    private static final double EMBER_PARTICLE_SPEED = 0.03;

    /** Base volume per layer explosion sound. */
    private static final float LAYER_VOLUME_BASE = 0.4f;
    /** Extra volume per stack for the per-layer sound. */
    private static final float LAYER_VOLUME_PER_STACK = 0.06f;
    /** Starting pitch for layer 0. */
    private static final float LAYER_PITCH_BASE = 1.1f;
    /** Pitch reduction per step as the blast digs deeper. */
    private static final float LAYER_PITCH_STEP = 0.03f;
    /** Minimum pitch after step-based reduction. */
    private static final float LAYER_PITCH_MIN = 0.7f;

    /** Base explosion damage at the layer center. */
    private static final float LAYER_DAMAGE = 6f;
    /** Knockback strength at the layer center. */
    private static final double KNOCKBACK_STRENGTH = 0.8;
    /** AABB expansion beyond the footprint for entity search. */
    private static final double ENTITY_SEARCH_EXPAND = 1.5;

    /** Flame particles per block in the preview footprint. */
    private static final int PREVIEW_FLAMES_PER_BLOCK = 3;
    /** Preview particle spread. */
    private static final double PREVIEW_SPREAD = 0.3;

    private int pipelineTick;
    private int miningDepth;
    private int stackCount;
    private Direction placedFace = Direction.UP;

    // --- WorldEffect (instant blob hit) ---

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.blazeExplosion(level, pos, targetFace);
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
            previewLayer(level, pos, placedFace, pipelineTick, stackCount);
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
     * Mines a single layer of the blaze burn at the given
     * {@code stepIndex} into the wall, auto-smelts drops, damages
     * entities, and emits flame particles + explosion sound.
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
        ItemStack fortuneTool = buildFortuneTool(level);
        List<ItemStack> drops = new ArrayList<>();
        List<int[]> footprint = ChainFootprint.layerFootprint(stackCount);
        int destroyed = mineFootprint(level, layerCenter, blastAxis, fortuneTool, drops, footprint);
        if (destroyed > 0) {
            ejectDrops(level, origin, placedFace, drops);
            spawnLayerFlames(level, layerCenter, blastAxis, destroyed);
            playLayerSound(level, layerCenter, stackCount, stepIndex);
        }
        damageEntitiesAtLayer(level, layerCenter, blastAxis, footprint);
        return destroyed;
    }

    /**
     * Emits flame burst particles at each block in the footprint for
     * {@code stepIndex}, without touching blocks. The caller schedules
     * this a few ticks ahead of {@link #mineLayer} so the preview
     * visually leads the destruction.
     *
     * @param level      the server level
     * @param origin     the anchor (marker) block position
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @param stackCount the stack count for footprint computation
     */
    public static void previewLayer(ServerLevel level, BlockPos origin,
                                    Direction placedFace, int stepIndex,
                                    int stackCount) {
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        BlockPos layerCenter = resolveLayerCenter(origin, placedFace, stepIndex);
        List<int[]> footprint = ChainFootprint.layerFootprint(stackCount);
        for (int[] offset : footprint) {
            BlockPos blockPos = offsetPerpendicular(layerCenter, blastAxis, offset[0], offset[1]);
            double bx = blockPos.getX() + BLOCK_CENTER_OFFSET;
            double by = blockPos.getY() + BLOCK_CENTER_OFFSET;
            double bz = blockPos.getZ() + BLOCK_CENTER_OFFSET;
            level.sendParticles(ParticleTypes.FLAME,
                    bx, by, bz, PREVIEW_FLAMES_PER_BLOCK,
                    PREVIEW_SPREAD, PREVIEW_SPREAD, PREVIEW_SPREAD, FLAME_PARTICLE_SPEED);
        }
    }


    /** First layer (stepIndex 0) lands on the hit block itself, one step
     * into the wall from the marker's air block.
     *
     * @param origin     the anchor (marker) block position
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @return the block position at the center of the given blast layer
     */
    private static BlockPos resolveLayerCenter(BlockPos origin, Direction placedFace, int stepIndex) {
        return origin.relative(placedFace.getOpposite(), stepIndex + 1);
    }


    /** Creates a diamond pickaxe with fortune 3 for loot context.
     *
     * @param level the server level (provides registry access)
     * @return a fortune-3 diamond pickaxe
     */
    private static ItemStack buildFortuneTool(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        Holder<Enchantment> fortune = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        tool.enchant(fortune, FORTUNE_LEVEL);
        return tool;
    }

    /**
     * Mines the footprint at the given layer center.
     *
     * @param level       the server level
     * @param layerCenter the center of the current layer
     * @param blastAxis   the axis the blast travels along
     * @param tool        the fortune tool for loot context
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
     * destructible (destroySpeed >= 0). Drops use fortune 3 loot context
     * and are auto-smelted via furnace recipes.
     *
     * @param level the server level
     * @param pos   the position to attempt
     * @param tool  the fortune tool for loot context
     * @param drops accumulator for block drops
     * @return true if a block was destroyed
     */
    private static boolean tryMineBlock(ServerLevel level, BlockPos pos,
                                        ItemStack tool, List<ItemStack> drops) {
        if (!canMineAt(level, pos)) { return false; }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        List<ItemStack> rawDrops = Block.getDrops(state, level, pos, blockEntity, null, tool);
        for (ItemStack drop : rawDrops) {
            mergeDrops(drops, trySmelting(level, drop));
        }
        state.spawnAfterBreak(level, pos, tool, true);

        level.levelEvent(BREAK_EFFECT_EVENT, pos, Block.getId(state));
        level.removeBlock(pos, false);
        return true;
    }

    /** Fortune-smelts a single block, dropping items at its position.
     * Used by ring-based flat delivery.
     *
     * @param level the server level
     * @param pos   the block position to mine and smelt
     */
    public static void fortuneSmeltSingle(ServerLevel level, BlockPos pos) {
        if (!canMineAt(level, pos)) { return; }
        ItemStack tool = buildFortuneTool(level);
        List<ItemStack> drops = new ArrayList<>();
        if (tryMineBlock(level, pos, tool, drops)) {
            for (ItemStack drop : drops) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    /** Returns true if the block at pos is in-bounds, non-air, and destructible.
     *
     * @param level the server level
     * @param pos   the block position to check
     * @return true if the block can be mined
     */
    private static boolean canMineAt(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) { return false; }
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0;
    }

    /** Attempts to smelt an item via furnace recipe. Returns the smelted
     * result at the same stack count, or the original if no recipe exists.
     *
     * @param level the server level
     * @param drop  the item to try smelting
     * @return smelted result or the original drop
     */
    private static ItemStack trySmelting(ServerLevel level, ItemStack drop) {
        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), level);
        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value()
                    .assemble(new SingleRecipeInput(drop));
            result.setCount(drop.getCount());
            return result;
        }
        return drop;
    }

    /**
     * Merges a drop into the accumulator, stacking with existing entries
     * where possible so the final ejection produces fewer item entities.
     *
     * @param accumulator the running drop list
     * @param drop        the drop to merge
     */
    private static void mergeDrops(List<ItemStack> accumulator, ItemStack drop) {
        for (ItemStack existing : accumulator) {
            if (ItemStack.isSameItemSameComponents(existing, drop)
                    && existing.getCount() + drop.getCount() <= existing.getMaxStackSize()) {
                existing.grow(drop.getCount());
                return;
            }
        }
        accumulator.add(drop.copy());
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
     * Offsets a position in the two axes perpendicular to the blast axis.
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
     * Damages living entities within the layer's footprint AABB.
     * Item entities are skipped so drops survive. Damage falls off
     * linearly with distance from the layer center.
     *
     * @param level       the server level
     * @param layerCenter the center of the current layer
     * @param blastAxis   the axis the blast travels along
     * @param footprint   2D offsets defining the layer shape
     */
    private static void damageEntitiesAtLayer(ServerLevel level, BlockPos layerCenter,
                                              Direction.Axis blastAxis, List<int[]> footprint) {
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        Vec3 center = new Vec3(cx, cy, cz);

        double maxOffset = computeMaxOffset(footprint);
        double radius = maxOffset + ENTITY_SEARCH_EXPAND;
        AABB area = new AABB(
                cx - radius, cy - radius, cz - radius,
                cx + radius, cy + radius, cz + radius);

        for (Entity entity : level.getEntities(null, area)) {
            if (entity instanceof ItemEntity) { continue; }
            if (!(entity instanceof LivingEntity)) { continue; }
            double dist = entity.position().distanceTo(center);
            if (dist > radius) { continue; }
            float falloff = 1f - (float) (dist / radius);
            entity.hurtServer(level,
                    level.damageSources().source(DamageTypes.EXPLOSION),
                    LAYER_DAMAGE * falloff);
            Vec3 knockback = entity.position().subtract(center)
                    .normalize().scale(KNOCKBACK_STRENGTH * falloff);
            entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
            entity.hurtMarked = true;
        }
    }

    /** Computes the maximum Euclidean offset in the footprint for entity
     * search radius.
     *
     * @param footprint 2D offsets from {@link ChainFootprint}
     * @return the maximum distance from origin in the footprint
     */
    private static double computeMaxOffset(List<int[]> footprint) {
        double max = 0;
        for (int[] offset : footprint) {
            double dist = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
            if (dist > max) { max = dist; }
        }
        return max;
    }


    /**
     * Spawns flame, lava, and ember particles at the layer center,
     * scaled by the number of blocks destroyed.
     *
     * @param level       the server level
     * @param layerCenter the layer center position
     * @param blastAxis   the axis the blast travels along
     * @param destroyed   the number of blocks destroyed in this layer
     */
    private static void spawnLayerFlames(ServerLevel level, BlockPos layerCenter,
                                         Direction.Axis blastAxis, int destroyed) {
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        double spreadX = blastAxis == Direction.Axis.X ? FLAME_ALONG_SPREAD : FLAME_PERP_SPREAD;
        double spreadY = blastAxis == Direction.Axis.Y ? FLAME_ALONG_SPREAD : FLAME_PERP_SPREAD;
        double spreadZ = blastAxis == Direction.Axis.Z ? FLAME_ALONG_SPREAD : FLAME_PERP_SPREAD;

        level.sendParticles(ParticleTypes.FLAME,
                cx, cy, cz, FLAME_PARTICLES_PER_BLOCK * destroyed,
                spreadX, spreadY, spreadZ, FLAME_PARTICLE_SPEED);
        level.sendParticles(ParticleTypes.LAVA,
                cx, cy, cz, LAVA_PARTICLES_PER_BLOCK * destroyed,
                spreadX, spreadY, spreadZ, 0.0);
        level.sendParticles(ParticleTypes.SMALL_FLAME,
                cx, cy, cz, EMBER_PARTICLES_PER_BLOCK * destroyed,
                spreadX, spreadY, spreadZ, EMBER_PARTICLE_SPEED);
    }


    /**
     * Plays a localized explosion sound for this layer. Pitch dips
     * per step to suggest the burn grinding deeper.
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
        level.playSound(null, layerCenter, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, volume, pitch);
    }
}
