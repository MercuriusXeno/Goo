package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.FrostFieldBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
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
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

public class WorldEffects {

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
        }
    }

    // Metal: Urchin spines  - damage mobs passing over
    private static void metalUrchin(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Place a damage cloud that hurts entities walking through
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        cloud.setRadius(1.5f);
        cloud.setDuration(200); // 10 seconds
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0);
        cloud.setCustomParticle(ParticleTypes.CRIT);
        level.addFreshEntity(cloud);
        // Damage entities in area immediately
        damageEntitiesInArea(level, pos, 2.0, 3.0f);
    }

    // Crystal: Glass shards damage zone
    private static void crystalShards(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return;
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        cloud.setRadius(2.0f);
        cloud.setDuration(300); // 15 seconds
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(-0.005f); // slowly shrinks
        cloud.setCustomParticle(ParticleTypes.DAMAGE_INDICATOR);
        level.addFreshEntity(cloud);
        damageEntitiesInArea(level, pos, 2.5, 4.0f);
    }

    // Leaf: Growth pulse  - bone meal effect in area every 16 seconds
    private static void leafGrowth(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Immediate growth pulse in 5x5 area
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos target = pos.offset(dx, 0, dz);
                BlockState state = level.getBlockState(target);
                if (state.isRandomlyTicking()) {
                    for (int i = 0; i < 3; i++) {
                        state.randomTick(serverLevel, target, level.getRandom());
                    }
                }
                // Also check one above (for crops on farmland)
                BlockPos above = target.above();
                BlockState aboveState = level.getBlockState(above);
                if (aboveState.isRandomlyTicking()) {
                    for (int i = 0; i < 3; i++) {
                        aboveState.randomTick(serverLevel, above, level.getRandom());
                    }
                }
            }
        }
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
            20, 2.0, 0.5, 2.0, 0.0);
    }

    // Vital: Spawn a friendly slime (living blob placeholder)
    private static void vitalLivingBlob(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return;
        Slime slime = new Slime(EntityType.SLIME, level);
        slime.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        // Small slime as living blob
        level.addFreshEntity(slime);
    }

    // Shroom: Spore area  - places mycelium and mushrooms
    private static void shroomSpores(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos target = pos.offset(dx, 0, dz);
                BlockState state = level.getBlockState(target);
                // Convert grass/dirt to mycelium
                if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
                    level.setBlock(target, Blocks.MYCELIUM.defaultBlockState(), Block.UPDATE_ALL);
                }
                // Chance to place mushroom above
                BlockPos above = target.above();
                if (level.getBlockState(above).isAir() && level.getRandom().nextFloat() < 0.2f) {
                    Block shroom = level.getRandom().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
                    if (shroom.defaultBlockState().canSurvive(level, above)) {
                        level.setBlock(above, shroom.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
            30, 2.0, 1.0, 2.0, 0.0);
    }

    /** Rock: chain implosion. Places a chain marker on the hit face. */
    private static void rockImplosion(Level level, BlockPos pos,
                                      @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) return;
        placeOrStackChain(level, pos, targetFace, GooType.ROCK);
    }

    /**
     * Blaze: chain explosion. Places a chain marker on the hit face.
     * Additional blobs during the fuse window stack up to 4 for 3/5/7/9 radius.
     */
    private static void blazeExplosion(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) return;
        placeOrStackChain(level, pos, targetFace, GooType.BLAZE);
    }

    /**
     * Stacks onto an existing chain marker if the hit block (or the
     * face-adjacent block) already has one of the same type. Otherwise
     * places a new marker on the hit face. Shared by all chain effects.
     */
    private static void placeOrStackChain(Level level, BlockPos hitBlock,
            @Nullable Direction face, GooType type) {
        // Hit block itself is a matching marker: stack directly
        if (tryStackExisting(level, hitBlock, type)) return;

        // Resolve placement on the hit face
        if (face == null) face = Direction.UP;
        BlockPos placePos = hitBlock.relative(face);

        // Face-adjacent block is a matching marker: stack onto it
        if (tryStackExisting(level, placePos, type)) return;

        // Place new marker in air
        if (!level.getBlockState(placePos).isAir()) return;
        level.setBlock(placePos, GooBlocks.CHAIN_MARKER.get().defaultBlockState(), 3);
        if (level.getBlockEntity(placePos) instanceof ChainMarkerBlockEntity be) {
            be.initChain(type, face);
        }
    }

    /** Tries to stack onto an existing same-type chain marker. */
    private static boolean tryStackExisting(Level level, BlockPos pos, GooType type) {
        if (!level.getBlockState(pos).is(GooBlocks.CHAIN_MARKER.get())) return false;
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
     */
    private static void frostFreeze(Level level, BlockPos pos,
                                    @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Resolve field placement position
        if (targetFace == null) targetFace = Direction.UP;
        BlockPos fieldPos = pos.relative(targetFace);

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
        if (!level.getBlockState(placePos).isAir()) return;
        level.setBlock(placePos, GooBlocks.FROST_FIELD.get().defaultBlockState(), 3);
        if (level.getBlockEntity(placePos) instanceof FrostFieldBlockEntity be) {
            be.initField(1);
        }
    }

    /** Tries to stack onto an existing frost field at the given position. */
    private static boolean tryStackFrostField(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(GooBlocks.FROST_FIELD.get())) return false;
        if (level.getBlockEntity(pos) instanceof FrostFieldBlockEntity be) {
            return be.tryStack();
        }
        return false;
    }

    /** Re-executes the freeze at the field's updated radius after stacking. */
    private static void refreeze(ServerLevel level, BlockPos fieldPos) {
        if (level.getBlockEntity(fieldPos) instanceof FrostFieldBlockEntity be) {
            FrostExecutor.execute(level, fieldPos, be.getRadius());
        }
    }

    // Typhoon: Persistent jet stream  - launch entities upward
    private static void typhoonJet(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Launch all entities in a column upward
        AABB area = new AABB(pos).inflate(1.0, 5.0, 1.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.push(0, 1.5, 0);
            entity.hurtMarked = true;
        }
        // Visual: wind particles
        serverLevel.sendParticles(ParticleTypes.CLOUD,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
            30, 0.5, 3.0, 0.5, 0.1);
    }

    // Glow: Place a glowstone light source (luminescent dome)
    private static void glowDome(Level level, BlockPos pos) {
        // Place glowstone as the permanent light source
        BlockPos lightPos = pos.above();
        if (level.getBlockState(lightPos).isAir()) {
            level.setBlock(lightPos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        } else if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // Hex: Ensorcelled area  - set nearby blocks dark enough for mob spawning
    private static void hexEnsorcelled(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Spawn a few hostile mobs near the impact point
        // For now, just spawn particles and make the area dark via area effect cloud
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        cloud.setRadius(4.0f);
        cloud.setDuration(600); // 30 seconds
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0);
        cloud.setCustomParticle(ParticleTypes.WITCH);
        level.addFreshEntity(cloud);
    }

    // Pulse: Emit a redstone signal pulse to nearby signal receivers
    private static void pulseSignal(Level level, BlockPos pos) {
        // Update neighboring blocks (triggers observers, pistons, etc.)
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            level.neighborChanged(neighbor, level.getBlockState(pos).getBlock(), null);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 2.0f);
    }

    // Nether: Convert blocks in area to air (negative energy dissolves)
    private static void netherConvert(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    BlockPos target = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(target);
                    float hardness = state.getDestroySpeed(level, target);
                    // Only convert blocks with reasonable hardness (not bedrock, etc.)
                    if (hardness >= 0 && hardness <= 3.0f && !state.isAir()) {
                        // Drop the block as item, then remove
                        Block.dropResources(state, level, target);
                        level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        serverLevel.sendParticles(ParticleTypes.SOUL,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            30, 2.0, 2.0, 2.0, 0.05);
    }

    // Ender: Place an ender pearl style teleporter marker (end rod as visual)
    private static void enderNode(Level level, BlockPos pos) {
        BlockPos nodePos = pos.above();
        if (level.getBlockState(nodePos).isAir()) {
            level.setBlock(nodePos, Blocks.END_ROD.defaultBlockState(), Block.UPDATE_ALL);
        }
        // TODO: Full teleporter node system (look + shift to warp)
    }

    // Aeon: Place a barrier-like reinforced block
    private static void aeonBarrier(Level level, BlockPos pos) {
        BlockPos barPos = pos.above();
        if (level.getBlockState(barPos).isAir()) {
            // Use obsidian as barrier substitute (barrier is invisible, obsidian is tough)
            level.setBlock(barPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
        // TODO: Custom aeon barrier block with blast resistance
    }

    private static void damageEntitiesInArea(Level level, BlockPos pos, double radius, float damage) {
        AABB area = new AABB(pos).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.hurt(level.damageSources().magic(), damage);
        }
    }
}
