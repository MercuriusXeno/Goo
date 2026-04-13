package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Metal spike trap behavior. After fuse expiry, detects mobs entering
 * a 1.5-block radius and stabs them with a spike. Each stab consumes
 * one charge (equal to stack count at detonation). Players are stabbed
 * unless sneaking. Flat mode makes the trap inert.
 */
public final class MetalBehavior implements ChainBehavior {

    /** Detection and spike reach radius in blocks. */
    public static final double SPIKE_RADIUS = 1.5;
    /** Damage per stab. */
    private static final float STAB_DAMAGE = 6f;
    /** Knockback strength on stab. */
    private static final double KNOCKBACK_STRENGTH = 0.6;
    /** Ticks a spike animation lasts (extend + retract). */
    public static final int SPIKE_ANIM_TICKS = 6;

    private static final String TAG_CHARGES = "MetalCharges";
    private static final String TAG_FACE = "MetalFace";
    private static final String TAG_FLAT_MODE = "MetalFlatMode";
    private static final String TAG_TRACKED = "MetalTracked";
    private static final String DEFAULT_FACE = "up";

    private int chargesRemaining;
    private Direction placedFace = Direction.UP;
    private boolean flatMode;
    /** Entity IDs currently inside the detection radius. */
    private final Set<Integer> trackedEntities = new HashSet<>();
    /** Entity positions stabbed this tick, for BER spike rendering. */
    private final List<Vec3> activeSpikes = new ArrayList<>();
    /** Tick counter for spike animation timing. */
    private int spikeAnimTick;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.chargesRemaining = be.getStackCount();
        this.placedFace = be.getPlacedFace();
        this.flatMode = be.isFlatMode();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        activeSpikes.clear();
        if (spikeAnimTick > 0) { spikeAnimTick--; }
        if (flatMode || chargesRemaining <= 0) { return; }

        boolean stabbed = scanAndStab(level, pos);
        if (stabbed) {
            spikeAnimTick = SPIKE_ANIM_TICKS;
            be.setChanged();
        }
    }

    /** Scans for entities in range and stabs newly-entered ones.
     *
     * @param level the server level
     * @param pos   the marker block position
     * @return true if any entity was stabbed this tick
     */
    private boolean scanAndStab(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(
                center.x - SPIKE_RADIUS, center.y - SPIKE_RADIUS, center.z - SPIKE_RADIUS,
                center.x + SPIKE_RADIUS, center.y + SPIKE_RADIUS, center.z + SPIKE_RADIUS);

        Set<Integer> currentInRange = new HashSet<>();
        boolean stabbed = false;
        for (Entity entity : level.getEntities(null, area)) {
            if (!isValidTarget(entity, center)) { continue; }
            currentInRange.add(entity.getId());
            if (!trackedEntities.contains(entity.getId())) {
                stabEntity(level, entity, center);
                activeSpikes.add(entity.position());
                stabbed = true;
                if (chargesRemaining <= 0) { break; }
            }
        }
        pruneTracked(currentInRange);
        trackedEntities.addAll(currentInRange);
        return stabbed;
    }

    @Override
    public boolean isActive() {
        return chargesRemaining > 0;
    }

    /**
     * Returns the entity positions stabbed this tick for BER rendering.
     *
     * @return list of world-space stab target positions
     */
    public List<Vec3> getActiveSpikes() {
        return activeSpikes;
    }

    /**
     * Returns the remaining spike animation ticks.
     *
     * @return ticks remaining in the current spike animation
     */
    public int getSpikeAnimTick() {
        return spikeAnimTick;
    }

    /**
     * Returns the number of charges remaining.
     *
     * @return charges left
     */
    public int getChargesRemaining() {
        return chargesRemaining;
    }

    /**
     * Returns true if the entity is a valid stab target: living, not
     * an item, within radius, and not a sneaking player.
     *
     * @param entity the entity to test
     * @param center the spike trap center position
     * @return true if the entity can be stabbed
     */
    private static boolean isValidTarget(Entity entity, Vec3 center) {
        if (entity instanceof ItemEntity) { return false; }
        if (!(entity instanceof LivingEntity)) { return false; }
        if (entity instanceof Player player && player.isShiftKeyDown()) { return false; }
        return entity.position().distanceTo(center) <= SPIKE_RADIUS;
    }

    /**
     * Damages and knocks back a single entity.
     *
     * @param level  the server level for damage source lookup
     * @param entity the entity to stab
     * @param center the spike trap center for knockback direction
     */
    private void stabEntity(ServerLevel level, Entity entity, Vec3 center) {
        entity.hurtServer(level,
                level.damageSources().source(DamageTypes.STALAGMITE),
                STAB_DAMAGE);
        Vec3 knockback = entity.position().subtract(center)
                .normalize().scale(KNOCKBACK_STRENGTH);
        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
        entity.hurtMarked = true;
        chargesRemaining--;
    }

    /**
     * Removes entity IDs that are no longer in detection range.
     *
     * @param currentInRange the set of entity IDs currently in range
     */
    private void pruneTracked(Set<Integer> currentInRange) {
        trackedEntities.retainAll(currentInRange);
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_CHARGES, chargesRemaining);
        output.putString(TAG_FACE, placedFace.getName());
        output.putBoolean(TAG_FLAT_MODE, flatMode);
        output.putIntArray(TAG_TRACKED, trackedEntities.stream()
                .mapToInt(Integer::intValue).toArray());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        chargesRemaining = input.getIntOr(TAG_CHARGES, 0);
        flatMode = input.getBooleanOr(TAG_FLAT_MODE, false);
        String faceName = input.getStringOr(TAG_FACE, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
