package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.FrostFieldBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public final class WorldEffects {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Vertical offset for entities/clouds placed above the target block. */
    private static final double ABOVE_BLOCK_OFFSET = 1.0;

    // ── Metal urchin parameters ──
    /** Metal urchin damage cloud radius. */
    private static final float URCHIN_CLOUD_RADIUS = 1.5f;
    /** Metal urchin cloud duration in ticks (10 seconds). */
    private static final int URCHIN_CLOUD_DURATION = 200;
    /** Metal urchin immediate damage radius. */
    private static final double URCHIN_DAMAGE_RADIUS = 2.0;
    /** Metal urchin immediate damage amount. */
    private static final float URCHIN_DAMAGE = 3.0f;

    // ── Crystal shard parameters ──
    /** Crystal shard damage cloud radius. */
    private static final float CRYSTAL_CLOUD_RADIUS = 2.0f;
    /** Crystal shard cloud duration in ticks (15 seconds). */
    private static final int CRYSTAL_CLOUD_DURATION = 300;
    /** Crystal shard cloud radius shrink rate per tick. */
    private static final float CRYSTAL_CLOUD_SHRINK_RATE = -0.005f;
    /** Crystal shard immediate damage radius. */
    private static final double CRYSTAL_DAMAGE_RADIUS = 2.5;
    /** Crystal shard immediate damage amount. */
    private static final float CRYSTAL_DAMAGE = 4.0f;

    // ── Leaf growth parameters ──
    /** Leaf growth area half-width (5x5 area). */
    private static final int LEAF_GROWTH_RADIUS = 2;
    /** Random ticks applied per block for leaf growth. */
    private static final int LEAF_GROWTH_TICKS = 3;
    /** Leaf growth particle count. */
    private static final int LEAF_PARTICLE_COUNT = 20;
    /** Leaf growth horizontal particle spread. */
    private static final double LEAF_PARTICLE_H_SPREAD = 2.0;
    /** Leaf growth vertical particle spread. */
    private static final double LEAF_PARTICLE_V_SPREAD = 0.5;

    // ── Shroom spore parameters ──
    /** Shroom spore area half-width (5x5 area). */
    private static final int SHROOM_SPORE_RADIUS = 2;
    /** Probability of placing a mushroom on an air block above mycelium. */
    private static final float SHROOM_SPAWN_CHANCE = 0.2f;
    /** Shroom spore particle count. */
    private static final int SHROOM_PARTICLE_COUNT = 30;
    /** Shroom spore vertical offset for particles. */
    private static final double SHROOM_PARTICLE_Y_OFFSET = 1.5;
    /** Shroom spore horizontal particle spread. */
    private static final double SHROOM_PARTICLE_H_SPREAD = 2.0;
    /** Shroom spore vertical particle spread. */
    private static final double SHROOM_PARTICLE_V_SPREAD = 1.0;

    // ── Typhoon jet parameters ──
    /** Typhoon jet horizontal inflation radius. */
    private static final double TYPHOON_H_INFLATE = 1.0;
    /** Typhoon jet vertical inflation radius (column height). */
    private static final double TYPHOON_V_INFLATE = 5.0;
    /** Typhoon jet upward push strength. */
    private static final double TYPHOON_PUSH_STRENGTH = 1.5;
    /** Typhoon wind particle count. */
    private static final int TYPHOON_PARTICLE_COUNT = 30;
    /** Typhoon wind horizontal particle spread. */
    private static final double TYPHOON_PARTICLE_H_SPREAD = 0.5;
    /** Typhoon wind vertical particle spread. */
    private static final double TYPHOON_PARTICLE_V_SPREAD = 3.0;
    /** Typhoon wind particle speed. */
    private static final double TYPHOON_PARTICLE_SPEED = 0.1;

    // ── Hex ensorcelled parameters ──
    /** Hex ensorcelled cloud radius. */
    private static final float HEX_CLOUD_RADIUS = 4.0f;
    /** Hex ensorcelled cloud duration in ticks (30 seconds). */
    private static final int HEX_CLOUD_DURATION = 600;

    // ── Pulse signal parameters ──
    /** Pulse signal bell sound volume. */
    private static final float PULSE_SOUND_VOLUME = 1.0f;
    /** Pulse signal bell sound pitch. */
    private static final float PULSE_SOUND_PITCH = 2.0f;

    // ── Nether convert parameters ──
    /** Nether conversion sphere radius. */
    private static final int NETHER_CONVERT_RADIUS = 3;
    /** Maximum block hardness that nether conversion can dissolve. */
    private static final float NETHER_MAX_HARDNESS = 3.0f;
    /** Nether soul particle count. */
    private static final int NETHER_PARTICLE_COUNT = 30;
    /** Nether soul particle spread. */
    private static final double NETHER_PARTICLE_SPREAD = 2.0;
    /** Nether soul particle speed. */
    private static final double NETHER_PARTICLE_SPEED = 0.05;
    /** Block update flags for setBlock calls. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private WorldEffects() {}

    /**
     * Applies the world effect for the given goo type at the target position.
     *
     * @param level      the world
     * @param pos        the target block position
     * @param type       the goo type whose effect to apply
     * @param targetFace the face of the block that was hit, or null if unknown
     */
    public static void apply(Level level, BlockPos pos, GooType type, @Nullable Direction targetFace) {
        switch (type) {
            case METAL -> metalUrchin(level, pos);
            case CRYSTAL -> crystalShards(level, pos);
            case LEAF -> leafGrowth(level, pos);
            case VITAL -> vitalLivingBlob(level, pos);
            case SHROOM -> shroomSpores(level, pos);
            case ROCK -> rockImplosion(level, pos, targetFace);
            case BLAZE -> blazeExplosion(level, pos, targetFace);
            case FROST -> frostFreeze(level, pos, targetFace);
            case TYPHOON -> typhoonJet(level, pos);
            case GLOW -> glowDome(level, pos);
            case HEX -> hexEnsorcelled(level, pos);
            case PULSE -> pulseSignal(level, pos);
            case NETHER -> netherConvert(level, pos);
            case ENDER -> enderNode(level, pos);
            case AEON -> aeonBarrier(level, pos);
            default -> {}
        }
    }

    /**
     * Places a lingering damage cloud and deals immediate damage to nearby entities.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void metalUrchin(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) { return; }
        // Place a damage cloud that hurts entities walking through
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        cloud.setRadius(URCHIN_CLOUD_RADIUS);
        cloud.setDuration(URCHIN_CLOUD_DURATION);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0);
        cloud.setCustomParticle(ParticleTypes.CRIT);
        level.addFreshEntity(cloud);
        // Damage entities in area immediately
        damageEntitiesInArea(level, pos, URCHIN_DAMAGE_RADIUS, URCHIN_DAMAGE);
    }

    /**
     * Places a shrinking crystal damage cloud and deals immediate AoE damage.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void crystalShards(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) { return; }
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        cloud.setRadius(CRYSTAL_CLOUD_RADIUS);
        cloud.setDuration(CRYSTAL_CLOUD_DURATION);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(CRYSTAL_CLOUD_SHRINK_RATE); // slowly shrinks
        cloud.setCustomParticle(ParticleTypes.DAMAGE_INDICATOR);
        level.addFreshEntity(cloud);
        damageEntitiesInArea(level, pos, CRYSTAL_DAMAGE_RADIUS, CRYSTAL_DAMAGE);
    }

    /**
     * Applies random ticks to blocks in a 5x5 area to accelerate crop and plant growth.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void leafGrowth(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        for (int dx = -LEAF_GROWTH_RADIUS; dx <= LEAF_GROWTH_RADIUS; dx++) {
            for (int dz = -LEAF_GROWTH_RADIUS; dz <= LEAF_GROWTH_RADIUS; dz++) {
                tickGrowthColumn(serverLevel, pos.offset(dx, 0, dz));
            }
        }
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            LEAF_PARTICLE_COUNT, LEAF_PARTICLE_H_SPREAD, LEAF_PARTICLE_V_SPREAD, LEAF_PARTICLE_H_SPREAD, 0.0);
    }

    /**
     * Ticks growth on a single column: the target block and the block above it
     * (to catch crops sitting on farmland).
     *
     * @param level  the server level
     * @param target the ground-level position to tick
     */
    private static void tickGrowthColumn(ServerLevel level, BlockPos target) {
        tickGrowthAt(level, target);
        tickGrowthAt(level, target.above());
    }

    /**
     * Applies multiple random ticks to a single block if it supports random ticking.
     *
     * @param level the server level
     * @param pos   the position to tick
     */
    private static void tickGrowthAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isRandomlyTicking()) {
            for (int i = 0; i < LEAF_GROWTH_TICKS; i++) {
                state.randomTick(level, pos, level.getRandom());
            }
        }
    }

    /**
     * Spawns a small slime as a living blob placeholder at the target position.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void vitalLivingBlob(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) { return; }
        Slime slime = new Slime(EntityType.SLIME, level);
        slime.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        // Small slime as living blob
        level.addFreshEntity(slime);
    }

    /**
     * Converts grass/dirt to mycelium in a 5x5 area and randomly places mushrooms.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void shroomSpores(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        for (int dx = -SHROOM_SPORE_RADIUS; dx <= SHROOM_SPORE_RADIUS; dx++) {
            for (int dz = -SHROOM_SPORE_RADIUS; dz <= SHROOM_SPORE_RADIUS; dz++) {
                BlockPos target = pos.offset(dx, 0, dz);
                spreadMycelium(level, target);
                tryPlaceMushroom(level, target.above());
            }
        }
        serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + SHROOM_PARTICLE_Y_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            SHROOM_PARTICLE_COUNT, SHROOM_PARTICLE_H_SPREAD, SHROOM_PARTICLE_V_SPREAD, SHROOM_PARTICLE_H_SPREAD, 0.0);
    }

    /**
     * Converts grass or dirt to mycelium at the given position.
     *
     * @param level the current level
     * @param pos   the position to convert
     */
    private static void spreadMycelium(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
            level.setBlock(pos, Blocks.MYCELIUM.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Randomly places a red or brown mushroom if the position is air and
     * the mushroom can survive there.
     *
     * @param level the current level
     * @param pos   the position to try placing a mushroom
     */
    private static void tryPlaceMushroom(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) { return; }
        if (level.getRandom().nextFloat() >= SHROOM_SPAWN_CHANCE) { return; }
        Block shroom = level.getRandom().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
        if (shroom.defaultBlockState().canSurvive(level, pos)) {
            level.setBlock(pos, shroom.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Rock: chain implosion. Places a chain marker on the hit face.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    private static void rockImplosion(Level level, BlockPos pos,
                                      @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeOrStackChain(level, pos, targetFace, GooType.ROCK);
    }

    /**
     * Blaze: chain explosion. Places a chain marker on the hit face.
     * Additional blobs during the fuse window stack up to 4 for 3/5/7/9 radius.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    private static void blazeExplosion(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeOrStackChain(level, pos, targetFace, GooType.BLAZE);
    }

    /**
     * Stacks onto an existing chain marker if the hit block (or the
     * face-adjacent block) already has one of the same type. Otherwise
     * places a new marker on the hit face. Shared by all chain effects.
     *
     * @param level    the current level
     * @param hitBlock the block that was hit
     * @param face     the face that was hit, or null
     * @param type     the goo type for the chain marker
     */
    private static void placeOrStackChain(Level level, BlockPos hitBlock,
            @Nullable Direction face, GooType type) {
        // Hit block itself is a matching marker: stack directly
        if (tryStackExisting(level, hitBlock, type)) { return; }

        // Resolve placement on the hit face
        Direction resolvedFace = face == null ? Direction.UP : face;
        BlockPos placePos = hitBlock.relative(resolvedFace);

        // Face-adjacent block is a matching marker: stack onto it
        if (tryStackExisting(level, placePos, type)) { return; }

        // Place new marker in air
        if (!level.getBlockState(placePos).isAir()) { return; }
        level.setBlock(placePos, GooBlocks.CHAIN_MARKER.get().defaultBlockState(), BLOCK_UPDATE_FLAGS);
        if (level.getBlockEntity(placePos) instanceof ChainMarkerBlockEntity be) {
            be.initChain(type, resolvedFace);
        }
    }

    /**
     * Tries to stack onto an existing same-type chain marker.
     *
     * @param level the current level
     * @param pos   the block position to check
     * @param type  the expected goo type
     * @return true if stacking succeeded
     */
    private static boolean tryStackExisting(Level level, BlockPos pos, GooType type) {
        if (!level.getBlockState(pos).is(GooBlocks.CHAIN_MARKER.get())) { return false; }
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be
                && be.getGooType() == type) {
            be.tryStack();
            return true;
        }
        return false;
    }

    /**
     * Frost: instant freeze + persistent melt-resist field. Executes the
     * freeze immediately, then places (or stacks onto) a FrostFieldBlock
     * that prevents packed ice from being swapped back to regular ice
     * until the field duration expires.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    private static void frostFreeze(Level level, BlockPos pos,
                                    @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }

        // Resolve field placement position
        Direction resolvedFace = targetFace == null ? Direction.UP : targetFace;
        BlockPos fieldPos = pos.relative(resolvedFace);

        // Stack onto existing frost field if present
        if (tryStackFrostField(level, pos)) {
            refreeze(serverLevel, pos);
            return;
        }
        if (tryStackFrostField(level, fieldPos)) {
            refreeze(serverLevel, fieldPos);
            return;
        }

        // Compute radius for initial placement
        int radius = EffectMath.computeFreezeRadius(1);

        // Execute freeze immediately at the hit block
        FrostExecutor.execute(serverLevel, pos, radius);

        // Place the frost field in air
        BlockPos placePos = level.getBlockState(fieldPos).isAir() ? fieldPos : pos;
        if (!level.getBlockState(placePos).isAir()) { return; }
        level.setBlock(placePos, GooBlocks.FROST_FIELD.get().defaultBlockState(), BLOCK_UPDATE_FLAGS);
        if (level.getBlockEntity(placePos) instanceof FrostFieldBlockEntity be) {
            be.initField(1);
        }
    }

    /**
     * Tries to stack onto an existing frost field at the given position.
     *
     * @param level the current level
     * @param pos   the block position to check
     * @return true if stacking succeeded
     */
    private static boolean tryStackFrostField(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(GooBlocks.FROST_FIELD.get())
                && level.getBlockEntity(pos) instanceof FrostFieldBlockEntity be
                && be.tryStack();
    }

    /**
     * Re-executes the freeze at the field's updated radius after stacking.
     *
     * @param level    the server level
     * @param fieldPos the frost field block position
     */
    private static void refreeze(ServerLevel level, BlockPos fieldPos) {
        if (level.getBlockEntity(fieldPos) instanceof FrostFieldBlockEntity be) {
            FrostExecutor.execute(level, fieldPos, be.getRadius());
        }
    }

    /**
     * Launches all entities in a vertical column upward with wind particles.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void typhoonJet(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        // Launch all entities in a column upward
        AABB area = new AABB(pos).inflate(TYPHOON_H_INFLATE, TYPHOON_V_INFLATE, TYPHOON_H_INFLATE);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.push(0, TYPHOON_PUSH_STRENGTH, 0);
            entity.hurtMarked = true;
        }
        // Visual: wind particles
        serverLevel.sendParticles(ParticleTypes.CLOUD,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            TYPHOON_PARTICLE_COUNT, TYPHOON_PARTICLE_H_SPREAD, TYPHOON_PARTICLE_V_SPREAD, TYPHOON_PARTICLE_H_SPREAD, TYPHOON_PARTICLE_SPEED);
    }

    /**
     * Places a glowstone block as a permanent light source above or at the target.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void glowDome(Level level, BlockPos pos) {
        // Place glowstone as the permanent light source
        BlockPos lightPos = pos.above();
        if (level.getBlockState(lightPos).isAir()) {
            level.setBlock(lightPos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        } else if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Spawns a large witch-particle cloud to create a dark ensorcelled zone.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void hexEnsorcelled(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) { return; }
        // Spawn a few hostile mobs near the impact point
        // For now, just spawn particles and make the area dark via area effect cloud
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        cloud.setRadius(HEX_CLOUD_RADIUS);
        cloud.setDuration(HEX_CLOUD_DURATION);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0);
        cloud.setCustomParticle(ParticleTypes.WITCH);
        level.addFreshEntity(cloud);
    }

    /**
     * Sends neighbor updates to trigger observers and pistons, with a bell sound.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void pulseSignal(Level level, BlockPos pos) {
        // Update neighboring blocks (triggers observers, pistons, etc.)
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            level.neighborChanged(neighbor, level.getBlockState(pos).getBlock(), null);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, PULSE_SOUND_VOLUME, PULSE_SOUND_PITCH);
    }

    /**
     * Destroys blocks with low hardness in a sphere, dropping their items.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void netherConvert(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        EffectMath.forEachInSphere(pos, NETHER_CONVERT_RADIUS,
            target -> destroySoftBlock(level, target));
        serverLevel.sendParticles(ParticleTypes.SOUL,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + BLOCK_CENTER_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            NETHER_PARTICLE_COUNT, NETHER_PARTICLE_SPREAD, NETHER_PARTICLE_SPREAD, NETHER_PARTICLE_SPREAD, NETHER_PARTICLE_SPEED);
    }

    /**
     * Drops resources from and removes a single block if it has a breakable
     * hardness (non-negative and at most {@link #NETHER_MAX_HARDNESS}).
     *
     * @param level the current level
     * @param pos   the position to destroy
     */
    private static void destroySoftBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { return; }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness >= 0 && hardness <= NETHER_MAX_HARDNESS) {
            Block.dropResources(state, level, pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Places an end rod as a visual teleporter marker above the target position.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void enderNode(Level level, BlockPos pos) {
        BlockPos nodePos = pos.above();
        if (level.getBlockState(nodePos).isAir()) {
            level.setBlock(nodePos, Blocks.END_ROD.defaultBlockState(), Block.UPDATE_ALL);
        }
        // TODO: Full teleporter node system (look + shift to warp)
    }

    /**
     * Places obsidian as a blast-resistant barrier above the target position.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    private static void aeonBarrier(Level level, BlockPos pos) {
        BlockPos barPos = pos.above();
        if (level.getBlockState(barPos).isAir()) {
            // Use obsidian as barrier substitute (barrier is invisible, obsidian is tough)
            level.setBlock(barPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
        // TODO: Custom aeon barrier block with blast resistance
    }

    /**
     * Deals magic damage to all living entities within a radius of the position.
     *
     * @param level  the current level
     * @param pos    the center block position
     * @param radius the damage radius in blocks
     * @param damage the damage amount
     */
    private static void damageEntitiesInArea(Level level, BlockPos pos, double radius, float damage) {
        AABB area = new AABB(pos).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.hurt(level.damageSources().magic(), damage);
        }
    }
}
