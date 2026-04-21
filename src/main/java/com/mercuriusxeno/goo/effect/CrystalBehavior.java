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

/**
 * Crystal shard cloud behavior. After fuse expiry, creates a zone of
 * suspended crystal slivers that damage anything moving through it.
 * Sneaking negates damage; sprinting doubles hit frequency. Each hit
 * consumes one charge; the cloud dissipates when charges run out.
 */
public final class CrystalBehavior implements ChainBehavior {

    /** Cloud effect radius in blocks. */
    public static final double CLOUD_RADIUS = 4.5;
    /** Charges granted per stacked blob. */
    public static final int CHARGES_PER_BLOB = 8;
    /** Damage per shard hit. */
    private static final float DAMAGE_PER_HIT = 1f;
    /** Ticks between hits at normal walking speed. */
    private static final int NORMAL_HIT_INTERVAL = 2;
    /** Ticks between hits when sprinting. */
    private static final int SPRINT_HIT_INTERVAL = 1;
    /** Invulnerability ticks set after each hit (low immunity frames). */
    private static final int HURT_COOLDOWN = 1;
    /** Minimum squared velocity to count as moving. */
    private static final double MOVE_THRESHOLD_SQ = 1e-4;

    private static final String TAG_CHARGES = "CrystalCharges";
    private static final String TAG_MAX_CHARGES = "CrystalMaxCharges";
    private static final String TAG_FACE = "CrystalFace";
    private static final String TAG_TICK = "CrystalTick";
    private static final String TAG_DISSOLVE = "CrystalDissolve";
    private static final String TAG_EXPAND = "CrystalExpand";
    private static final String DEFAULT_FACE = "up";
    /** Ticks for the cloud to fully expand or contract (~0.5 seconds). */
    private static final int ANIM_DURATION = 10;

    private int chargesRemaining;
    private int maxCharges;
    private Direction placedFace = Direction.UP;
    /** Internal tick counter for hit interval timing. */
    private int tickCounter;
    /** Remaining contract ticks when flattening, 0 when not contracting. */
    private int contractTicks;
    /** Remaining expand ticks when spawning/unflattening, 0 when fully expanded. */
    private int expandTicks;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.chargesRemaining = be.getStackCount() * CHARGES_PER_BLOB;
        this.maxCharges = chargesRemaining;
        this.placedFace = be.getPlacedFace();
        this.tickCounter = 0;
        this.expandTicks = ANIM_DURATION;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (expandTicks > 0) { expandTicks--; }
        if (contractTicks > 0) {
            contractTicks--;
            return;
        }
        if (chargesRemaining <= 0) { return; }
        tickCounter++;
        shredEntities(level, pos, be);
    }

    /** Returns the cloud radius as a fraction [0-1] for the expand/contract animation.
     *
     * @return 0 when fully contracted, 1 when fully expanded
     */
    public float getRadiusFraction() {
        if (expandTicks > 0) {
            return 1f - (float) expandTicks / ANIM_DURATION;
        }
        if (contractTicks > 0) {
            return (float) contractTicks / ANIM_DURATION;
        }
        return 1f;
    }

    /** Returns true if the cloud is animating (expanding or contracting).
     *
     * @return true during either animation
     */
    public boolean isAnimating() {
        return expandTicks > 0 || contractTicks > 0;
    }

    @Override
    public boolean isActive() {
        return chargesRemaining > 0;
    }

    @Override
    public boolean allowsTopOff() {
        return true;
    }

    @Override
    public void onTopOff(ChainMarkerBlockEntity be) {
        chargesRemaining = be.getStackCount() * CHARGES_PER_BLOB;
        maxCharges = Math.max(maxCharges, chargesRemaining);
    }

    /**
     * Returns the charge density as a 0-1 ratio for visual scaling.
     *
     * @return charges remaining / max charges
     */
    public float getDensity() {
        return maxCharges > 0 ? (float) chargesRemaining / maxCharges : 0f;
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
     * Scans for moving entities and damages them at the appropriate interval.
     *
     * @param level the server level
     * @param pos   the marker block position
     * @param be    the owning block entity
     */
    private void shredEntities(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(
                center.x - CLOUD_RADIUS, center.y - CLOUD_RADIUS, center.z - CLOUD_RADIUS,
                center.x + CLOUD_RADIUS, center.y + CLOUD_RADIUS, center.z + CLOUD_RADIUS);

        for (Entity entity : level.getEntities(null, area)) {
            if (chargesRemaining <= 0) { break; }
            if (!isShredTarget(entity, center)) { continue; }
            int interval = getHitInterval(entity);
            if (tickCounter % interval != 0) { continue; }
            shredEntity(level, entity, be);
        }
    }

    /**
     * Returns true if the entity is a valid shred target.
     *
     * @param entity the entity to test
     * @param center the cloud center position
     * @return true if the entity should be damaged
     */
    private static boolean isShredTarget(Entity entity, Vec3 center) {
        if (!isShredEligible(entity)) { return false; }
        return entity.position().distanceTo(center) <= CLOUD_RADIUS
                && isMovingHorizontally(entity);
    }

    /** Checks horizontal movement only - gravity gives standing entities a vertical delta.
     *
     * @param entity the entity to check
     * @return true if the entity has significant horizontal velocity
     */
    private static boolean isMovingHorizontally(Entity entity) {
        Vec3 delta = entity.getDeltaMovement();
        return delta.x * delta.x + delta.z * delta.z > MOVE_THRESHOLD_SQ;
    }

    /**
     * Rejects items, non-living entities, and sneaking players.
     *
     * @param entity the entity to test
     * @return true if the entity can be damaged by the cloud
     */
    private static boolean isShredEligible(Entity entity) {
        return !(entity instanceof ItemEntity) && entity instanceof LivingEntity
                && !(entity instanceof Player player && player.isShiftKeyDown());
    }

    /**
     * Returns the hit interval for the entity (faster if sprinting).
     *
     * @param entity the entity to check
     * @return tick interval between hits
     */
    private static int getHitInterval(Entity entity) {
        if (entity instanceof Player player && player.isSprinting()) {
            return SPRINT_HIT_INTERVAL;
        }
        return NORMAL_HIT_INTERVAL;
    }

    /**
     * Damages a single entity and decrements charges.
     *
     * @param level  the server level
     * @param entity the entity to damage
     * @param be     the owning block entity for stack management
     */
    private void shredEntity(ServerLevel level, Entity entity, ChainMarkerBlockEntity be) {
        entity.hurtServer(level,
                level.damageSources().source(DamageTypes.CACTUS),
                DAMAGE_PER_HIT);
        entity.invulnerableTime = HURT_COOLDOWN;
        int prevStack = chargesRemaining / CHARGES_PER_BLOB;
        chargesRemaining--;
        int newStack = chargesRemaining / CHARGES_PER_BLOB;
        if (newStack < prevStack) {
            be.decrementStack();
        }
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_CHARGES, chargesRemaining);
        output.putInt(TAG_MAX_CHARGES, maxCharges);
        output.putString(TAG_FACE, placedFace.getName());
        output.putInt(TAG_TICK, tickCounter);
        output.putInt(TAG_DISSOLVE, contractTicks);
        output.putInt(TAG_EXPAND, expandTicks);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        chargesRemaining = input.getIntOr(TAG_CHARGES, 0);
        maxCharges = input.getIntOr(TAG_MAX_CHARGES, 0);
        tickCounter = input.getIntOr(TAG_TICK, 0);
        contractTicks = input.getIntOr(TAG_DISSOLVE, 0);
        expandTicks = input.getIntOr(TAG_EXPAND, 0);
        String faceName = input.getStringOr(TAG_FACE, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
