package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.ChainBehavior;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.*;

/**
 * Metal goo: spike trap. Stitched merge of the prior MetalEffect
 * (instant world hit, places a trap via EffectBlockPlacement) and
 * MetalBehavior (chain marker fuse, multi-tick spike-animation system).
 */
public final class MetalBehavior implements WorldEffect, ChainBehavior {

    /**
     * Detection and spike reach radius in blocks (50% wider than original).
     */
    public static final double SPIKE_RADIUS = 3.75;
    /**
     * Total ticks for one spike animation cycle.
     */
    public static final int TOTAL_ANIM_TICKS = 13;
    /**
     * Tick at which the spike reaches full extension and deals damage.
     */
    public static final int STRIKE_TICK = 6;
    /**
     * Blob contraction scale during windup (0 = no change, positive = smaller).
     */
    public static final float WINDUP_CONTRACT = 0.30f;
    /**
     * Damage per impale.
     */
    private static final float STAB_DAMAGE = 6f;
    /**
     * Cooldown ticks between consecutive spike shots.
     */
    private static final int SPIKE_COOLDOWN = 10;
    /**
     * Crit particle count on spike impact.
     */
    private static final int CRIT_PARTICLE_COUNT = 8;
    /**
     * Crit particle spread radius.
     */
    private static final double CRIT_SPREAD = 0.4;
    /**
     * Crit particle speed.
     */
    private static final double CRIT_SPEED = 0.15;
    /**
     * Smoke particle count when the trap expires.
     */
    private static final int SMOKE_PARTICLE_COUNT = 12;
    /**
     * Smoke particle spread radius.
     */
    private static final double SMOKE_SPREAD = 0.3;
    /**
     * Smoke particle speed.
     */
    private static final double SMOKE_SPEED = 0.05;
    /**
     * Block center offset (0.5 added to BlockPos coords).
     */
    private static final double BLOCK_CENTER = 0.5;
    /**
     * Dissipate sound volume.
     */
    private static final float DISSIPATE_VOLUME = 0.5f;
    /**
     * Dissipate sound pitch.
     */
    private static final float DISSIPATE_PITCH = 1.2f;
    /**
     * Body-height fraction for particle spawn at entity midpoint.
     */
    private static final double ENTITY_MID_HEIGHT = 0.5;
    /**
     * Block update flags: notify neighbors + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    //
    // Ticks 0-3:  WINDUP  - blob contracts 30%, no spike visible
    // Ticks 4-5:  EMERGE  - blob returns to size, spike extends to full
    // Tick 6:     STRIKE  - full extension, damage dealt
    // Ticks 6-8:  HOLD    - spike stays rigid at captured position
    // Ticks 9-12: RETRACT - fast retract back into blob
    /**
     * Array offset for X target coordinate in anim data.
     */
    private static final int ANIM_OFFSET_TX = 2;
    /**
     * Array offset for Y target coordinate in anim data.
     */
    private static final int ANIM_OFFSET_TY = 3;
    /**
     * Array offset for Z target coordinate in anim data.
     */
    private static final int ANIM_OFFSET_TZ = 4;
    /**
     * First tick of the emerge phase (spike starts extending).
     */
    private static final int EMERGE_TICK = 4;
    /**
     * First tick of the retract phase.
     */
    private static final int RETRACT_TICK = 9;
    private static final String TAG_FACE = "MetalFace";
    private static final String TAG_SPIKE_ANIMS = "MetalSpikeAnims";
    private static final String DEFAULT_FACE = "up";
    /**
     * Stride for the flat spike anim array (entityId, tick, fx, fy, fz).
     */
    private static final int ANIM_STRIDE = 5;
    /**
     * Active per-entity spike animations, keyed by entity ID.
     */
    private final Map<Integer, SpikeAnim> spikeAnims = new HashMap<>();
    private Direction placedFace = Direction.UP;
    /**
     * Tracks the last-known stack count for isActive on the server.
     */
    private int lastKnownStacks;

    /**
     * Ticks remaining before a new spike can start.
     */
    private int spikeCooldown;

    /**
     * True once the dissipate smoke has been spawned.
     */
    private boolean dissipated;

    // --- WorldEffect (instant blob hit) ---

    /**
     * Spawns a poof of smoke when the trap runs out of charges.
     *
     * @param level the server level to spawn particles and sound in
     * @param pos   the block position where smoke appears
     */
    private static void spawnDissipateSmoke(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + BLOCK_CENTER;
        double cy = pos.getY() + BLOCK_CENTER;
        double cz = pos.getZ() + BLOCK_CENTER;
        level.sendParticles(ParticleTypes.POOF, cx, cy, cz,
                SMOKE_PARTICLE_COUNT, SMOKE_SPREAD, SMOKE_SPREAD, SMOKE_SPREAD,
                SMOKE_SPEED);
        level.playSound(null, cx, cy, cz,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, DISSIPATE_VOLUME, DISSIPATE_PITCH);
    }

    // --- ChainBehavior (fused chain marker detonation) ---

    /**
     * Computes the spike extension fraction for rendering.
     * <pre>
     * Ticks 0-3:  0 (windup, blob contracts, no spike)
     * Ticks 4-5:  0 -> 1.0 (emerge, spike extends full distance)
     * Ticks 6-8:  1.0 (hold at full extension, damage at tick 6)
     * Ticks 9-12: 1.0 -> 0 (fast retract)
     * </pre>
     *
     * @param animTick    the animation tick (0 to TOTAL_ANIM_TICKS-1)
     * @param partialTick the partial tick for smooth interpolation
     * @return extension fraction in [0, 1]
     */
    public static float extensionFraction(int animTick, float partialTick) {
        float t = animTick + partialTick;
        if (t < EMERGE_TICK) {
            return 0f;
        }
        if (t < STRIKE_TICK) {
            return (t - EMERGE_TICK) / (STRIKE_TICK - EMERGE_TICK);
        }
        if (t < RETRACT_TICK) {
            return 1f;
        }
        if (t < TOTAL_ANIM_TICKS) {
            float frac = (t - RETRACT_TICK) / (TOTAL_ANIM_TICKS - RETRACT_TICK);
            return 1f - frac;
        }
        return 0f;
    }

    /**
     * Computes the blob contraction scale during the windup phase.
     * Returns 1.0 normally, dips to (1 - WINDUP_CONTRACT) during
     * ticks 0-3, and returns to 1.0 during emerge ticks 4-5.
     *
     * @param animTick    the animation tick
     * @param partialTick the partial tick for smooth interpolation
     * @return scale multiplier for the orb [0.85, 1.0]
     */
    public static float blobContraction(int animTick, float partialTick) {
        float t = animTick + partialTick;
        if (t < 0) {
            return 1f;
        }
        if (t < EMERGE_TICK) {
            float frac = t / EMERGE_TICK;
            float contractCurve = (float) Math.sin(frac * Math.PI);
            return 1f - WINDUP_CONTRACT * contractCurve;
        }
        if (t < STRIKE_TICK) {
            float frac = (t - EMERGE_TICK) / (STRIKE_TICK - EMERGE_TICK);
            return 1f - WINDUP_CONTRACT * (1f - frac);
        }
        return 1f;
    }

    /**
     * Returns true if the entity is a valid impale target: living, not
     * an item, within radius, and not a sneaking player.
     *
     * @param entity the entity to test
     * @param center the spike trap center position
     * @return true if the entity can be impaled
     */
    private static boolean isValidTarget(Entity entity, Vec3 center) {
        if (entity instanceof ItemEntity) {
            return false;
        }
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        if (entity instanceof Player player && player.isShiftKeyDown()) {
            return false;
        }
        return entity.position().distanceTo(center) <= SPIKE_RADIUS;
    }

    /**
     * Triggers a block update to sync state to clients.
     *
     * @param be the owning block entity
     */
    private static void syncToClient(ChainMarkerBlockEntity be) {
        if (be.getLevel() != null && !be.getLevel().isClientSide()) {
            be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(),
                    be.getBlockState(), BLOCK_UPDATE_FLAGS);
        }
    }

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.metalSpikeTrap(level, pos, targetFace);
    }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.placedFace = be.getPlacedFace();
        this.lastKnownStacks = be.getStackCount();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        lastKnownStacks = be.getStackCount();
        if (spikeCooldown > 0) {
            spikeCooldown--;
        }
        advanceAndCleanAnims(level, pos);
        if (lastKnownStacks > 0) {
            scanForNewTargets(level, pos, be);
        }
        syncToClient(be);
    }

    /**
     * Advances all active spike animations by one tick. Deals damage
     * at STRIKE_TICK and removes completed animations.
     *
     * @param level the server level
     * @param pos   the marker block position
     */
    private void advanceAndCleanAnims(ServerLevel level, BlockPos pos) {
        Iterator<Map.Entry<Integer, SpikeAnim>> it = spikeAnims.entrySet().iterator();
        while (it.hasNext()) {
            SpikeAnim anim = it.next().getValue();
            anim.tick++;
            tickSingleAnim(level, it, anim);
        }
        tryDissipate(level, pos);
    }

    /**
     * Deals damage at strike tick and removes completed animations.
     *
     * @param level the server level
     * @param it    the iterator for safe removal
     * @param anim  the spike animation to advance
     */
    private void tickSingleAnim(ServerLevel level,
                                Iterator<Map.Entry<Integer, SpikeAnim>> it, SpikeAnim anim) {
        if (anim.tick == STRIKE_TICK && !anim.damageDealt) {
            dealDamage(level, anim);
        }
        if (anim.tick >= TOTAL_ANIM_TICKS) {
            it.remove();
        }
    }

    /**
     * Spawns dissipate smoke once all anims are done and no stacks remain.
     *
     * @param level the server level
     * @param pos   the marker block position
     */
    private void tryDissipate(ServerLevel level, BlockPos pos) {
        if (!dissipated && spikeAnims.isEmpty() && lastKnownStacks <= 0) {
            dissipated = true;
            spawnDissipateSmoke(level, pos);
        }
    }

    /**
     * Deals stalagmite damage to the target entity without knockback.
     *
     * @param level the server level
     * @param anim  the spike animation whose target to damage
     */
    private void dealDamage(ServerLevel level, SpikeAnim anim) {
        Entity entity = level.getEntity(anim.entityId);
        if (entity instanceof LivingEntity living) {
            living.hurtServer(level,
                    level.damageSources().source(DamageTypes.STALAGMITE),
                    STAB_DAMAGE);
            living.hurtMarked = false;
            level.sendParticles(ParticleTypes.CRIT,
                    living.getX(), living.getY(ENTITY_MID_HEIGHT), living.getZ(),
                    CRIT_PARTICLE_COUNT, CRIT_SPREAD, CRIT_SPREAD, CRIT_SPREAD,
                    CRIT_SPEED);
        }
        anim.damageDealt = true;
    }

    /**
     * Scans for entities in the spike radius and starts new spike
     * animations for entities not already animated or on cooldown.
     * Prunes cooldown entries for entities that left range.
     *
     * @param level the server level
     * @param pos   the marker block position
     * @param be    the owning block entity for charge management
     */
    private void scanForNewTargets(ServerLevel level, BlockPos pos,
                                   ChainMarkerBlockEntity be) {
        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(
                center.x - SPIKE_RADIUS, center.y - SPIKE_RADIUS, center.z - SPIKE_RADIUS,
                center.x + SPIKE_RADIUS, center.y + SPIKE_RADIUS, center.z + SPIKE_RADIUS);

        for (Entity entity : level.getEntities(null, area)) {
            if (!isValidTarget(entity, center)) {
                continue;
            }
            if (!tryStartSpike(entity, be)) {
                break;
            }
        }
    }

    /**
     * Starts a spike animation on the entity if not already animated and off cooldown.
     *
     * @param entity the target entity
     * @param be     the owning block entity for charge management
     * @return true if scanning should continue, false if charges exhausted
     */
    private boolean tryStartSpike(Entity entity, ChainMarkerBlockEntity be) {
        int id = entity.getId();
        if (spikeAnims.containsKey(id) || spikeCooldown > 0) {
            return true;
        }
        if (be.getStackCount() <= 0) {
            return false;
        }
        be.decrementStack();
        lastKnownStacks = be.getStackCount();
        Vec3 captured = entity.getBoundingBox().getCenter();
        spikeAnims.put(id, new SpikeAnim(id, captured.x, captured.y, captured.z));
        spikeCooldown = SPIKE_COOLDOWN;
        return true;
    }

    @Override
    public boolean isActive() {
        return lastKnownStacks > 0 || !spikeAnims.isEmpty();
    }

    @Override
    public boolean allowsTopOff() {
        return true;
    }

    /**
     * Returns the active spike animation snapshots for the BER.
     * Each entry is [entityId, animTick, floatBitsX, floatBitsY, floatBitsZ].
     *
     * @return list of snapshot arrays
     */
    public List<int[]> getSpikeSnapshots() {
        List<int[]> out = new ArrayList<>(spikeAnims.size());
        for (SpikeAnim anim : spikeAnims.values()) {
            out.add(new int[]{
                    anim.entityId, anim.tick,
                    Float.floatToRawIntBits((float) anim.targetX),
                    Float.floatToRawIntBits((float) anim.targetY),
                    Float.floatToRawIntBits((float) anim.targetZ)
            });
        }
        return out;
    }

    /**
     * Returns true if there are active spike animations.
     *
     * @return true when any spike is animating
     */
    public boolean hasActiveSpikes() {
        return !spikeAnims.isEmpty();
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putString(TAG_FACE, placedFace.getName());
        int[] animData = new int[spikeAnims.size() * ANIM_STRIDE];
        int idx = 0;
        for (SpikeAnim anim : spikeAnims.values()) {
            animData[idx] = anim.entityId;
            animData[idx + 1] = anim.tick;
            animData[idx + ANIM_OFFSET_TX] = Float.floatToRawIntBits((float) anim.targetX);
            animData[idx + ANIM_OFFSET_TY] = Float.floatToRawIntBits((float) anim.targetY);
            animData[idx + ANIM_OFFSET_TZ] = Float.floatToRawIntBits((float) anim.targetZ);
            idx += ANIM_STRIDE;
        }
        output.putIntArray(TAG_SPIKE_ANIMS, animData);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        String faceName = input.getStringOr(TAG_FACE, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
        spikeAnims.clear();
        int[] animData = input.getIntArray(TAG_SPIKE_ANIMS).orElse(new int[0]);
        for (int i = 0; i + ANIM_STRIDE - 1 < animData.length; i += ANIM_STRIDE) {
            SpikeAnim anim = new SpikeAnim(animData[i],
                    Float.intBitsToFloat(animData[i + ANIM_OFFSET_TX]),
                    Float.intBitsToFloat(animData[i + ANIM_OFFSET_TY]),
                    Float.intBitsToFloat(animData[i + ANIM_OFFSET_TZ]));
            anim.tick = animData[i + 1];
            anim.damageDealt = anim.tick >= STRIKE_TICK;
            spikeAnims.put(anim.entityId, anim);
        }
    }

    /**
     * Mutable animation state for a single spike targeting one entity.
     * The target position is captured at creation and stays rigid.
     */
    static final class SpikeAnim {
        final int entityId;
        final double targetX;
        final double targetY;
        final double targetZ;
        int tick;
        boolean damageDealt;

        SpikeAnim(int entityId, double targetX, double targetY, double targetZ) {
            this.entityId = entityId;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }
    }
}
