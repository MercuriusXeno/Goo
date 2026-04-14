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
    private static final String TAG_FLAT_MODE = "CrystalFlatMode";
    private static final String TAG_TICK = "CrystalTick";
    private static final String DEFAULT_FACE = "up";

    private int chargesRemaining;
    private int maxCharges;
    private Direction placedFace = Direction.UP;
    private boolean flatMode;
    /** Internal tick counter for hit interval timing. */
    private int tickCounter;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.chargesRemaining = be.getStackCount() * CHARGES_PER_BLOB;
        this.maxCharges = chargesRemaining;
        this.placedFace = be.getPlacedFace();
        this.flatMode = be.isFlatMode();
        this.tickCounter = 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (flatMode || chargesRemaining <= 0) { return; }
        tickCounter++;
        shredEntities(level, pos, be);
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
        if (entity instanceof ItemEntity) { return false; }
        if (!(entity instanceof LivingEntity)) { return false; }
        if (entity instanceof Player player && player.isShiftKeyDown()) { return false; }
        if (entity.position().distanceTo(center) > CLOUD_RADIUS) { return false; }
        return entity.getDeltaMovement().lengthSqr() > MOVE_THRESHOLD_SQ;
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
        output.putBoolean(TAG_FLAT_MODE, flatMode);
        output.putInt(TAG_TICK, tickCounter);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        chargesRemaining = input.getIntOr(TAG_CHARGES, 0);
        maxCharges = input.getIntOr(TAG_MAX_CHARGES, 0);
        flatMode = input.getBooleanOr(TAG_FLAT_MODE, false);
        tickCounter = input.getIntOr(TAG_TICK, 0);
        String faceName = input.getStringOr(TAG_FACE, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
